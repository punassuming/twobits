package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twobits.pricedrop.data.model.PriceObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceObservationDao {
    @Query("SELECT * FROM price_observations WHERE productId = :productId ORDER BY observedAt ASC")
    fun observeForProduct(productId: Long): Flow<List<PriceObservationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(observation: PriceObservationEntity): Long
}
