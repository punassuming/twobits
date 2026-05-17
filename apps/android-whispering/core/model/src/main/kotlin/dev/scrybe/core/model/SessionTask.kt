package dev.scrybe.core.model

import java.time.Instant

data class SessionTask(
    val id: String,
    val sessionId: String,
    val text: String,
    val assignee: String? = null,
    val dueLabel: String? = null,
    val isDone: Boolean = false,
    val createdAt: Instant,
)
