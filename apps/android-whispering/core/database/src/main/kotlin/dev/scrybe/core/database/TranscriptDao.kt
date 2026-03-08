package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getTranscriptsForSession(sessionId: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    suspend fun getTranscriptById(id: String): TranscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteTranscript(id: String)
}
