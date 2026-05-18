package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_tasks",
    indices = [Index("sessionId")],
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SessionTaskEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val text: String,
    val assignee: String? = null,
    val dueLabel: String? = null,
    val isDone: Boolean = false,
    val createdAt: Long,
)
