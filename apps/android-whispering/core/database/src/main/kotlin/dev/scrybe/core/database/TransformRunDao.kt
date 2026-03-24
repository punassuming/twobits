package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransformRunDao {
    @Query("SELECT * FROM transform_runs ORDER BY startedAt DESC")
    fun getAllRuns(): Flow<List<TransformRunEntity>>

    @Query("SELECT * FROM transform_runs WHERE sessionId = :sessionId ORDER BY startedAt DESC")
    fun getRunsForSession(sessionId: String): Flow<List<TransformRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: TransformRunEntity)

    @Query("DELETE FROM transform_runs WHERE sessionId = :sessionId")
    suspend fun deleteRunsForSession(sessionId: String)
}
