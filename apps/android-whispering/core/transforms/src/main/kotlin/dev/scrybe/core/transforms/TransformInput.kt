package dev.scrybe.core.transforms

data class TransformInput(
    val sessionId: String,
    val transcriptId: String,
    val transcriptText: String,
    val currentText: String,
    val profileId: String,
    val systemPrompt: String,
    val combinedTranscriptText: String? = null,
    /**
     * Optional override for the OpenAI model used to run this transformation.
     * When null, the provider falls back to the user's configured default.
     */
    val modelName: String? = null,
)
