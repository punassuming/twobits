package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val tags: String,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: String,
    val sampleRateHz: Int,
    val encodingBitRate: Int,
    val channelCount: Int,
    val waveformSamples: String,
    val status: String,
    val isArchived: Boolean,
    val estimatedTranscriptionCostUsd: Double?,
    val folderId: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationLabel: String? = null,
    val sentimentJson: String? = null,
    val topicsJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
