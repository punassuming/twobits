package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "speaker_segments",
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
data class SpeakerSegmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val speakerId: String,
    val speakerLabel: String? = null,
    val personId: String? = null,
    val startMs: Long,
    val endMs: Long,
)
