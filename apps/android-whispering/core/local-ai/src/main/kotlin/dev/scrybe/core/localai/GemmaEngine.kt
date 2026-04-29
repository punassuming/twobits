package dev.scrybe.core.localai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

internal class GemmaEngine(
    context: Context,
    modelFile: File,
) : Closeable {
    private val llm: LlmInference

    init {
        val options =
            LlmInference.LlmInferenceOptions
                .builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    suspend fun generate(prompt: String): String =
        withContext(Dispatchers.Default) {
            llm.generateResponse(prompt)
        }

    override fun close() {
        llm.close()
    }
}
