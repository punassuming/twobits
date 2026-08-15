package dev.scrybe.core.localai

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

internal class WhisperEngine(
    modelDir: File,
    filePrefix: String = "tiny",
) : Closeable {
    private val recognizer: OfflineRecognizer

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
    ): String {
        val chunkSize = CHUNK_SECONDS * sampleRate
        if (samples.size <= chunkSize) {
            return decodeChunk(samples, sampleRate)
        }
        val parts = mutableListOf<String>()
        var offset = 0
        while (offset < samples.size) {
            // A chunk's native decode call has no suspension points of its own to notice
            // cancellation at, so this is the only checkpoint between chunks a Cancel action
            // (TranscriptionCancellationController) has to actually stop a long recording instead
            // of running every remaining chunk to completion first.
            currentCoroutineContext().ensureActive()
            val end = (offset + chunkSize).coerceAtMost(samples.size)
            val chunkText = decodeChunk(samples.copyOfRange(offset, end), sampleRate)
            if (chunkText.isNotBlank()) parts += chunkText
            offset = end
        }
        return parts.joinToString(" ")
    }

    private suspend fun decodeChunk(
        samples: FloatArray,
        sampleRate: Int,
    ): String =
        withContext(Dispatchers.Default) {
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            stream.release()
            result.text.trim()
        }

    override fun close() {
        recognizer.release()
    }

    private companion object {
        // Comfortably under sherpa-onnx's ~29.5s (max_num_frames - 50 at 10ms/frame) hard cutoff.
        const val CHUNK_SECONDS = 28

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
