package dev.scrybe.core.transcription

data class TranscriptionOptions(
    val language: String? = null,
    val prompt: String? = null,
    val responseFormat: String = "json",
)
