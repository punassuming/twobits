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
                val selectedModel = preferencesDataStore.localGemmaModel.first()
                val modelFile =
                    modelManager.gemmaModelFile(selectedModel)
                        ?: modelManager.anyGemmaReady()?.let { modelManager.gemmaModelFile(it) }
                        ?: error("No Gemma model downloaded. Go to Settings → Provider → Local to download one.")

                val transcript = input.combinedTranscriptText ?: input.transcriptText
                val prompt =
                    "<start_of_turn>user\n" +
                        "${input.systemPrompt}\n\nTranscript:\n$transcript\n\nOutput only the result." +
                        "<end_of_turn>\n<start_of_turn>model\n"

                GemmaEngine(context, modelFile).use { engine ->
                    val response = engine.generate(prompt)
                    TransformResult(
                        transformedText = response.trim(),
                        modelName = selectedModel.displayName,
                    )
                }
            }
    }
