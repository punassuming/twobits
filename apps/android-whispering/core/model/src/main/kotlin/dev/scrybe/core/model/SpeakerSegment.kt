package dev.scrybe.core.model

data class SpeakerSegment(
    val id: String,
    val sessionId: String,
    val speakerId: String,
    val speakerLabel: String?,
    val personId: String?,
    val startMs: Long,
    val endMs: Long,
)
