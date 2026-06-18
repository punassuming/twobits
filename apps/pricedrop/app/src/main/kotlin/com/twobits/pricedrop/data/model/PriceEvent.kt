package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_events")
data class PriceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val price: Double,
    val retailer: String = "",
    val recordedAt: Long = System.currentTimeMillis(),
)
