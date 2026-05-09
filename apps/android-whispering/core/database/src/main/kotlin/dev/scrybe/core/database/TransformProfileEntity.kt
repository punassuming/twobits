package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transform_profiles")
data class TransformProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val steps: String,
    val providerType: String,
    val isDefault: Boolean,
    val modelName: String? = null,
)
