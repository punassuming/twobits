package dev.scrybe.core.model

import java.time.Instant

data class Folder(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val createdAt: Instant,
)
