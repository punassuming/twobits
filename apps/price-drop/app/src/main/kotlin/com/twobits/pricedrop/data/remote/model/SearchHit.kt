package com.twobits.pricedrop.data.remote.model

import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProductOffer

/** A normalized product search result surfaced to the Search UI. */
data class SearchHit(
    val title: String,
    val price: Double?,
    val source: String,
    val url: String,
    val confidence: Int = 0,
    val canonicalProductId: String = "",
    val identity: ProductIdentity = ProductIdentity(),
    val imageUrl: String = "",
    val offers: List<ProductOffer> = emptyList(),
)

/** A barcode → product resolution result. */
data class BarcodeMatch(
    val found: Boolean,
    val title: String = "",
    val asin: String = "",
    val imageUrl: String = "",
    val price: Double? = null,
    val url: String = "",
)
