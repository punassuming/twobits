package dev.scrybe.core.model

import java.time.Instant

data class RecordingSession(
    val id: String,
    val title: String,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: AudioFormat,
    val status: SessionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
