package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twobits.pricedrop.data.model.Activity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE productId = :productId ORDER BY timestamp DESC")
    fun observeForProduct(productId: Long): Flow<List<Activity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: Activity): Long

    @Query("DELETE FROM activities WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)
}
