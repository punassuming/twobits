package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twobits.pricedrop.data.model.Offer
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers WHERE productId = :productId ORDER BY effectivePrice ASC")
    fun observeForProduct(productId: Long): Flow<List<Offer>>

    @Query("SELECT * FROM offers WHERE productId = :productId ORDER BY effectivePrice ASC")
    suspend fun getForProduct(productId: Long): List<Offer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<Offer>)

    /** Replace the offer set for a product atomically on each refresh. */
    @Query("DELETE FROM offers WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)
}
