package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
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
    ) : InsightService {
        override suspend fun analyzeSentiment(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> =
            if (preferencesDataStore.aiFeaturesProvider.first() == ProviderType.LOCAL.name) {
                localService.analyzeSentiment(transcriptText, durationMs, providerType)
            } else {
                openAiService.analyzeSentiment(transcriptText, durationMs, providerType)
            }

        override suspend fun extractTopics(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> =
            if (preferencesDataStore.aiFeaturesProvider.first() == ProviderType.LOCAL.name) {
                localService.extractTopics(transcriptText, durationMs, providerType)
            } else {
                openAiService.extractTopics(transcriptText, durationMs, providerType)
            }
    }
