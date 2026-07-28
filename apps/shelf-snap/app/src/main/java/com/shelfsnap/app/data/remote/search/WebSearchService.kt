package com.shelfsnap.app.data.remote.search

/** A single result returned by a [WebSearchService]. */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    /** Marketplace key when the upstream returned a structured listing. */
    val platformKey: String? = null,
    /** Parsed listing price supplied by the upstream, not inferred by the model. */
    val price: Double? = null,
    /** Completed-sale state supplied by the upstream; null when it is unknown. */
    val sold: Boolean? = null,
    /** Upstream recency/completion label when available. */
    val date: String = "",
)

/**
 * Maps a result URL to a supported marketplace [com.shelfsnap.app.data.model.Platform.key],
 * or null when the URL isn't a marketplace listing at all (blogs, retailer pages, forums).
 *
 * This is the shared "is this a real posting?" bar. Providers that return structured listing
 * data (SearchAPI) set [WebSearchResult.platformKey] themselves; the generic providers
 * (Serper/Jina/Brave) return bare title+url+snippet, so the evidence gatherer backfills the
 * key from the URL — otherwise their results could never become comparable listings no
 * matter how relevant they were.
 *
 * Returns real `Platform.key` values ("fbmarket", not "facebook") so
 * [com.shelfsnap.app.data.model.Platform.fromKey] resolves them; an unresolvable key causes
 * the comp to be silently discarded downstream.
 */
fun marketplaceKeyFromUrl(url: String): String? =
    when {
        url.contains("ebay.", ignoreCase = true) -> "ebay"
        url.contains("mercari.", ignoreCase = true) -> "mercari"
        url.contains("offerup.", ignoreCase = true) -> "offerup"
        url.contains("facebook.", ignoreCase = true) -> "fbmarket"
        url.contains("craigslist.", ignoreCase = true) -> "craigslist"
        else -> null
    }

/** Which web-search backend to use for market research. */
enum class SearchProvider(
    val key: String,
    val displayName: String,
) {
    /** No web search — pricing relies on the model's own knowledge only. */
    NONE("none", "None (AI only)"),
    BRAVE("brave", "Brave Search"),
    JINA("jina", "Jina AI Search"),
    SEARCHAPI("searchapi", "SearchAPI.io"),
    SERPER("serper", "Serper.dev"),
    ;

    /**
     * Whether this backend returns structured sold/price fields rather than bare web snippets.
     * Only SearchAPI maps eBay queries onto a completed-sales engine, so it is the only
     * provider that can yield a *verified* sold comp; the rest are plain-Google quality.
     */
    val suppliesStructuredListings: Boolean
        get() = this == SEARCHAPI

    /**
     * Whether this backend reliably honors `site:` operators, which is what makes the
     * marketplace-targeted core queries ("… site:ebay.com/itm") return real marketplace
     * postings instead of unrelated pages. SearchAPI and Serper both pass the query straight
     * through to a real Google engine; Jina's s.jina.ai silently drops `site:` and returns
     * eBay error pages, and Brave has no stated `site:` guarantee either. Either or both of
     * SearchAPI/Serper can be enabled as the primary marketplace searcher(s).
     */
    val honorsSiteFilter: Boolean
        get() = this == SEARCHAPI || this == SERPER

    companion object {
        fun fromKey(key: String): SearchProvider = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * Abstraction over a web-search backend used to gather comparable-listing evidence
 * for price research. Implementations are selected at runtime by the user's chosen
 * [SearchProvider]; both Brave and Jina require an API key.
 */
interface WebSearchService {
    /** The provider this implementation serves. */
    val provider: SearchProvider

    /**
     * Runs [query] and returns up to [limit] results.
     *
     * @param apiKey provider API key, or blank when the provider needs none.
     * @throws java.io.IOException on network/transport failure so callers can map
     *         it to a friendly message.
     */
    suspend fun search(
        query: String,
        apiKey: String,
        limit: Int = 8,
    ): List<WebSearchResult>
}
