package dev.scrybe.core.model

import java.time.Instant

data class TransformRun(
    val id: String,
    val sessionId: String,
    val profileId: String,
    val inputTranscriptId: String,
    val outputTranscriptId: String?,
    val status: TransformStatus,
    val errorMessage: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
)
