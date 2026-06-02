package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val voiceEmbeddingJson: String? = null,
    val createdAt: Long,
)
