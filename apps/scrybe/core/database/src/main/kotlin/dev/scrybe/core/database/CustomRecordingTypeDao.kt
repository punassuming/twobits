package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRecordingTypeDao {
    @Query("SELECT * FROM custom_recording_types ORDER BY createdAt ASC")
    fun getAllTypes(): Flow<List<CustomRecordingTypeEntity>>

    @Query("SELECT * FROM custom_recording_types WHERE id = :id LIMIT 1")
    suspend fun getTypeById(id: String): CustomRecordingTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertType(type: CustomRecordingTypeEntity)

    @Query("DELETE FROM custom_recording_types WHERE id = :id")
    suspend fun deleteType(id: String)
}
