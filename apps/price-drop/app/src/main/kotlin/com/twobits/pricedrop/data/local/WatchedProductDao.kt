package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.twobits.pricedrop.data.model.WatchedProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedProductDao {
    @Query("SELECT * FROM watched_products WHERE isActive = 1 ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchedProduct>>

    @Query("SELECT * FROM watched_products WHERE id = :id")
    suspend fun getById(id: Long): WatchedProduct?

    @Query("SELECT COUNT(*) FROM watched_products WHERE isActive = 1")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: WatchedProduct): Long

    @Update
    suspend fun update(product: WatchedProduct)

    @Delete
    suspend fun delete(product: WatchedProduct)

    @Query("UPDATE watched_products SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE watched_products SET currentPrice = :price, lastCheckedAt = :checkedAt WHERE id = :id")
    suspend fun updatePrice(
        id: Long,
        price: Double,
        checkedAt: Long,
    )

    @Query("UPDATE watched_products SET lastCouponCheckedAt = :ts WHERE id = :id")
    suspend fun updateCouponCheckedAt(
        id: Long,
        ts: Long,
    )
}
