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
