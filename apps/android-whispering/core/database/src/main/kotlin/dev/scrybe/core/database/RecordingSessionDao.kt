package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getActiveSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchivedSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun getSessionById(id: String): Flow<RecordingSessionEntity?>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSessionByIdOnce(id: String): RecordingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity)

    @Update
    suspend fun updateSession(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE recording_sessions SET status = :newStatus, updatedAt = :updatedAt WHERE status = :oldStatus")
    suspend fun updateSessionsByStatus(
        oldStatus: String,
        newStatus: String,
        updatedAt: Long,
    )
}
