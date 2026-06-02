package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transform_runs")
data class TransformRunEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val profileId: String,
    val inputTranscriptId: String,
    val outputTranscriptId: String?,
    val status: String,
    val errorMessage: String?,
    val startedAt: Long,
    val completedAt: Long?,
)
