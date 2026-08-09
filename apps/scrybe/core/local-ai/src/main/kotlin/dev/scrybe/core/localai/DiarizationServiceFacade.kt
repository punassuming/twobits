package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SpeakerSegment
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import dev.scrybe.core.transcription.DebugLogStore
import dev.scrybe.core.transcription.DiarizationService
import dev.scrybe.core.transcription.OpenAiDiarizationService
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiarizationServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiDiarizationService,
        private val localService: LocalDiarizationService,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val debugLogStore: DebugLogStore,
    ) : DiarizationService {
        override suspend fun diarize(
            sessionId: String,
            audioFile: File,
            transcriptText: String,
            providerType: ProviderType,
        ): Result<List<SpeakerSegment>> =
            if (preferencesDataStore.aiFeaturesProvider.first() == ProviderType.LOCAL.name) {
                // Record the routing decision itself: an on-device run makes no network calls, so
                // without this the AI call log shows nothing at all for the whole diarization —
                // indistinguishable from diarization never running.
                if (preferencesDataStore.debugDiarization.first()) {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.AI_CALL,
                            op = "diarize",
                            endpoint = "on-device",
                            model = "local",
                            requestSummary = "AI features source is Local — routed to on-device model, not OpenAI",
                            success = true,
                        ),
                    )
                }
                localService.diarize(sessionId, audioFile, transcriptText, providerType)
            } else {
                openAiService.diarize(sessionId, audioFile, transcriptText, providerType)
            }
    }
