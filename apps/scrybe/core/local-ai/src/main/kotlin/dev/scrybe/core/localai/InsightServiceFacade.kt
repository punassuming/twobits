package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import dev.scrybe.core.transcription.DebugLogStore
import dev.scrybe.core.transcription.InsightService
import dev.scrybe.core.transcription.OpenAiInsightService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiInsightService,
        private val localService: LocalInsightService,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val debugLogStore: DebugLogStore,
    ) : InsightService {
        override suspend fun analyzeSentiment(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> =
            if (routedLocal("insight-sentiment")) {
                localService.analyzeSentiment(transcriptText, durationMs, providerType)
            } else {
                openAiService.analyzeSentiment(transcriptText, durationMs, providerType)
            }

        override suspend fun extractTopics(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> =
            if (routedLocal("insight-topics")) {
                localService.extractTopics(transcriptText, durationMs, providerType)
            } else {
                openAiService.extractTopics(transcriptText, durationMs, providerType)
            }

        // Records the routing decision itself when debug is on: an on-device run makes no network
        // calls, so without this the AI call log shows nothing for the whole insight pass —
        // indistinguishable from insights never running.
        private suspend fun routedLocal(op: String): Boolean {
            val local = preferencesDataStore.aiFeaturesProvider.first() == ProviderType.LOCAL.name
            if (local && preferencesDataStore.debugDiarization.first()) {
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = op,
                        endpoint = "on-device",
                        model = "local",
                        requestSummary = "AI features source is Local — routed to on-device model, not OpenAI",
                        success = true,
                    ),
                )
            }
            return local
        }
    }
