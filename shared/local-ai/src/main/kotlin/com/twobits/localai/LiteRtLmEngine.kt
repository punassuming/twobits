package com.twobits.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

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
    visionBackend: Backend? = null,
) : Closeable {
    private val engine: Engine

    init {
        val engineConfig =
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = visionBackend,
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

    suspend fun generate(prompt: String): String =
        withContext(Dispatchers.Default) {
            // Message has no `.text` — the response's Content.Text is unwrapped via
            // Message.toString() -> Contents.toString() -> Content.Text.toString() (confirmed
            // against the real com.google.ai.edge.litertlm.Message source, not the getting-started
            // doc, whose `.text` example doesn't match the actual class).
            conversation.sendMessage(prompt).toString()
        }

    /** EXPERIMENTAL — see class doc. [imageFile] is sent as a single image alongside [prompt]. */
    suspend fun generateWithImage(
        imageFile: File,
        prompt: String,
    ): String =
        withContext(Dispatchers.Default) {
            conversation
                .sendMessage(
                    Contents.of(
                        Content.ImageFile(imageFile.absolutePath),
                        Content.Text(prompt),
                    ),
                ).toString()
        }

    override fun close() {
        conversation.close()
        engine.close()
    }
}
