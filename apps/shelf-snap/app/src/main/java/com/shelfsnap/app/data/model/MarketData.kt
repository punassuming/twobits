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
    val sourceUrl: String = "",
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
    val retrievedAt: Long = 0L,
    /** SearchProvider key of the web-search backend used, or "" when search was disabled. */
    val searchProviderKey: String = "",
    /** Number of web results that fed the estimate; 0 with no error = search found nothing. */
    val searchResultCount: Int = 0,
    /** Non-null when the web search itself failed (auth, network, HTTP error). */
    val searchError: String? = null,
    /** Transparency detail (queries, timings, pages read); null for older saved research. */
    val debug: MarketResearchDebug? = null,
)

/** A cited source backing a price estimate. */
data class Citation(
    val label: String,
    val url: String = "",
)

/** One web-search query that was run during research, with how many results it returned. */
data class MarketQuery(
    /** Short provenance label, e.g. "Jina AI" or "Brave Search". */
    val label: String,
    val query: String,
    val resultCount: Int = 0,
)

/**
 * Behind-the-scenes detail for a research run, surfaced in the Market tab's "Debug info"
 * panel for transparency: which queries ran, how the providers performed, and timings.
 * Nullable on [MarketResearch] so research saved before this existed stays loadable.
 */
data class MarketResearchDebug(
    val queries: List<MarketQuery> = emptyList(),
    /** Number of distinct listing pages opened/read via the Jina Reader. */
    val pagesRead: Int = 0,
    /** Web-search phase duration in millis. */
    val searchMs: Long = 0L,
    /** Page-reading (Jina Reader) phase duration in millis. */
    val readMs: Long = 0L,
    /** LLM synthesis phase duration in millis. */
    val synthesisMs: Long = 0L,
    /** Total research duration in millis. */
    val totalMs: Long = 0L,
)

/**
 * A listing of an item on a selling platform. [status] tracks the lifecycle so the
 * inventory can show Listed/Sold badges.
 */
data class PlatformListing(
    val platformKey: String,
    val status: ListingStatus,
    val price: Double,
    val listingUrl: String? = null,
)

enum class ListingStatus {
    ACTIVE,
    SOLD,
    DRAFT,
    EXPIRED,
}
