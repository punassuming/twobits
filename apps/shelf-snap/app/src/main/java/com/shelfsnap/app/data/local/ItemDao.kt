package com.shelfsnap.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query(
        """SELECT * FROM items
           WHERE (:query = '' OR
                  lower(category) LIKE '%' || lower(:query) || '%' OR
                  lower(description) LIKE '%' || lower(:query) || '%' OR
                  lower(brand) LIKE '%' || lower(:query) || '%')
           ORDER BY updatedAt DESC"""
    )
    fun observeFiltered(query: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM items WHERE isDraft = 0")
    fun observeConfirmedCount(): Flow<Int>
}
