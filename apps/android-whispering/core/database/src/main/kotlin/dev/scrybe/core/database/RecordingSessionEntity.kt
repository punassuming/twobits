package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)
