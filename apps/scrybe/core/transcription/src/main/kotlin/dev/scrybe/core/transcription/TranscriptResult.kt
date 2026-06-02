package dev.scrybe.core.transcription

data class TranscriptResult(
    val text: String,
    val language: String?,
    val durationSeconds: Double?,
)
