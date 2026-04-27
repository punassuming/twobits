package dev.scrybe.core.localai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transforms.TransformInput
import dev.scrybe.core.transforms.TransformResult
import dev.scrybe.core.transforms.TransformationProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTransformationProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val modelManager: LocalModelManager,
    ) : TransformationProvider {
        override val providerType: ProviderType = ProviderType.LOCAL

        override suspend fun transform(input: TransformInput): Result<TransformResult> =
            runCatching {
                val modelFile =
                    modelManager.gemmaModelFile()
                        ?: return Result.failure(IllegalStateException("Gemma model not downloaded"))

                val transcript = input.combinedTranscriptText ?: input.transcriptText
                val prompt =
                    """
                    |${input.systemPrompt}
                    |
                    |Transcript:
                    |$transcript
                    |
                    |Output only the result, no explanations.
                    """.trimMargin()

                GemmaEngine(context, modelFile).use { engine ->
                    val response = engine.generate(prompt)
                    TransformResult(
                        transformedText = response.trim(),
                        modelName = "gemma2-2b-it",
                    )
                }
            }
    }
