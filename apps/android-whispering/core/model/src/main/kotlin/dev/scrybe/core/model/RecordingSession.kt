package dev.scrybe.core.model

import java.time.Instant

data class RecordingSession(
    val id: String,
    val title: String,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: AudioFormat,
    val sampleRateHz: Int,
    val encodingBitRate: Int,
    val channelCount: Int,
    val waveformSamples: List<Float>,
    val status: SessionStatus,
    val isArchived: Boolean,
    val estimatedTranscriptionCostUsd: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
