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
    /**
     * True when web search ran but no enabled provider could return completed-sale data — only
     * SearchAPI.io maps eBay queries onto a sold-listings engine, so with Serper/Jina/Brave a
     * verified sold comp is impossible no matter how many results come back. Drives the Market
     * tab's explanation so an empty comps list doesn't look like a transient failure.
     * Defaults false so research saved before this existed doesn't show the hint spuriously.
     */
    val soldDataUnavailable: Boolean = false,
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
    /** Raw result count the provider returned for this query, before filtering to real postings. */
    val resultCount: Int = 0,
    /**
     * Of [resultCount], how many resolved to an actual marketplace posting (a recognizable
     * platform key). Equal to [resultCount] unless results came back that didn't resolve to a
     * marketplace platform — seeing e.g. "8 found, 0 recognized" pinpoints a classification
     * gap (wrong platform, an unscoped query, a site indexed differently than the query
     * assumed) instead of looking identical to a query that truly returned nothing.
     */
    val legitResultCount: Int = 0,
    /** Provider-specific failure for this attempt; null for successful empty results. */
    val error: String? = null,
)

/**
 * One Jina Reader page-open attempt during the verification phase, with whether it confirmed a
 * real listing and — when it didn't — why, so an empty comps list is diagnosable instead of a
 * black box. Every attempt is recorded, not just confirmed ones, mirroring [MarketQuery] not
 * hiding failed queries either.
 */
data class PageReadOutcome(
    /** Display name of the marketplace this candidate page belongs to, e.g. "Mercari". */
    val marketplace: String,
    val url: String,
    val verified: Boolean,
    /** Null when [verified]; a short human-readable reason otherwise, e.g. "page too short (140 chars)" or a matched dead-page phrase. */
    val reason: String? = null,
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
    /** Every Jina Reader page-open attempt (verified or rejected, with why) — see [PageReadOutcome]. */
    val pageOutcomes: List<PageReadOutcome> = emptyList(),
    /** Every outbound API call this run made: search queries + page reads + the AI synthesis call. */
    val totalApiCalls: Int = 0,
    /** Distinct services called, e.g. ["Serper.dev", "Jina AI Search", "Jina Reader", "OpenAI"]. */
    val servicesUsed: List<String> = emptyList(),
    /** Web-search phase duration in millis. */
    val searchMs: Long = 0L,
    /** Page-reading (Jina Reader) phase duration in millis. */
    val readMs: Long = 0L,
    /** LLM synthesis phase duration in millis. */
    val synthesisMs: Long = 0L,
    /** Total research duration in millis. */
    val totalMs: Long = 0L,
    /** First 800 chars of the synthesis system prompt for transparency. */
    val synthesisPrompt: String? = null,
)

/**
 * A listing of an item on a selling platform. [status] tracks the lifecycle so the
 * inventory can show Listed/Sold badges.
 *
 * [title], [description], [condition], and [shipping] hold platform-specific listing copy
 * generated at cross-list time (DRAFT status) and optionally AI-refined before publishing.
 * All nullable with defaults for backward-compat with saved JSON that predates these fields.
 */
data class PlatformListing(
    val platformKey: String,
    val status: ListingStatus,
    val price: Double,
    val listingUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val condition: String? = null,
    val shipping: String? = null,
)

enum class ListingStatus {
    DRAFT,
    ACTIVE,
    SOLD,
    EXPIRED,
    UNLISTED,
}
