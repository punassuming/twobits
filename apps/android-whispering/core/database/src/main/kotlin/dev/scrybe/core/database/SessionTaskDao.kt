package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SessionTaskCount(
    val sessionId: String,
    val count: Int,
)

@Dao
interface SessionTaskDao {
    @Query("SELECT * FROM session_tasks WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getTasksForSession(sessionId: String): Flow<List<SessionTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<SessionTaskEntity>)

    @Query("UPDATE session_tasks SET isDone = :isDone WHERE id = :id")
    suspend fun updateIsDone(
        id: String,
        isDone: Boolean,
    )

    @Query("SELECT COUNT(*) FROM session_tasks WHERE sessionId = :sessionId AND isDone = 0")
    suspend fun getOpenTaskCount(sessionId: String): Int

    @Query(
        "SELECT sessionId, COUNT(*) as count FROM session_tasks " +
            "WHERE isDone = 0 GROUP BY sessionId",
    )
    fun getOpenTaskCountsPerSession(): Flow<List<SessionTaskCount>>
}
