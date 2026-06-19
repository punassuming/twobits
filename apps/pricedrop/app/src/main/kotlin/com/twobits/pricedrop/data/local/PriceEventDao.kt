package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twobits.pricedrop.data.model.PriceEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceEventDao {
    @Query("SELECT * FROM price_events WHERE productId = :productId ORDER BY recordedAt ASC")
    fun observeForProduct(productId: Long): Flow<List<PriceEvent>>

    @Query("SELECT * FROM price_events WHERE productId = :productId ORDER BY recordedAt ASC")
    suspend fun getForProduct(productId: Long): List<PriceEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PriceEvent): Long

    @Query("DELETE FROM price_events WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)
}
