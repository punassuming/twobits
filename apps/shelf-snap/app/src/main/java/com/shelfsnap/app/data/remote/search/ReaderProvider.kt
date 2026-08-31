package com.shelfsnap.app.data.remote.search

/**
 * Which page-reader backend to use for opening a specific listing URL during price research.
 * Kept separate from [SearchProvider] — reading is a distinct concern from searching, and a
 * saved reader key enables reading independently of which search providers are enabled.
 */
enum class ReaderProvider(
    val key: String,
    val displayName: String,
) {
    JINA("jina", "Jina AI"),
    FIRECRAWL("firecrawl", "Firecrawl"),
    ;

    companion object {
        fun fromKey(key: String): ReaderProvider = entries.firstOrNull { it.key == key } ?: JINA
    }
}
