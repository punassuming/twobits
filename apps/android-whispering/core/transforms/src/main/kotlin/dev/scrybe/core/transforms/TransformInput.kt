package dev.scrybe.core.transforms

data class TransformInput(
    val sessionId: String,
    val transcriptId: String,
    val transcriptText: String,
    val currentText: String,
    val profileId: String,
    val systemPrompt: String,
    val combinedTranscriptText: String? = null,
)
