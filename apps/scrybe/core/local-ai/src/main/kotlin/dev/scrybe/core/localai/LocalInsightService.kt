package dev.scrybe.core.localai

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.InsightService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInsightService
    @Inject
    constructor(
        private val localLlmService: LocalLlmService,
    ) : InsightService {
        override suspend fun analyzeSentiment(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> = localLlmService.analyzeSentiment(transcriptText, durationMs)

        override suspend fun extractTopics(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> = localLlmService.extractTopics(transcriptText, durationMs)
    }
