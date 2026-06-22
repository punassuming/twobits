package com.twobits.pricedrop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twobits.pricedrop.data.model.Drop
import kotlinx.coroutines.flow.Flow

@Dao
interface DropDao {
    @Query("SELECT * FROM drops WHERE isDismissed = 0 ORDER BY detectedAt DESC")
    fun observeActive(): Flow<List<Drop>>

    @Query("SELECT * FROM drops WHERE productId = :productId ORDER BY detectedAt DESC")
    fun observeForProduct(productId: Long): Flow<List<Drop>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(drop: Drop): Long

    @Query("UPDATE drops SET isDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE drops SET isDismissed = 1 WHERE productId = :productId")
    suspend fun dismissAllForProduct(productId: Long)

    @Query("UPDATE drops SET isDismissed = 1")
    suspend fun dismissAll()

    @Query("SELECT COUNT(*) FROM drops WHERE isDismissed = 0")
    fun observeActiveCount(): Flow<Int>
}
