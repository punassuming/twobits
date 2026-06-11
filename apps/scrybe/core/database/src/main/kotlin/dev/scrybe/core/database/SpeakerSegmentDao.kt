package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerSegmentDao {
    @Query("SELECT * FROM speaker_segments WHERE sessionId = :sessionId ORDER BY startMs")
    fun getSegmentsForSession(sessionId: String): Flow<List<SpeakerSegmentEntity>>

    @Query("SELECT * FROM speaker_segments WHERE sessionId = :sessionId ORDER BY startMs")
    suspend fun getSegmentsOnce(sessionId: String): List<SpeakerSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SpeakerSegmentEntity>)

    @Query("DELETE FROM speaker_segments WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("UPDATE speaker_segments SET speakerLabel = :label WHERE sessionId = :sessionId AND speakerId = :speakerId")
    suspend fun updateSpeakerLabel(
        sessionId: String,
        speakerId: String,
        label: String?,
    )

    @Query("UPDATE speaker_segments SET personId = :personId WHERE sessionId = :sessionId AND speakerId = :speakerId")
    suspend fun updatePersonId(
        sessionId: String,
        speakerId: String,
        personId: String?,
    )

    @Query("SELECT DISTINCT sessionId FROM speaker_segments WHERE personId = :personId")
    suspend fun getSessionIdsForPerson(personId: String): List<String>

    @Query("SELECT * FROM speaker_segments WHERE personId IS NOT NULL")
    fun getAllSegmentsWithPerson(): Flow<List<SpeakerSegmentEntity>>

    @Query("UPDATE speaker_segments SET speakerId = :targetId WHERE sessionId = :sessionId AND speakerId = :sourceId")
    suspend fun mergeSpeakerId(
        sessionId: String,
        sourceId: String,
        targetId: String,
    )

    @Query("DELETE FROM speaker_segments WHERE id = :id")
    suspend fun deleteSegmentById(id: String)
}
