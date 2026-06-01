package dev.scrybe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SentimentSegment(
    val startMs: Long,
    val endMs: Long,
    val sentiment: String,
)

@Serializable
data class TopicMarker(
    val timeMs: Long,
    val label: String,
)
