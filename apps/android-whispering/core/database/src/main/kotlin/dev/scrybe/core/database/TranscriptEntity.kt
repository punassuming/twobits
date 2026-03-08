package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class TranscriptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val content: String,
    val type: String,
    val providerType: String?,
    val transformProfileId: String?,
    val transformRunId: String?,
    val createdAt: Long,
)
