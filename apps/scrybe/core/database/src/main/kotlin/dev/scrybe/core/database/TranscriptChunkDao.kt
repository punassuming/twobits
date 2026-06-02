package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranscriptChunkDao {
    @Query("SELECT * FROM transcript_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getChunksForSession(sessionId: String): List<TranscriptChunkEntity>

    @Query("SELECT * FROM transcript_chunks WHERE sessionId = :sessionId AND chunkIndex = :chunkIndex LIMIT 1")
    suspend fun getChunkByIndex(
        sessionId: String,
        chunkIndex: Int,
    ): TranscriptChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: TranscriptChunkEntity)

    @Query("DELETE FROM transcript_chunks WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
