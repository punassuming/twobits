package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.AiCallDebugEntry
import dev.scrybe.core.transcription.AiCallDebugStore
import dev.scrybe.core.transcription.TranscriptResult
import dev.scrybe.core.transcription.TranscriptionOptions
import dev.scrybe.core.transcription.TranscriptionProvider
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionProvider
    @Inject
    constructor(
        private val modelManager: LocalModelManager,
        private val aiCallDebugStore: AiCallDebugStore,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : TranscriptionProvider {
        override val providerType: ProviderType = ProviderType.LOCAL

        override suspend fun transcribe(
            audioFile: File,
            options: TranscriptionOptions,
        ): Result<TranscriptResult> {
            // An on-device run makes no network calls, so without this the AI call log shows
            // nothing at all for a local transcription regardless of model or outcome —
            // indistinguishable from transcription never running (the exact bug already fixed
            // once for diarization/insights via DiarizationServiceFacade/InsightServiceFacade,
            // but missed here since this provider has no equivalent local/cloud facade to carry
            // the fix — it's the local branch of TranscriptionOrchestrator's provider map).
            val debugEnabled = preferencesDataStore.debugDiarization.first()

            suspend fun record(
                success: Boolean,
                snippet: String,
            ) {
                if (!debugEnabled) return
                aiCallDebugStore.record(
                    AiCallDebugEntry(
                        timestampMs = System.currentTimeMillis(),
                        op = "transcribe",
                        endpoint = "on-device",
                        model = modelManager.selectedWhisperModel.value.filePrefix,
                        requestSummary = "file=${audioFile.name}",
                        success = success,
                        responseSnippet = snippet,
                    ),
                )
            }

            return runCatching {
                val (modelDir, model) =
                    modelManager.activeWhisperDir()
                        ?: run {
                            record(success = false, snippet = "Whisper model not downloaded")
                            return Result.failure(IllegalStateException("Whisper model not downloaded"))
                        }

                val samples = AudioDecoder.decodeToFloatArray(audioFile)
                WhisperEngine(modelDir, model.filePrefix).use { engine ->
                    val text = engine.transcribe(samples)
                    record(success = true, snippet = "${text.length} chars")
                    TranscriptResult(
                        text = text,
                        language = "en",
                        durationSeconds = null,
                    )
                }
            }.onFailure { error ->
                record(success = false, snippet = "${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }
