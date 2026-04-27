package dev.scrybe.core.localai

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.TranscriptResult
import dev.scrybe.core.transcription.TranscriptionOptions
import dev.scrybe.core.transcription.TranscriptionProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionProvider
    @Inject
    constructor(
        private val modelManager: LocalModelManager,
    ) : TranscriptionProvider {
        override val providerType: ProviderType = ProviderType.LOCAL

        override suspend fun transcribe(
            audioFile: File,
            options: TranscriptionOptions,
        ): Result<TranscriptResult> =
            runCatching {
                val modelDir =
                    modelManager.whisperModelDir()
                        ?: return Result.failure(IllegalStateException("Whisper model not downloaded"))

                val samples = AudioDecoder.decodeToFloatArray(audioFile)
                WhisperEngine(modelDir).use { engine ->
                    val text = engine.transcribe(samples)
                    TranscriptResult(
                        text = text,
                        language = "en",
                        durationSeconds = null,
                    )
                }
            }
    }
