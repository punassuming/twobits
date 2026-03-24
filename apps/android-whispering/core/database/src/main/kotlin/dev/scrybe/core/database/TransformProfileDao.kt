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

    @Query("SELECT * FROM transform_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): TransformProfileEntity?

    @Query("SELECT * FROM transform_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): TransformProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: TransformProfileEntity)

    @Query("UPDATE transform_profiles SET isDefault = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setDefaultProfile(profileId: String)

    @Query("UPDATE transform_profiles SET isDefault = 0")
    suspend fun clearDefaultProfile()

    @Query("DELETE FROM transform_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)
}
