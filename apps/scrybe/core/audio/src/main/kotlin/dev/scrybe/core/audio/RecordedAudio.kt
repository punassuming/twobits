package dev.scrybe.core.audio

import dev.scrybe.core.model.AudioFormat

data class RecordedAudio(
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: AudioFormat,
    val sampleRateHz: Int,
    val encodingBitRate: Int,
    val channelCount: Int,
    val waveformSamples: List<Float>,
)
