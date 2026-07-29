package dev.scrybe.core.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

internal class LiteRtLmEngine(
    context: Context,
    modelFile: File,
    systemInstruction: String? = null,
) : Closeable {
    private val engine: Engine

    init {
        val engineConfig =
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            )
        engine = Engine(engineConfig)
        engine.initialize()
    }

    // One Conversation per engine instance, used for exactly one generate() call — same
    // single-turn-per-instance shape as the GemmaEngine this replaces, so no chat history
    // accumulates across LocalLlmService/LocalTransformationProvider calls.
    private val conversation =
        engine.createConversation(
            ConversationConfig(
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
            ),
        )

    suspend fun generate(prompt: String): String =
        withContext(Dispatchers.Default) {
            conversation.sendMessage(prompt).text
        }

    override fun close() {
        conversation.close()
        engine.close()
    }
}
