package dev.scrybe.core.model

data class CustomRecordingType(
    val id: String,
    val name: String,
    val defaultProfileId: String?,
    val createdAt: Long,
)
