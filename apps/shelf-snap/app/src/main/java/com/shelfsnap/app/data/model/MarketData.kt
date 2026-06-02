package com.shelfsnap.app.data.model

/**
 * A single comparable listing found during market research, used to justify a
 * suggested price. Always carries a [sourceUrl] so the estimate can cite its source.
 */
data class MarketComp(
    val platformKey: String,
    val title: String,
    val price: Double,
    /** True if this is a completed/sold listing (stronger price signal than active). */
    val sold: Boolean,
    /** Human-readable recency, e.g. "3 days ago" or "Active". */
    val date: String,
    val sourceUrl: String = ""
)

/**
 * The result of researching an item's resale value: comparable listings, a
 * per-platform suggested price, and the citations that back the estimate.
 *
 * [suggestedPrices] is keyed by [Platform.key]. [confidencePercent] reflects how
 * much signal the comps provide (more sold comps across more platforms → higher).
 */
data class MarketResearch(
    val comps: List<MarketComp> = emptyList(),
    val suggestedPrices: Map<String, Double> = emptyMap(),
    val averageSoldPrice: Double = 0.0,
    val lowPrice: Double = 0.0,
    val highPrice: Double = 0.0,
    val confidencePercent: Int = 0,
    /** Source URLs / descriptions cited for the estimate. */
    val citations: List<Citation> = emptyList(),
    /** When the research was last refreshed (epoch millis), or 0 if never. */
    val retrievedAt: Long = 0L
)

/** A cited source backing a price estimate. */
data class Citation(
    val label: String,
    val url: String = ""
)

/**
 * A listing of an item on a selling platform. [status] tracks the lifecycle so the
 * inventory can show Listed/Sold badges.
 */
data class PlatformListing(
    val platformKey: String,
    val status: ListingStatus,
    val price: Double
)

enum class ListingStatus {
    ACTIVE,
    SOLD,
    DRAFT,
    EXPIRED
}
