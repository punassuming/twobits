package com.shelfsnap.app.data.remote.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads a single web page through Jina AI's Reader endpoint (r.jina.ai), which returns the page
 * rendered as clean, LLM-friendly text/markdown — stripped of nav, ads, and scripts.
 *
 * This is distinct from [JinaAiSearchService] (s.jina.ai, which *finds* result links). The Reader
 * *opens* a specific URL so the pricing model can read the actual listing (price, condition, sold
 * comps) instead of relying on a short search snippet. Brave has no equivalent, so page reading is
 * always Jina-keyed even when the URL was discovered via Brave.
 */
@Singleton
class JinaReaderService
    @Inject
    constructor() {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                // Hard ceiling on the whole call, above the eBay path's own 20s X-Timeout budget
                // plus network overhead — see JinaAiSearchService for why readTimeout alone
                // isn't enough (it only caps the gap between reads, not total duration).
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

        /**
         * Fetches [pageUrl] via the Reader and returns its cleaned text, truncated to
         * [maxChars]. Returns null on any failure so callers can fall back to the search snippet.
         *
         * @param apiKey Jina API key (required — the Reader free tier still needs a bearer token).
         */
        suspend fun read(
            pageUrl: String,
            apiKey: String,
            maxChars: Int = MAX_CHARS,
        ): String? =
            withContext(Dispatchers.IO) {
                if (apiKey.isBlank() || pageUrl.isBlank()) return@withContext null
                val requestBuilder =
                    Request
                        .Builder()
                        .url("https://r.jina.ai/$pageUrl")
                        .addHeader("Accept", "text/plain")
                        .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        // Ask the Reader to skip images and keep the response compact.
                        .addHeader("X-Return-Format", "text")
                        .addHeader("X-Retain-Images", "none")
                if (marketplaceKeyFromUrl(pageUrl) == "ebay") {
                    // eBay item pages consistently came back under MIN_CONFIRMED_LISTING_CHARS
                    // (~190-200 chars, near-identical length across unrelated item IDs) via the
                    // Reader's default "auto" engine — the signature of a bot-check/consent
                    // shell, not real content. Forcing headless-Chrome rendering and waiting for
                    // visible content (not just the initial response) gets past it; other
                    // marketplaces read fine on the faster default and don't need this.
                    requestBuilder
                        .addHeader("X-Engine", "browser")
                        .addHeader("X-Respond-Timing", "visible-content")
                        .addHeader("X-Timeout", "20")
                }
                val request = requestBuilder.get().build()

                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Jina Reader failed for $pageUrl: HTTP ${response.code}")
                            return@use null
                        }
                        response.body
                            ?.string()
                            ?.trim()
                            ?.take(maxChars)
                            ?.ifBlank { null }
                    }
                }.getOrElse {
                    Log.w(TAG, "Jina Reader threw for $pageUrl: ${it.javaClass.simpleName}")
                    null
                }
            }

        companion object {
            private const val TAG = "JinaReaderService"

            /** Cap per-page text so a handful of pages don't blow the model's context budget. */
            private const val MAX_CHARS = 2_000
        }
    }
