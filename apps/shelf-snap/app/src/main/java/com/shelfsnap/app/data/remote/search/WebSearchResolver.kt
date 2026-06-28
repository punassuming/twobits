package com.shelfsnap.app.data.remote.search

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the active [WebSearchService] implementation for a chosen [SearchProvider].
 * Keeps the pricing service decoupled from concrete backends so new providers can be
 * added by registering another [WebSearchService].
 */
@Singleton
class WebSearchResolver
    @Inject
    constructor(
        private val brave: BraveSearchService,
        private val jina: JinaAiSearchService,
        private val searchapi: SearchApiService,
    ) {
        /** Returns the service for [provider], or null when web search is disabled. */
        fun resolve(provider: SearchProvider): WebSearchService? =
            when (provider) {
                SearchProvider.NONE -> null
                SearchProvider.BRAVE -> brave
                SearchProvider.JINA -> jina
                SearchProvider.SEARCHAPI -> searchapi
            }
    }
