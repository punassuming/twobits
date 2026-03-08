package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun getAllProviderConfigs(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE providerType = :providerType LIMIT 1")
    suspend fun getProviderConfig(providerType: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderConfig(config: ProviderConfigEntity)

    @Update
    suspend fun updateProviderConfig(config: ProviderConfigEntity)
}
