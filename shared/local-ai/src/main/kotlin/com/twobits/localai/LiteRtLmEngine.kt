package com.twobits.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Public stand-in for `com.google.ai.edge.litertlm.Backend`, which callers outside this module
 * can't reference directly — `shared/local-ai` depends on `litertlm-android` via
 * `implementation`, not `api`, deliberately keeping the third-party library an internal
 * implementation detail (no app module lists it as a direct dependency). [LiteRtLmEngine]
 * translates this to the real `Backend` internally.
 */
enum class LiteRtBackend {
    CPU,
}

/** A non-content heartbeat emitted while a native LiteRT-LM request is still running. */
data class LiteRtGenerationProgress(
    val elapsedMs: Long,
    val receivedMessageCount: Int,
)

private fun LiteRtBackend.toEngineBackend(): Backend =
    when (this) {
        LiteRtBackend.CPU -> Backend.CPU()
    }

/**
 * Shared LiteRT-LM wrapper for Scrybe, Shelf Snap, and PriceDrop's on-device text (and,
 * experimentally, vision) inference — one `Conversation` per instance, used for exactly one
 * logical exchange (a single [generate]/[generateWithImage] call for one-shot callers, or
 * repeated calls on the same instance for a caller that wants multi-turn history — the
 * underlying `Conversation` accumulates turns across calls on the same instance by itself).
 *
 * [generateWithImage] is EXPERIMENTAL: `litert-community`'s Gemma 4 E2B/E4B repos are listed
 * under its "Multi-Modality Models" collection, and LiteRT-LM's own Kotlin API documents
 * `Content.ImageFile`/`Content.Text` for "models with multi-modality support" — but the only
 * worked example in that documentation is Gemma3n, not Gemma 4 specifically, and nobody has
 * independently verified end-to-end that a Gemma 4 E2B/E4B `.litertlm` bundle actually answers
 * an image-plus-text prompt correctly. Verify on a real device before removing this caveat.
 *
 * Instances are only obtainable through [acquire] (or the [withLocalLlmEngine] convenience for
 * the common load-use-close shape), which is where two process-wide protections live:
 * a free-memory check before any native allocation ([LocalInferenceMemoryGuard]) and a gate that
 * keeps at most one engine resident per process. Both exist because the failure mode they
 * prevent — a multi-GB model load pushing the process past the low-memory killer — leaves no
 * exception and no stack trace, only a dead app.
 */
class LiteRtLmEngine internal constructor(
    context: Context,
    modelFile: File,
    systemInstruction: String?,
    visionBackend: LiteRtBackend?,
    maxNumTokens: Int,
    private val onClosed: () -> Unit,
) : Closeable {
    private val engine: Engine
    private val conversation: Conversation
    private val closed = AtomicBoolean(false)

    init {
        val engineConfig =
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = visionBackend?.toEngineBackend(),
                maxNumTokens = maxNumTokens,
                // A dedicated subdirectory, not the app's whole cache dir: app code writes and
                // deletes its own temp files there (downscaled vision JPEGs, for one), and
                // LiteRT-LM treats this directory as its own to cache compiled artifacts in.
                cacheDir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }.path,
            )
        engine = Engine(engineConfig)
        engine.initialize()
        conversation =
            runCatching {
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = systemInstruction?.let { Contents.of(it) },
                        samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
                    ),
                )
            }.onFailure {
                // The engine's native memory would otherwise stay allocated with no owner to
                // ever release it — a construction failure must not leak a loaded model.
                engine.close()
            }.getOrThrow()
    }

    /**
     * Generates one response with a hard deadline. LiteRT-LM's synchronous API blocks inside
     * JNI and ignores coroutine cancellation; its callback API plus [Conversation.cancelProcess]
     * is required to actually stop native generation when the deadline expires.
     */
    suspend fun generate(
        prompt: String,
        timeoutMs: Long = DEFAULT_GENERATION_TIMEOUT_MS,
        onProgress: (LiteRtGenerationProgress) -> Unit = {},
    ): String = generateAsync(timeoutMs, onProgress) { callback -> conversation.sendMessageAsync(prompt, callback) }

    /** EXPERIMENTAL — see class doc. [imageFile] is sent as a single image alongside [prompt]. */
    suspend fun generateWithImage(
        imageFile: File,
        prompt: String,
        timeoutMs: Long = DEFAULT_GENERATION_TIMEOUT_MS,
        onProgress: (LiteRtGenerationProgress) -> Unit = {},
    ): String =
        generateAsync(timeoutMs, onProgress) { callback ->
            conversation.sendMessageAsync(
                Contents.of(
                    Content.ImageFile(imageFile.absolutePath),
                    Content.Text(prompt),
                ),
                callback,
            )
        }

    private suspend fun generateAsync(
        timeoutMs: Long,
        onProgress: (LiteRtGenerationProgress) -> Unit,
        start: (MessageCallback) -> Unit,
    ): String {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        val startedAtMs = System.currentTimeMillis()
        val result = CompletableDeferred<String>()
        val accumulatedResponse = StringBuilder()
        val messageCount = AtomicInteger(0)
        val callback =
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    synchronized(accumulatedResponse) { accumulatedResponse.append(message.toString()) }
                    onProgress(
                        LiteRtGenerationProgress(
                            System.currentTimeMillis() - startedAtMs,
                            messageCount.incrementAndGet(),
                        ),
                    )
                }

                override fun onDone() {
                    val response = synchronized(accumulatedResponse) { accumulatedResponse.toString() }
                    if (response.isEmpty()) {
                        result.completeExceptionally(IllegalStateException("LiteRT-LM completed without a response"))
                    } else {
                        result.complete(response)
                    }
                }

                override fun onError(throwable: Throwable) {
                    result.completeExceptionally(throwable)
                }
            }
        return coroutineScope {
            val heartbeat =
                launch {
                    while (isActive) {
                        delay(PROGRESS_HEARTBEAT_MS)
                        onProgress(
                            LiteRtGenerationProgress(
                                System.currentTimeMillis() - startedAtMs,
                                messageCount.get(),
                            ),
                        )
                    }
                }
            try {
                onProgress(LiteRtGenerationProgress(elapsedMs = 0, receivedMessageCount = 0))
                start(callback)
                withTimeout(timeoutMs) { result.await() }
            } catch (timeout: TimeoutCancellationException) {
                throw LocalGenerationTimeoutException(timeoutMs, timeout)
            } finally {
                // Covers both the timeout above and the caller's own coroutine being cancelled
                // (e.g. a ViewModel cleared mid-generation): either way, if the deferred never
                // completed, native generation is still running and must be told to stop.
                if (!result.isCompleted) conversation.cancelProcess()
                heartbeat.cancel()
            }
        }
    }

    /** Idempotent. Releases the native engine and then the process-wide gate held since [acquire]. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            conversation.close()
            engine.close()
        } finally {
            onClosed()
        }
    }

    companion object {
        const val DEFAULT_GENERATION_TIMEOUT_MS = 90_000L

        /**
         * Context window (prompt + image soft tokens + response). Never set before, which left
         * the library's own default in charge — and a vision call's image tokens plus the full
         * JSON-schema system prompt reused from the cloud path can outrun a small default, which
         * a native runtime answers with an abort rather than an exception.
         */
        const val DEFAULT_MAX_NUM_TOKENS = 4096
        private const val PROGRESS_HEARTBEAT_MS = 5_000L
        private const val CACHE_SUBDIR = "litertlm"

        /**
         * At most one engine resident per process. Overlapping local calls are a documented,
         * intended pattern for the apps (Shelf Snap fans out listing refinement per platform
         * while a vision analysis can still be running), and each of them would otherwise load
         * its own multi-GB copy of the model at the same time. The second caller now simply
         * waits for the first engine to close.
         */
        private val residentEngineGate = Mutex()

        /**
         * The only way to obtain an engine. Takes the process-wide gate, checks free memory
         * against the model on disk, then performs the blocking native load — the caller is
         * responsible for being off the main thread, exactly as with the old constructor. The
         * gate is released by [close], or immediately if anything here throws (including the
         * caller being cancelled while waiting for it).
         */
        suspend fun acquire(
            context: Context,
            modelFile: File,
            systemInstruction: String? = null,
            visionBackend: LiteRtBackend? = null,
            maxNumTokens: Int = DEFAULT_MAX_NUM_TOKENS,
        ): LiteRtLmEngine {
            require(maxNumTokens > 0) { "maxNumTokens must be positive" }
            residentEngineGate.lock()
            return runCatching {
                // Checked after taking the gate, not before: the previous engine releasing its
                // memory is exactly the event that can turn a refusal into a pass.
                LocalInferenceMemoryGuard.requireHeadroom(context, modelFile, vision = visionBackend != null)
                LiteRtLmEngine(context, modelFile, systemInstruction, visionBackend, maxNumTokens) {
                    residentEngineGate.unlock()
                }
            }.onFailure { residentEngineGate.unlock() }.getOrThrow()
        }
    }
}

/**
 * Load-use-close in one call — the shape every one-shot caller wants. See [LiteRtLmEngine.acquire]
 * for the memory check and one-engine-at-a-time gate this goes through.
 */
suspend fun <T> withLocalLlmEngine(
    context: Context,
    modelFile: File,
    systemInstruction: String? = null,
    visionBackend: LiteRtBackend? = null,
    maxNumTokens: Int = LiteRtLmEngine.DEFAULT_MAX_NUM_TOKENS,
    block: suspend (LiteRtLmEngine) -> T,
): T = LiteRtLmEngine.acquire(context, modelFile, systemInstruction, visionBackend, maxNumTokens).use { block(it) }

class LocalGenerationTimeoutException(
    timeoutMs: Long,
    cause: Throwable,
) : RuntimeException("Local generation timed out after ${timeoutMs / 1_000}s and was cancelled", cause)
