package dev.scrybe.core.localai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transforms.TransformInput
import dev.scrybe.core.transforms.TransformResult
import dev.scrybe.core.transforms.TransformationProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTransformationProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val modelManager: LocalModelManager,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : TransformationProvider {
        override val providerType: ProviderType = ProviderType.LOCAL

        override suspend fun transform(input: TransformInput): Result<TransformResult> =
            runCatching {
                val selectedModel = preferencesDataStore.localLlmModel.first()
                val modelFile =
                    modelManager.llmModelFile(selectedModel)
                        ?: modelManager.anyLlmReady()?.let { modelManager.llmModelFile(it) }
                        ?: error("No local model downloaded. Go to Settings → Provider → Local to download one.")

                val transcript = input.combinedTranscriptText ?: input.transcriptText
                val prompt = "Transcript:\n$transcript\n\nOutput only the result."

                LiteRtLmEngine(context, modelFile, systemInstruction = input.systemPrompt).use { engine ->
                    val response = engine.generate(prompt)
                    TransformResult(
                        transformedText = response.trim(),
                        modelName = selectedModel.displayName,
                    )
                }
            }
    }
