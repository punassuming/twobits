package dev.scrybe.core.model

data class CustomRecordingType(
    val id: String,
    val name: String,
    val outputDescription: String,
    val systemPrompt: String,
    val createdAt: Long,
)
