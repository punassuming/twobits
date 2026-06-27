package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_products")
data class WatchedProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val brand: String = "",
    val category: String = "",
    val currentPrice: Double = 0.0,
    val targetPrice: Double? = null,
    val alertType: String = "below_target",
    val alertThresholdPct: Int = 10,
    val imageUrl: String = "",
    val productUrl: String = "",
    val asin: String = "",
    val upc: String = "",
    val retailers: String = "",
    // Effective-price components for the best current offer.
    val shipping: Double = 0.0,
    val fees: Double = 0.0,
    val seller: String = "",
    // Provenance: which source produced the current price + match confidence (0–100).
    val source: String = "",
    val confidence: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long = 0L,
    val lastCouponCheckedAt: Long = 0L,
    val trackedHigh: Double = 0.0,
    val trackedLow: Double = Double.MAX_VALUE,
    val trackedAvg: Double = 0.0,
    val isActive: Boolean = true,
)
