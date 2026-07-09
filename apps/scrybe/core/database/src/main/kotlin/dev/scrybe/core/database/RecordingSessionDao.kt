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

    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessionsOnce(): List<RecordingSessionEntity>

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

    @Query("SELECT audioFilePath FROM recording_sessions")
    suspend fun getAllAudioFilePaths(): List<String>

    @Query("SELECT * FROM recording_sessions WHERE audioFilePath = :path LIMIT 1")
    suspend fun getSessionByAudioFilePath(path: String): RecordingSessionEntity?

    @Query("SELECT COUNT(*) FROM recording_sessions WHERE audioFilePath = :path")
    suspend fun countSessionsByAudioFilePath(path: String): Int

    @Query("SELECT * FROM recording_sessions WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getSessionsByFolder(folderId: String): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE folderId IS NULL ORDER BY createdAt DESC")
    fun getSessionsWithoutFolder(): Flow<List<RecordingSessionEntity>>

    @Query("UPDATE recording_sessions SET folderId = :folderId, updatedAt = :updatedAt WHERE id IN (:sessionIds)")
    suspend fun moveSessionsToFolder(
        sessionIds: List<String>,
        folderId: String?,
        updatedAt: Long,
    )

    @Query("UPDATE recording_sessions SET locationLat = :lat, locationLng = :lng, locationLabel = :label, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLocation(
        id: String,
        lat: Double?,
        lng: Double?,
        label: String?,
        updatedAt: Long,
    )

    @Query("UPDATE recording_sessions SET sentimentJson = :sentimentJson, topicsJson = :topicsJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateInsights(
        id: String,
        sentimentJson: String?,
        topicsJson: String?,
        updatedAt: Long,
    )

    /**
     * Reassigns every recording of a (deleted) custom type back to the Journal mode so no
     * session is left pointing at a type that no longer exists.
     */
    @Query(
        "UPDATE recording_sessions SET mode = :journalMode, customTypeId = NULL, updatedAt = :updatedAt " +
            "WHERE customTypeId = :customTypeId",
    )
    suspend fun reassignCustomTypeToJournal(
        customTypeId: String,
        journalMode: String,
        updatedAt: Long,
    )
}
