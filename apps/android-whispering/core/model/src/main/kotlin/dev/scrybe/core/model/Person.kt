package dev.scrybe.core.model

import java.time.Instant

data class Person(
    val id: String,
    val name: String,
    val voiceEmbeddingJson: String?,
    val createdAt: Instant,
)
