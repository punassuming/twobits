package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_observations",
    indices = [
        Index(value = ["productId", "observedAt"]),
        Index(value = ["canonicalProductId", "merchantId", "listingId", "observedAt"], unique = true),
    ],
)
data class PriceObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val canonicalProductId: String,
    val merchantId: String,
    val listingId: String? = null,
    val itemPriceMinor: Long,
    val shippingPriceMinor: Long? = null,
    val totalPriceMinor: Long,
    val currency: String,
    val availability: String,
    val observedAt: Long,
    val provider: String,
    val provenance: String,
)
