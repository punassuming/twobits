package dev.scrybe.core.transforms

data class TransformInput(
    val sessionId: String,
    val transcriptId: String,
    val rawText: String,
    val profileId: String,
    val systemPrompt: String,
)
