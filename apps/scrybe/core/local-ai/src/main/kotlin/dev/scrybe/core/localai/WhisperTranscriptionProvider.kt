package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import dev.scrybe.core.transcription.DebugLogStore
import dev.scrybe.core.transcription.TranscriptResult
import dev.scrybe.core.transcription.TranscriptionOptions
import dev.scrybe.core.transcription.TranscriptionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionProvider
    @Inject
    constructor(
        private val modelManager: LocalModelManager,
        private val debugLogStore: DebugLogStore,
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
                durationMs: Long? = null,
            ) {
                if (!debugEnabled) return
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "transcribe",
                        endpoint = "on-device",
                        model = modelManager.selectedWhisperModel.value.filePrefix,
                        requestSummary = "file=${audioFile.name}",
                        success = success,
                        responseSnippet = snippet,
                        durationMs = durationMs,
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

                // Decoding the recording (MediaExtractor/MediaCodec) and constructing
                // WhisperEngine (a synchronous native model load off disk) are both blocking —
                // neither hops dispatchers on its own, so a caller that launches this from a
                // bare viewModelScope.launch {} (main-thread by default) would ANR. Every
                // current caller happens to be right (auto-transcribe uses an IO-scoped
                // coroutine), but that's an easy contract to break for a new one, so it's
                // enforced here instead of trusted at every call site.
                withContext(Dispatchers.IO) {
                    // Measures decode + model-load + chunked-transcribe together, not just the
                    // model call — this is what actually answers "why does local transcription
                    // feel slow", since decode/model-construction can dominate for a short clip.
                    val startedAtMs = System.currentTimeMillis()
                    val decoded = AudioDecoder.decode(audioFile)
                    // Recorded — and awaited — immediately before the risky native call below, not
                    // after: a native crash in WhisperEngine's ONNX model load/decode kills the
                    // process with zero chance for any Kotlin try/catch to run, so this entry
                    // already being safely on disk is the only way to later see, from the debug
                    // log alone, that a transcription was in flight when it crashed — there's no
                    // matching "transcribe" entry after it if so (see DebugLogStore.staleStartWarning).
                    if (debugEnabled) {
                        debugLogStore.record(
                            DebugLogEntry(
                                timestampMs = startedAtMs,
                                type = DebugLogEntryType.AI_CALL,
                                op = "transcribe-start",
                                endpoint = "on-device",
                                model = model.filePrefix,
                                requestSummary = "file=${audioFile.name}",
                                success = true,
                            ),
                        )
                    }
                    WhisperEngine(modelDir, model.filePrefix).use { engine ->
                        val text = engine.transcribe(decoded.samples, decoded.sampleRateHz)
                        record(success = true, snippet = "${text.length} chars", durationMs = System.currentTimeMillis() - startedAtMs)
                        TranscriptResult(
                            text = text,
                            language = "en",
                            durationSeconds = null,
                        )
                    }
                }
            }.onFailure { error ->
                record(success = false, snippet = "${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }
