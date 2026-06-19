package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_events")
data class PriceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    /** Observed list price at this point in time. */
    val price: Double,
    /** Estimated effective price (base + shipping + fees − coupon) when known; else equals [price]. */
    val effectivePrice: Double = price,
    val retailer: String = "",
    val recordedAt: Long = System.currentTimeMillis(),
)
