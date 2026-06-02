package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript_chunks",
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "chunkIndex"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TranscriptChunkEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val status: String,
    val text: String? = null,
    val createdAt: Long,
)
