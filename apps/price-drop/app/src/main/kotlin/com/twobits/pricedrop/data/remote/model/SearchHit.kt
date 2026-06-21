package com.twobits.pricedrop.data.remote.model

/** A normalized product search result surfaced to the Search UI. */
data class SearchHit(
    val title: String,
    val price: Double?,
    val source: String,
    val url: String,
    val confidence: Int = 0,
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
