package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_recording_types")
data class CustomRecordingTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultProfileId: String? = null,
    val createdAt: Long,
    // Name from the shared CustomTypeIcon set (core/common); null renders the default label icon.
    val iconName: String? = null,
)
