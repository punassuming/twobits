package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single retailer/seller offer for a watched product. Offers are refreshed on each
 * price check and compared by [effectivePrice] (lowest verified first) in Product Detail.
 */
@Entity(tableName = "offers")
data class Offer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val retailer: String,
    val seller: String = "",
    val basePrice: Double,
    val shipping: Double = 0.0,
    val fees: Double = 0.0,
    val couponCode: String = "",
    val couponDiscount: Double = 0.0,
    /** base + shipping + fees − couponDiscount, never below 0. */
    val effectivePrice: Double,
    /** "in_stock" | "out_of_stock" | "unknown". */
    val availability: String = "unknown",
    /** Match/seller confidence 0–100. */
    val confidence: Int = 0,
    val url: String = "",
    /** "web" | "shopping" | "amazon" | "manual" | "ai" — drives the source badge. */
    val source: String = "",
    val lastCheckedAt: Long = System.currentTimeMillis(),
)
