package dev.scrybe.core.audio

data class RecordingTelemetry(
    val elapsedMs: Long = 0L,
    val amplitudeRatio: Float = 0f,
)
