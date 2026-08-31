package com.shelfsnap.app.data.remote.search

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the active [PageReaderService] implementation for a chosen [ReaderProvider]. Only one
 * reader runs at a time — unlike search, which fans out to every enabled provider, reading the
 * same URL twice with two readers would double API cost for no accuracy benefit.
 */
@Singleton
class PageReaderResolver
    @Inject
    constructor(
        private val jina: JinaReaderService,
        private val firecrawl: FirecrawlReaderService,
    ) {
        fun resolve(provider: ReaderProvider): PageReaderService =
            when (provider) {
                ReaderProvider.JINA -> jina
                ReaderProvider.FIRECRAWL -> firecrawl
            }
    }
