package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransformProfileDao {
    @Query("SELECT * FROM transform_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<TransformProfileEntity>>

    @Query("SELECT * FROM transform_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): TransformProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: TransformProfileEntity)

    @Query("DELETE FROM transform_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)
}
