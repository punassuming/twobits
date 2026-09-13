package dev.scrybe.core.localai

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

internal class LocalTranscriptionTimeoutException(
    timeoutMs: Long,
) : RuntimeException(
        "Local Whisper decode timed out after ${timeoutMs / 1_000}s and was abandoned " +
            "(sherpa-onnx has no cancel/interrupt API for a blocking decode() call)",
    )

internal class WhisperEngine(
    modelDir: File,
    filePrefix: String = "tiny",
) : Closeable {
    private val recognizer: OfflineRecognizer

    // Deliberately NOT a child of any caller's coroutine — sherpa-onnx's OfflineRecognizer.decode()
    // is a synchronous JNI call with no cancel/interrupt/timeout API of its own (confirmed against
    // its actual Kotlin API surface: createStream/getResult/decode/setConfig/release only), so
    // once it's running, nothing can stop it. Running it here instead lets decodeChunk() stop
    // *awaiting* a hung call (via withTimeoutOrNull below) without needing to — impossibly —
    // interrupt it; the abandoned call simply keeps running on this scope until it returns on its
    // own or the process dies.
    private val nativeCallScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outstandingNativeCalls = AtomicInteger(0)

    @Volatile
    private var releasePending = false

    init {
        // sherpa-onnx's asr-models release only ships int8-quantized encoder/decoder pairs for
        // tiny/base/small — medium (and any future larger tier) has only the fp32 files. Handing
        // OfflineRecognizer's native constructor a path that doesn't exist aborts the whole
        // process (no JNI exception to catch, so runCatching upstream never sees it) — every
        // inference on that tier crashed the app outright. Falling back to fp32 here keeps
        // construction on the Kotlin side, where a missing model can fail as a normal exception.
        val encoderPath = resolveModelFile(modelDir, "$filePrefix-encoder").absolutePath
        val decoderPath = resolveModelFile(modelDir, "$filePrefix-decoder").absolutePath
        val tokensPath = File(modelDir, "$filePrefix-tokens.txt").absolutePath

        val whisperConfig =
            OfflineWhisperModelConfig(
                encoder = encoderPath,
                decoder = decoderPath,
                language = "en",
                task = "transcribe",
            )
        val modelConfig = OfflineModelConfig(whisper = whisperConfig, tokens = tokensPath)
        val config = OfflineRecognizerConfig(modelConfig = modelConfig)
        recognizer = OfflineRecognizer(config = config)
    }

    /**
     * sherpa-onnx's offline Whisper decode silently discards anything past the first ~29.5s of a
     * single acceptWaveform() call — Whisper's encoder has a fixed 30s context window, and
     * sherpa-onnx trims to it (logging a warning no caller here ever sees) rather than chunking
     * on its own. Left alone, a multi-minute recording only ever has its opening ~30 seconds
     * decoded, and if that opening is silence, ringing, or hold music, Whisper hallucinates a
     * short non-speech token ("(mumbling)", "[Music]") for the whole file — indistinguishable
     * from transcription being totally broken. Real recordings routinely run minutes long, so
     * this splits into sub-30s windows and decodes each one on the same recognizer/model
     * instance, concatenating the results.
     */
    suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int = 16000,
        onChunkDecoded: ((index: Int, total: Int, elapsedMs: Long) -> Unit)? = null,
    ): String {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        val chunkSize = CHUNK_SECONDS * sampleRate
        if (samples.size <= chunkSize) {
            // Mirrors the between-chunk checkpoint below — a short recording is otherwise the
            // one case with zero cancellation checkpoints at all, since it never enters the loop.
            // This only catches a cancel requested before decode starts (e.g. queued behind other
            // batch items); once decodeChunk's native call is running, nothing can interrupt it.
            currentCoroutineContext().ensureActive()
            val startedAtMs = System.currentTimeMillis()
            val text = decodeChunk(samples, sampleRate)
            onChunkDecoded?.invoke(0, 1, System.currentTimeMillis() - startedAtMs)
            return text
        }
        val totalChunks = (samples.size + chunkSize - 1) / chunkSize
        val parts = mutableListOf<String>()
        var offset = 0
        var index = 0
        while (offset < samples.size) {
            // decodeChunk() awaits the native call on a detached scope, so a Cancel action
            // (TranscriptionCancellationController) already resolves mid-chunk; this checkpoint
            // just avoids starting the next chunk's native work after a cancel that landed
            // between two chunks.
            currentCoroutineContext().ensureActive()
            val end = (offset + chunkSize).coerceAtMost(samples.size)
            val startedAtMs = System.currentTimeMillis()
            val chunkText = decodeChunk(samples.copyOfRange(offset, end), sampleRate)
            onChunkDecoded?.invoke(index, totalChunks, System.currentTimeMillis() - startedAtMs)
            if (chunkText.isNotBlank()) parts += chunkText
            offset = end
            index++
        }
        return parts.joinToString(" ")
    }

    private suspend fun decodeChunk(
        samples: FloatArray,
        sampleRate: Int,
    ): String {
        outstandingNativeCalls.incrementAndGet()
        val deferred =
            nativeCallScope.async {
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, sampleRate)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }
        deferred.invokeOnCompletion {
            if (outstandingNativeCalls.decrementAndGet() == 0 && releasePending) {
                recognizer.release()
            }
        }
        // await() is a real suspension point, unlike the native decode() call itself — it responds
        // immediately to both this timeout and the caller's own Job being cancelled, regardless of
        // whether the abandoned native call ever returns.
        return withTimeoutOrNull(CHUNK_DECODE_TIMEOUT_MS) { deferred.await() }
            ?: throw LocalTranscriptionTimeoutException(CHUNK_DECODE_TIMEOUT_MS)
    }

    override fun close() {
        // If an abandoned decode (above) is still running when close() is called (e.g. this
        // engine's `.use { }` block exiting via the timeout exception), releasing the recognizer
        // now would free native memory that call is still touching inside JNI — a use-after-free
        // that crashes the whole process, not a catchable Kotlin exception. Defer release() to
        // that call's own eventual completion instead: a bounded memory leak only for as long as
        // the abandoned call keeps running, never blocking a later transcription (a fresh
        // WhisperEngine is constructed per attempt regardless).
        if (outstandingNativeCalls.get() == 0) {
            recognizer.release()
        } else {
            releasePending = true
        }
    }

    private companion object {
        // Whisper's encoder always sees a fixed 30-second window: sherpa-onnx zero-pads every
        // acceptWaveform() call up to that length before running it, so a decode call costs the
        // same whether it holds 10s or 28s of audio. That makes the only sensible target
        // "as much real audio per window as fits" — comfortably under sherpa-onnx's ~29.5s
        // (max_num_frames - 50 at 10ms/frame) hard cutoff, past which samples are silently
        // dropped. This value was once lowered to 10 to shorten the wait before a Cancel could
        // take effect at the between-chunk checkpoint; that tripled the encoder work per
        // recording and fed the model windows that were two-thirds silence padding — a known
        // trigger for Whisper's decoder looping on repeated/hallucinated tokens until it hits
        // its token cap — which is what made local transcription crawl or never finish. Cancel
        // no longer depends on chunk length (decodeChunk() awaits the native call on a detached
        // scope and returns the moment the caller is cancelled), so do NOT lower this again
        // for cancel latency.
        const val CHUNK_SECONDS = 28

        // A hang detector, not a throughput limit: the unit of native work is always one full
        // 30s window (see CHUNK_SECONDS). Tiny/Base/Small finish one in a few seconds; Medium
        // (fp32 only — no int8 build ships for it) on a throttled mid-range phone can
        // legitimately take longer than the previous 45s budget, which would have failed those
        // runs instead of the hung ones it was meant to catch. This leaves room for the slowest
        // real tier while still surfacing a truly stuck decode within a few minutes.
        // transcribe()'s loop doesn't catch/retry, so at most one timeout is ever paid per attempt.
        const val CHUNK_DECODE_TIMEOUT_MS = 180_000L

        fun resolveModelFile(
            modelDir: File,
            baseName: String,
        ): File {
            val int8 = File(modelDir, "$baseName.int8.onnx")
            if (int8.exists()) return int8
            val fp32 = File(modelDir, "$baseName.onnx")
            if (fp32.exists()) return fp32
            error("No $baseName model file found in ${modelDir.absolutePath} (checked .int8.onnx and .onnx)")
        }
    }
}
