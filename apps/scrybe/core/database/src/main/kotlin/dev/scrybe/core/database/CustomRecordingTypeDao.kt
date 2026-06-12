package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRecordingTypeDao {
    @Query("SELECT * FROM custom_recording_types ORDER BY createdAt ASC")
    fun getAll(): Flow<List<CustomRecordingTypeEntity>>

    @Query("SELECT * FROM custom_recording_types WHERE id = :id")
    suspend fun getById(id: String): CustomRecordingTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(type: CustomRecordingTypeEntity)

    @Query("DELETE FROM custom_recording_types WHERE id = :id")
    suspend fun delete(id: String)
}
