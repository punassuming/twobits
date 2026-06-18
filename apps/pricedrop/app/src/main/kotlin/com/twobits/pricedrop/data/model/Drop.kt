package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drops")
data class Drop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: String,
    val oldPrice: Double? = null,
    val newPrice: Double? = null,
    val couponCode: String = "",
    val couponDiscount: String = "",
    val retailer: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false,
)
