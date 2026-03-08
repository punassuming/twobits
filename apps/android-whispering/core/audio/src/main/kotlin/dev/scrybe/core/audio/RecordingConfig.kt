package dev.scrybe.core.audio

import dev.scrybe.core.model.AudioFormat

data class RecordingConfig(
    val outputDir: String,
    val audioFormat: AudioFormat = AudioFormat.AAC,
    val maxDurationMs: Long = 3_600_000L,
)
