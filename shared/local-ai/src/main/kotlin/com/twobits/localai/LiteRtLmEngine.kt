package com.twobits.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
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
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
 */
class LiteRtLmEngine(
    context: Context,
    modelFile: File,
    systemInstruction: String? = null,
    visionBackend: LiteRtBackend? = null,
) : Closeable {
    private val engine: Engine

    init {
        val engineConfig =
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = visionBackend?.toEngineBackend(),
                cacheDir = context.cacheDir.path,
            )
        engine = Engine(engineConfig)
        engine.initialize()
    }

    private val conversation =
        engine.createConversation(
            ConversationConfig(
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
            ),
        )

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
        val latestMessage = AtomicReference<Message?>(null)
        val messageCount = AtomicInteger(0)
        val callback =
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    latestMessage.set(message)
                    onProgress(
                        LiteRtGenerationProgress(
                            System.currentTimeMillis() - startedAtMs,
                            messageCount.incrementAndGet(),
                        ),
                    )
                }

                override fun onDone() {
                    val response = latestMessage.get()?.toString()
                    if (response.isNullOrEmpty()) {
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
                conversation.cancelProcess()
                throw LocalGenerationTimeoutException(timeoutMs, timeout)
            } finally {
                heartbeat.cancel()
            }
        }
    }

    override fun close() {
        conversation.close()
        engine.close()
    }

    companion object {
        const val DEFAULT_GENERATION_TIMEOUT_MS = 90_000L
        private const val PROGRESS_HEARTBEAT_MS = 5_000L
    }
}

class LocalGenerationTimeoutException(
    timeoutMs: Long,
    cause: Throwable,
) : RuntimeException("Local generation timed out after ${timeoutMs / 1_000}s and was cancelled", cause)
