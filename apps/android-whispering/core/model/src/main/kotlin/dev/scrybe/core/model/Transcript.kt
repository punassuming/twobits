package dev.scrybe.core.model

import java.time.Instant

data class Transcript(
    val id: String,
    val sessionId: String,
    val content: String,
    val type: TranscriptType,
    val providerType: ProviderType?,
    val transformProfileId: String?,
    val transformRunId: String?,
    val createdAt: Instant,
)
