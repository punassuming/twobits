package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType

interface InsightService {
    suspend fun analyzeSentiment(
        transcriptText: String,
        durationMs: Long,
        providerType: ProviderType,
    ): Result<String>

    suspend fun extractTopics(
        transcriptText: String,
        durationMs: Long,
        providerType: ProviderType,
    ): Result<String>
}
