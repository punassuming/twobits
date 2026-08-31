package com.shelfsnap.app.data.remote.search

/**
 * Abstraction over a "read this exact URL" backend used to open a specific listing page
 * for price research, as opposed to [WebSearchService] which only *finds* result links.
 * Implementations are selected at runtime by the user's chosen [ReaderProvider].
 */
interface PageReaderService {
    /**
     * Fetches [pageUrl] and returns its cleaned text, truncated to [maxChars].
     * Returns null on any failure so callers can fall back to the search snippet.
     */
    suspend fun read(
        pageUrl: String,
        apiKey: String,
        maxChars: Int = 2_000,
    ): String?
}
