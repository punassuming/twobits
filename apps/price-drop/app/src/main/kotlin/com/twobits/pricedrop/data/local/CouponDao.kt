package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twobits.pricedrop.data.model.Coupon
import kotlinx.coroutines.flow.Flow

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons WHERE productId = :productId ORDER BY lastCheckedAt DESC")
    fun observeForProduct(productId: Long): Flow<List<Coupon>>

    @Query("SELECT * FROM coupons WHERE productId = :productId ORDER BY lastCheckedAt DESC")
    suspend fun getForProduct(productId: Long): List<Coupon>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coupons: List<Coupon>)

    @Query("DELETE FROM coupons WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)

    @Query("DELETE FROM coupons WHERE productId = :productId AND source != 'manual'")
    suspend fun deleteProviderPromotions(productId: Long)
}
