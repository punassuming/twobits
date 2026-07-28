package com.shelfsnap.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shelfsnap.app.data.model.Citation
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.MarketComp
import com.shelfsnap.app.data.model.MarketQuery
import com.shelfsnap.app.data.model.MarketResearch
import com.shelfsnap.app.data.model.MarketResearchDebug
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.remote.search.JinaReaderService
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.shelfsnap.app.data.remote.search.WebSearchResolver
import com.shelfsnap.app.data.remote.search.WebSearchResult
import com.shelfsnap.app.data.remote.search.WebSearchService
import com.shelfsnap.app.data.remote.search.marketplaceKeyFromUrl
import com.shelfsnap.app.util.ApiKeyValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a price-research run; [error] is non-null on failure. */
data class PriceResearchResult(
    val research: MarketResearch = MarketResearch(),
    /** Suggested overall asking price (USD), or null if not produced. */
    val suggestedValue: Double? = null,
    val error: String? = null,
    /** True when live web-search evidence was available; false means AI training data only. */
    val hasWebEvidence: Boolean = false,
)

/** What the web-search step produced: results plus the provider and any failure. */
data class SearchEvidence(
    val results: List<WebSearchResult> = emptyList(),
    /** [SearchProvider.key] used, or "" when search was disabled. */
    val providerKey: String = "",
    /** Non-null when the search call itself failed. */
    val error: String? = null,
    /** Per-query provenance log for the Market tab's Debug panel. */
    val queries: List<MarketQuery> = emptyList(),
    /** How many result pages were opened via the Jina Reader for richer evidence. */
    val pagesRead: Int = 0,
    /**
     * Total Jina Reader calls actually made, including ones that didn't pass
     * [looksLikeConfirmedListing] and so aren't counted in [pagesRead]. This is the real
     * page-read API call count; [pagesRead] is an evidence-quality count, not a call count.
     */
    val readAttempts: Int = 0,
    /** Web-search phase duration (millis). */
    val searchMs: Long = 0L,
    /** Page-reading phase duration (millis). */
    val readMs: Long = 0L,
    /**
     * True when at least one provider used here can return completed-sale data. False means
     * verified sold comps were impossible from the start, regardless of how many calls ran.
     */
    val soldCapable: Boolean = false,
)

private data class SearchAttempt(
    val provider: SearchProvider,
    val query: String,
    val results: List<WebSearchResult> = emptyList(),
    val error: String? = null,
)

/**
 * Fills in [WebSearchResult.platformKey] from the URL when the provider didn't supply one.
 *
 * Serper/Jina/Brave return bare title+url+snippet, so without this every result from them
 * looks like a non-marketplace page — they could never satisfy a per-provider posting quota
 * nor contribute a comparable listing. Structured providers already set the key, so theirs
 * is left alone.
 */
private fun WebSearchResult.withBackfilledPlatform(): WebSearchResult =
    if (platformKey != null) this else copy(platformKey = marketplaceKeyFromUrl(url))

/**
 * Produces resale price guidance for an item.
 *
 * Pipeline:
 *  1. (optional) gather comparable-listing evidence via the user's [WebSearchService]
 *     (Brave / DuckDuckGo), gated by a search API key.
 *  2. ask the OpenAI Chat Completions API to synthesize the evidence (plus the item's
 *     own attributes) into per-platform suggested prices, comparable comps, and cited
 *     sources as structured JSON.
 *
 * Web search is best-effort: if it's disabled or fails, the model still produces an
 * estimate from its own knowledge, and the result is labelled accordingly.
 */
@Singleton
class PriceResearchService
    @Inject
    constructor(
        private val searchResolver: WebSearchResolver,
        private val jinaReader: JinaReaderService,
    ) {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

        private val gson = Gson()
        private val json = "application/json; charset=utf-8".toMediaType()

        /**
         * Researches [item]'s resale value.
         *
         * @param openAiKey OpenAI API key (required for BYOK; pass the RevenueCat user ID for Pro).
         * @param searchProviders list of (provider, key) pairs used to gather evidence; empty disables web search.
         * @param readerKey Jina key used to *open* the top result pages (r.jina.ai) for richer
         *   evidence; null/blank skips page reading. Jina-only — Brave has no reader.
         * @param model OpenAI model to use for price synthesis; defaults to [MODEL].
         * @param openAiBaseUrl Override to route LLM calls through the TwoBits Worker proxy.
         * @param openAiAuthHeader Override auth header (defaults to "Bearer $openAiKey").
         * @param workerSearchUrl When non-null, web evidence is gathered via the Worker's
         *   managed search endpoint instead of calling Jina/Brave directly.
         * @param workerAuthHeader Auth header for [workerSearchUrl] calls.
         * @param onProgress Fired as search queries, page reads, and synthesis happen, so the
         *   UI can show live status instead of an opaque spinner. Purely informational — a slow
         *   or absent collector never affects the research itself. May be invoked from multiple
         *   concurrent coroutines (queries and marketplace reads run in parallel); each call
         *   carries a full snapshot so the collector can just replace its state.
         */
        suspend fun research(
            item: Item,
            openAiKey: String,
            searchProviders: List<Pair<SearchProvider, String>> = emptyList(),
            readerKey: String? = null,
            model: String = MODEL,
            openAiBaseUrl: String = "https://api.openai.com",
            openAiAuthHeader: String = "Bearer $openAiKey",
            workerSearchUrl: String? = null,
            workerAuthHeader: String? = null,
            onProgress: (ResearchProgress) -> Unit = {},
        ): PriceResearchResult =
            withContext(Dispatchers.IO) {
                val totalStart = System.currentTimeMillis()
                // Skip key validation when routing through the Worker proxy.
                if (openAiBaseUrl == "https://api.openai.com" && !ApiKeyValidator.isValid(openAiKey)) {
                    return@withContext PriceResearchResult(error = ERROR_INVALID_KEY)
                }

                // Step 1 — best-effort web evidence (search, then optionally read pages).
                val evidence =
                    if (workerSearchUrl != null && workerAuthHeader != null) {
                        gatherWorkerEvidence(item, workerSearchUrl, workerAuthHeader, onProgress)
                    } else {
                        gatherEvidence(item, searchProviders, readerKey, onProgress)
                    }

                onProgress(
                    ResearchProgress(
                        phase = ResearchProgress.Phase.SYNTHESIZING,
                        detail = "Analyzing with AI…",
                        queriesRun = evidence.queries.size,
                        resultsFound = evidence.results.count { it.platformKey != null },
                        pagesConfirmed = evidence.pagesRead,
                    ),
                )

                // Step 2 — synthesize via the model.
                val synthesisStart = System.currentTimeMillis()
                val synthesisPromptSnippet = buildSystemPrompt(item).take(800)
                val result =
                    runCatching {
                        val requestBody = buildRequest(item, evidence, model)
                        val endpoint = if (isResponsesModel(model)) "v1/responses" else "v1/chat/completions"
                        val request =
                            Request
                                .Builder()
                                .url("$openAiBaseUrl/$endpoint")
                                .addHeader("Authorization", openAiAuthHeader)
                                .addHeader("X-TwoBits-App", "shelfsnap")
                                .addHeader("X-TwoBits-Op", "price-research")
                                .addHeader("Content-Type", "application/json")
                                .post(gson.toJson(requestBody).toRequestBody(json))
                                .build()

                        client.newCall(request).execute().use { response ->
                            val body = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                Log.w(TAG, "Pricing request failed: HTTP ${response.code}")
                                return@use PriceResearchResult(error = friendlyHttpError(response.code))
                            }
                            parseResponse(body, evidence, isResponsesModel(model))
                        }
                    }.getOrElse { e ->
                        Log.w(TAG, "Pricing request threw ${e.javaClass.simpleName}")
                        PriceResearchResult(error = friendlyNetworkError(e))
                    }
                val now = System.currentTimeMillis()

                // Attach transparency detail to a successful run.
                if (result.error == null) {
                    // Every outbound call this run actually made: one per search query, one per
                    // Jina Reader page (attempted, not just confirmed — a rejected read still
                    // cost a call), and the synthesis call itself.
                    val totalApiCalls = evidence.queries.size + evidence.readAttempts + 1
                    val servicesUsed =
                        (
                            evidence.queries.map { it.label } +
                                listOfNotNull(if (evidence.readAttempts > 0) "Jina Reader" else null) +
                                listOf("OpenAI")
                        ).distinct()
                    Log.i(
                        TAG,
                        "Research complete: $totalApiCalls API calls (${servicesUsed.joinToString()}) in ${now - totalStart}ms",
                    )
                    val debug =
                        MarketResearchDebug(
                            queries = evidence.queries,
                            pagesRead = evidence.pagesRead,
                            totalApiCalls = totalApiCalls,
                            servicesUsed = servicesUsed,
                            searchMs = evidence.searchMs,
                            readMs = evidence.readMs,
                            synthesisMs = now - synthesisStart,
                            totalMs = now - totalStart,
                            synthesisPrompt = synthesisPromptSnippet,
                        )
                    result.copy(research = result.research.copy(debug = debug))
                } else {
                    result
                }
            }

        private suspend fun gatherEvidence(
            item: Item,
            providers: List<Pair<SearchProvider, String>>,
            readerKey: String?,
            onProgress: (ResearchProgress) -> Unit,
        ): SearchEvidence {
            val services =
                providers.mapNotNull { (provider, key) ->
                    searchResolver.resolve(provider)?.let { it to key }
                }
            if (services.isEmpty()) return SearchEvidence()

            val hasStructuredProvider = services.any { it.first.provider.suppliesStructuredListings }
            val queryPlan = buildSearchQueries(item)
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<WebSearchResult>()
            val queryLog = mutableListOf<MarketQuery>()
            var lastError: String? = null
            val primaryProviderKey =
                services
                    .first()
                    .first.provider.key

            // Phase 1 — search. SearchAPI and Serper both honor site: reliably (SearchAPI
            // additionally maps eBay onto a real completed-sales engine), so whichever of them
            // is enabled — either or both — runs the marketplace-targeted core queries (eBay,
            // Mercari, OfferUp, Craigslist, Facebook Marketplace). Jina would just re-run the
            // identical core query for a worse result (it silently drops site: and returns eBay
            // error pages), so it skips core when a site:-honoring provider is available and
            // spends its calls on the
            // broadening queries instead — genuinely different evidence rather than a redundant
            // search. Without SearchAPI or Serper enabled, every non-Brave provider runs core
            // itself, since it's the only evidence source for that marketplace at all.
            //
            // Whichever providers run core, all of them keep going through the broadening
            // queries only until they've found enough real marketplace postings. The original
            // implementation fired the full (query × provider) cartesian product concurrently:
            // with 5 queries and 3 providers that is 15 billed calls, every provider answering
            // the *same* query, merged set capped at MAX_SEARCH_RESULTS anyway — most of what
            // was paid for was discarded. An earlier attempt at fixing this applied the same
            // early-stop quota to every query including the marketplace-targeted ones — but
            // "ebay." matches broadly, so a provider often satisfied the whole quota off the
            // *first* query and never even tried Mercari or OfferUp, leaving every result from
            // one marketplace; core queries are now unconditional for whichever provider(s)
            // are responsible for them. Providers are queried in parallel with each other
            // (independent vendors, so wall-clock still collapses to the slowest single
            // provider) but each provider's own queries run in sequence so its quota can
            // short-circuit the broadening tail.
            //
            // Brave is the exception, but only when a site:-honoring provider is actually
            // available: it has no site: advantage and no dedicated engine, so it isn't worth
            // its cost/latency once SearchAPI/Serper already found enough. In that case it runs
            // as a genuine fallback — held back until the rest finish, then fired (generic,
            // unfiltered queries only) only if they came up thin. Without a site:-honoring
            // provider at all, Brave is a primary source same as everyone else (no "better
            // option" to defer to), so it joins the immediate group and runs core too.
            val hasSiteFilterProvider = services.any { it.first.provider.honorsSiteFilter }
            val (braveServices, otherServices) = services.partition { it.first.provider == SearchProvider.BRAVE }
            val immediateServices = if (hasSiteFilterProvider) otherServices else services
            val deferredBraveServices = if (hasSiteFilterProvider) braveServices else emptyList()
            // Queries run concurrently (within a provider's core queries, and across providers),
            // so these need to be safe for concurrent increment — plain vars would race.
            val queriesRun = AtomicInteger(0)
            val resultsFound = AtomicInteger(0)
            val onQueryDone: (SearchProvider, String, Int) -> Unit = { provider, query, resultCount ->
                queriesRun.incrementAndGet()
                resultsFound.addAndGet(resultCount)
                onProgress(
                    ResearchProgress(
                        phase = ResearchProgress.Phase.SEARCHING,
                        detail = "${provider.displayName}: $query",
                        queriesRun = queriesRun.get(),
                        resultsFound = resultsFound.get(),
                    ),
                )
            }
            val searchStart = System.currentTimeMillis()
            val immediateAttempts: List<List<SearchAttempt>> =
                coroutineScope {
                    immediateServices
                        .map { (service, key) ->
                            val runsCore = service.provider.honorsSiteFilter || !hasSiteFilterProvider
                            async {
                                runProviderQueries(
                                    service = service,
                                    key = key,
                                    core = if (runsCore) queryPlan.core else emptyList(),
                                    broadening = queryPlan.broadening,
                                    onQueryDone = onQueryDone,
                                )
                            }
                        }.map { it.await() }
                }
            // Distinct URLs, not raw occurrences: the same listing can come back from more than
            // one provider or query (SearchAPI and Serper both surfacing the same eBay item,
            // say), and counting each repeat toward the threshold could suppress Brave even
            // though the actual amount of distinct evidence is still thin.
            val immediateLegitPostings =
                immediateAttempts
                    .flatten()
                    .flatMap { it.results }
                    .filter { it.platformKey != null }
                    .map { it.url }
                    .distinct()
                    .size
            val braveAttempts: List<List<SearchAttempt>> =
                if (deferredBraveServices.isNotEmpty() && immediateLegitPostings < BRAVE_FALLBACK_THRESHOLD) {
                    coroutineScope {
                        deferredBraveServices
                            .map { (service, key) ->
                                async {
                                    runProviderQueries(
                                        service = service,
                                        key = key,
                                        core = emptyList(),
                                        broadening = queryPlan.broadening,
                                        onQueryDone = onQueryDone,
                                    )
                                }
                            }.map { it.await() }
                    }
                } else {
                    emptyList()
                }
            val perProviderAttempts: List<List<SearchAttempt>> = immediateAttempts + braveAttempts
            val allAttempts = perProviderAttempts.flatten()
            for (attempt in allAttempts) {
                queryLog.add(
                    MarketQuery(
                        label = attempt.provider.displayName,
                        query = attempt.query,
                        resultCount = attempt.results.size,
                        error = attempt.error,
                    ),
                )
            }
            // Merge across providers preferring real marketplace postings, so the capped
            // evidence set isn't crowded out by blog posts and unrelated shop pages that
            // happened to be returned first. Real postings are interleaved round-robin across
            // marketplaces rather than concatenated in provider/query order: a single provider
            // can return a full page of eBay results before its Mercari/OfferUp queries even
            // run, and concatenating would let that fill the entire MAX_SEARCH_RESULTS cap
            // before another marketplace's results are ever considered — meaning that
            // marketplace's query was billed but its evidence never reached the model at all.
            // Round-robin guarantees every marketplace with at least one result keeps a slot.
            val allResults = allAttempts.flatMap { it.results }
            val (withPlatform, withoutPlatform) = allResults.partition { it.platformKey != null }
            val byMarketplace = withPlatform.groupBy { it.platformKey }.values.map { it.iterator() }
            val ranked = mutableListOf<WebSearchResult>()
            while (byMarketplace.any { it.hasNext() }) {
                for (it in byMarketplace) {
                    if (it.hasNext()) ranked.add(it.next())
                }
            }
            ranked.addAll(withoutPlatform)
            for (r in ranked) {
                if (merged.size >= MAX_SEARCH_RESULTS) break
                if (seen.add(r.url)) merged.add(r)
            }
            if (merged.isEmpty()) {
                lastError =
                    allAttempts.firstNotNullOfOrNull { it.error }
                        ?: queryLog.firstOrNull()?.let { "No results from ${it.label}" }
            }
            val searchMs = System.currentTimeMillis() - searchStart

            // Phase 2 — open result pages via Jina Reader so the model reads the actual listing
            // (price, condition, sold status), not just a search snippet. Reads target
            // READS_PER_MARKETPLACE *confirmed* matches per distinct marketplace present in the
            // results, not just the first N candidates: the Reader returns page text on any
            // HTTP 200, including "listing removed"/bot-block pages, so a read that doesn't look
            // like a real listing (see [looksLikeConfirmedListing]) doesn't consume a slot —
            // the next-ranked candidate for that marketplace is tried instead, until the
            // marketplace's confirmed quota is hit or its candidates run out. Marketplaces are
            // worked concurrently with each other (independent URLs, so wall-clock collapses to
            // the slowest single marketplace) but each marketplace's own candidates are tried in
            // sequence so a bad read can be followed by the next one.
            var pagesRead = 0
            var readMs = 0L
            var readAttempts = 0
            val candidatesByMarketplace: Map<String?, List<Int>> =
                merged.indices
                    .filter { merged[it].platformKey != null }
                    .groupBy { merged[it].platformKey }
            if (!readerKey.isNullOrBlank() && candidatesByMarketplace.isNotEmpty()) {
                val readStart = System.currentTimeMillis()
                val pagesTarget = candidatesByMarketplace.size * READS_PER_MARKETPLACE
                val pagesConfirmed = AtomicInteger(0)
                val pagesAttempted = AtomicInteger(0)
                val confirmedReads: List<Pair<Int, String>> =
                    coroutineScope {
                        candidatesByMarketplace.values
                            .map { candidates ->
                                async {
                                    val confirmed = mutableListOf<Pair<Int, String>>()
                                    for (i in candidates) {
                                        if (confirmed.size >= READS_PER_MARKETPLACE) break
                                        val marketplaceName = Platform.fromKey(merged[i].platformKey ?: "")?.displayName ?: "listing"
                                        val text = jinaReader.read(merged[i].url, readerKey) ?: ""
                                        pagesAttempted.incrementAndGet()
                                        val isMatch = looksLikeConfirmedListing(text)
                                        if (isMatch) {
                                            confirmed.add(i to text)
                                            pagesConfirmed.incrementAndGet()
                                        }
                                        onProgress(
                                            ResearchProgress(
                                                phase = ResearchProgress.Phase.VERIFYING,
                                                detail = if (isMatch) "$marketplaceName verified" else "$marketplaceName — trying next listing",
                                                queriesRun = queriesRun.get(),
                                                resultsFound = resultsFound.get(),
                                                pagesConfirmed = pagesConfirmed.get(),
                                                pagesTarget = pagesTarget,
                                            ),
                                        )
                                    }
                                    confirmed
                                }
                            }.map { it.await() }
                    }.flatten()
                for ((i, text) in confirmedReads) {
                    merged[i] = merged[i].copy(snippet = (merged[i].snippet + "\n" + text).take(MAX_SNIPPET_CHARS))
                    pagesRead++
                }
                readAttempts = pagesAttempted.get()
                readMs = System.currentTimeMillis() - readStart
            }

            return SearchEvidence(
                results = merged,
                providerKey = primaryProviderKey,
                error = if (merged.isEmpty()) lastError else null,
                queries = queryLog,
                pagesRead = pagesRead,
                readAttempts = readAttempts,
                searchMs = searchMs,
                readMs = readMs,
                soldCapable = hasStructuredProvider,
            )
        }

        /**
         * Runs [core] (always, unconditionally, concurrently) then [broadening] (sequentially,
         * until [LEGIT_POSTINGS_PER_PROVIDER] real marketplace postings are found) against a
         * single provider. Core queries target different marketplaces and none of them is
         * gated on another's outcome, so waiting for eBay to finish before even starting Mercari
         * only added latency — a provider with 5 core queries at, say, 8s each spent 40s in
         * this phase for no reason. Broadening stays sequential: each one's necessity depends on
         * the running legitPostings total, which only exists once the previous query is done.
         */
        private suspend fun runProviderQueries(
            service: WebSearchService,
            key: String,
            core: List<String>,
            broadening: List<String>,
            onQueryDone: (SearchProvider, String, Int) -> Unit = { _, _, _ -> },
        ): List<SearchAttempt> {
            suspend fun runOne(query: String): SearchAttempt {
                val attempt =
                    runCatching { service.search(query, key) }
                        .fold(
                            onSuccess = { results ->
                                SearchAttempt(
                                    provider = service.provider,
                                    query = query,
                                    results = results.map { it.withBackfilledPlatform() },
                                )
                            },
                            onFailure = { e ->
                                Log.w(TAG, "Web search failed for query '$query': ${e.javaClass.simpleName}: ${e.message}")
                                SearchAttempt(
                                    provider = service.provider,
                                    query = query,
                                    error = e.message ?: e.javaClass.simpleName,
                                )
                            },
                        )
                onQueryDone(service.provider, query, attempt.results.count { it.platformKey != null })
                return attempt
            }

            val coreAttempts =
                coroutineScope {
                    core.map { async { runOne(it) } }.map { it.await() }
                }
            val attempts = coreAttempts.toMutableList()
            var legitPostings = coreAttempts.sumOf { attempt -> attempt.results.count { it.platformKey != null } }
            for (query in broadening) {
                if (legitPostings >= LEGIT_POSTINGS_PER_PROVIDER) break
                val attempt = runOne(query)
                attempts.add(attempt)
                legitPostings += attempt.results.count { it.platformKey != null }
            }
            return attempts
        }

        /**
         * Best-effort check that a Jina Reader response is real listing content rather than a
         * dead link — the Reader returns HTTP 200 (and therefore non-null text) for expired
         * pages and most bot-block/CAPTCHA interstitials too, since those are still valid page
         * loads from its point of view. There's no structured signal to check instead short of
         * a second LLM call per page, so this is a heuristic, not a guarantee: a short response
         * is treated as a failed read, and a small set of phrases disqualifies an otherwise
         * long-enough response. The denylist deliberately excludes "no longer available" /
         * "item is unavailable" wording: those phrases are just as common on a genuinely ended
         * or sold listing that still shows full price/condition detail — exactly the evidence
         * this pipeline wants most — as on an actually-removed one, so treating them as
         * disqualifying would throw away good evidence more often than it catches bad pages.
         */
        private fun looksLikeConfirmedListing(text: String): Boolean {
            if (text.trim().length < MIN_CONFIRMED_LISTING_CHARS) return false
            val lower = text.lowercase()
            return DEAD_PAGE_PHRASES.none { lower.contains(it) }
        }

        /**
         * Variant of [gatherEvidence] that calls the Worker's managed Jina search endpoint
         * instead of the user's own Jina/Brave key.
         */
        private suspend fun gatherWorkerEvidence(
            item: Item,
            workerUrl: String,
            authHeader: String,
            onProgress: (ResearchProgress) -> Unit,
        ): SearchEvidence {
            // The Worker routes every query through SearchAPI (see the "searchapi" provider in
            // the request body below), which does honor completed-sales targeting. This path
            // already had a sensible early-stop (MIN_RESULTS_EARLY_STOP below) before the
            // BYOK path's per-provider quota existed, so the core/broadening split isn't
            // needed here — run the plan's queries in their original flat order.
            val queries = buildSearchQueries(item).all
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<WebSearchResult>()
            val queryLog = mutableListOf<MarketQuery>()
            var lastError: String? = null

            val searchStart = System.currentTimeMillis()
            var queriesRun = 0
            var resultsFound = 0
            for (query in queries) {
                if (merged.size >= MAX_SEARCH_RESULTS) break
                runCatching {
                    // "searchapi" honors site: operators (Jina silently ignores them), so the
                    // platform-targeted queries below actually return marketplace listings.
                    val bodyJson = """{"query":${gson.toJson(query)},"provider":"searchapi","limit":8}"""
                    val req =
                        Request
                            .Builder()
                            .url(workerUrl)
                            .addHeader("Authorization", authHeader)
                            .addHeader("X-TwoBits-App", "shelfsnap")
                            .addHeader("Content-Type", "application/json")
                            .post(bodyJson.toRequestBody(json))
                            .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("Worker search ${resp.code}")
                        val root = JsonParser.parseString(resp.body?.string() ?: "{}").asJsonObject
                        root.getAsJsonArray("results")?.map { el ->
                            val r = el.asJsonObject
                            WebSearchResult(
                                title = r.get("title")?.asString ?: "",
                                url = r.get("url")?.asString ?: "",
                                snippet = r.get("description")?.asString ?: "",
                                platformKey = r.get("platform")?.asString,
                                price = r.get("price")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() },
                                sold = r.get("sold")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() },
                                date = r.get("date")?.asString ?: "",
                            )
                        } ?: emptyList()
                    }
                }.fold(
                    onSuccess = { results ->
                        queryLog.add(MarketQuery(label = "Managed search", query = query, resultCount = results.size))
                        results.forEach { r ->
                            if (seen.add(r.url) && merged.size < MAX_SEARCH_RESULTS) merged.add(r)
                        }
                        resultsFound += results.count { it.platformKey != null }
                    },
                    onFailure = {
                        Log.w(TAG, "Worker search failed for query '$query': ${it.message}")
                        lastError = it.message ?: it.javaClass.simpleName
                        queryLog.add(
                            MarketQuery(
                                label = "Managed search",
                                query = query,
                                resultCount = 0,
                                error = lastError,
                            ),
                        )
                    },
                )
                queriesRun++
                onProgress(
                    ResearchProgress(
                        phase = ResearchProgress.Phase.SEARCHING,
                        detail = "Managed search: $query",
                        queriesRun = queriesRun,
                        resultsFound = resultsFound,
                    ),
                )
                if (merged.size >= MIN_RESULTS_EARLY_STOP) break
            }
            val searchMs = System.currentTimeMillis() - searchStart

            return SearchEvidence(
                results = merged,
                providerKey = "searchapi",
                error = if (merged.isEmpty()) lastError else null,
                queries = queryLog,
                searchMs = searchMs,
                soldCapable = true,
            )
        }

        /**
         * [core] targets a specific marketplace (one query each for eBay, Mercari, OfferUp,
         * Craigslist, and Facebook Marketplace) and must always run — it's the only source of
         * evidence for that marketplace at all. [broadening] widens the search (tag-augmented,
         * generic fallback) and exists purely to fill gaps; it's safe to skip once a provider
         * already has enough evidence, unlike [core] where skipping a query means skipping that
         * marketplace entirely.
         */
        private data class SearchQueryPlan(
            val core: List<String>,
            val broadening: List<String>,
        ) {
            val all: List<String> get() = core + broadening
        }

        /**
         * Every marketplace gets both a "sold" and a "for sale" core query — a sold comp is the
         * stronger price signal, but an asking-price-only marketplace (or one where the sold
         * query just came up empty) still contributes useful range context. Filtering for sold
         * alone was undercounting evidence: SearchAPI's ebay_search engine takes a hard
         * sold-only filter (see [com.shelfsnap.app.data.remote.search.buildSearchApiUrl]), which
         * excluded every active eBay listing outright, and the "sold" keyword added to the other
         * marketplaces' queries — while not a hard filter there, since plain Google can't filter
         * to completed sales — still nudged every query toward the same narrower slice.
         */
        private fun buildSearchQueries(item: Item): SearchQueryPlan {
            // Prefer brand+model; fall back to a user-set title when brand/model are both blank
            // so a custom title still yields a precise, quotable descriptor instead of dropping
            // straight to the weaker generic description+category path below.
            val base =
                listOf(item.brand, item.model)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { item.title }
            val hasQuotableDescriptor = base.isNotBlank()
            val quoted = if (base.isNotBlank()) "\"$base\"" else ""
            val conditionLabel = item.condition.searchLabel()

            // Build the best descriptor available for platform-targeted queries.
            // Use a truncated description so site: queries stay focused; the full AI-prose
            // description produces overly long queries that return 0 results on eBay/Mercari.
            val genericDescriptor =
                listOf(item.description.take(50).trim(), item.category)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            val descriptor =
                when {
                    hasQuotableDescriptor -> "$quoted $conditionLabel ${item.category}"
                    else -> "$genericDescriptor $conditionLabel"
                }.trim()

            // Two queries per marketplace — sold and for-sale — so generic items (no
            // brand/model) still get real listing evidence rather than bare text searches.
            //
            // Craigslist and Facebook Marketplace are real limitations, not just more of the
            // same: Craigslist listings are deleted (not archived as "sold") once a sale closes,
            // so the sold variant finds little to nothing there even when it works elsewhere.
            // Facebook Marketplace item pages sit almost entirely behind a login wall, so
            // Google — and therefore every provider here — indexes very few of them at all.
            // Both are included because they're the only shot at any evidence from those
            // marketplaces, not because the hit rate is expected to match eBay/Mercari/OfferUp.
            val marketplaceDomains =
                listOf(
                    "site:ebay.com/itm",
                    "mercari.com",
                    "offerup.com",
                    "craigslist.org",
                    "facebook.com/marketplace",
                )
            val core =
                marketplaceDomains.flatMap { domain ->
                    listOf(
                        "$descriptor $domain sold".trim(),
                        "$descriptor $domain for sale".trim(),
                    )
                }

            val broadening = mutableListOf<String>()
            // Tag-augmented for broader evidence.
            if (item.tags.isNotEmpty()) {
                val tagHint = item.tags.take(3).joinToString(" ")
                broadening.add("$quoted $tagHint ${item.category} price".trim())
            }
            // General fallback. Keep the descriptor quoted when we have one — an unquoted
            // brand/model let search engines drop the model number entirely and return
            // loosely-related listings for the same category (toner cartridges for a NUC),
            // which then crowded out the real postings in the capped evidence set.
            broadening.add(
                if (hasQuotableDescriptor) {
                    "$quoted ${item.category} resale price used".trim()
                } else {
                    listOf(item.description.take(60).trim(), item.category, "resale price used")
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" ")
                },
            )
            return SearchQueryPlan(core = core, broadening = broadening)
        }

        private fun com.shelfsnap.app.data.model.Condition.searchLabel(): String =
            when (this) {
                com.shelfsnap.app.data.model.Condition.EXCELLENT -> "like new"
                com.shelfsnap.app.data.model.Condition.GOOD -> "good condition"
                com.shelfsnap.app.data.model.Condition.FAIR -> "used"
                com.shelfsnap.app.data.model.Condition.POOR -> "parts or repair"
            }

        private fun buildSystemPrompt(item: Item): String {
            val platformKeys = Platform.entries.joinToString(", ") { it.key }
            return """
                You are a reselling price-research assistant. Using the item details and any
                web-search evidence provided, estimate fair resale prices for second-hand
                marketplaces and cite your sources. Respond ONLY with valid JSON in this schema:
                {
                  "suggestedValue": <number, overall asking price in USD>,
                  "averageSoldPrice": <number>,
                  "lowPrice": <number>,
                  "highPrice": <number>,
                  "confidencePercent": <integer 0-100>,
                  "suggestedPrices": { "<platformKey>": <number>, ... },
                  "comps": [
                    { "platform": "<platformKey>", "title": "<listing title>",
                      "price": <number>, "sold": <true|false>, "date": "<recency>",
                      "url": "<copy the exact url from one of the search results above that is this listing — leave blank only if no exact match exists>" }
                  ],
                  "citations": [ { "label": "<source>", "url": "<url>" } ]
                }
                Valid platformKey values: $platformKeys.
                Prefer sold listings over active ones. If evidence is thin, lower the
                confidence and say so via fewer comps.
                URL RULE: Only include a comp if its url matches exactly one of the provided
                search result urls above. Do not synthesize or guess URLs. Leave url blank
                if no exact match from the evidence.
                IMPORTANT: Only use snippets that contain an actual price (e.g. '${'$'}XX.XX') and
                indicate a completed/sold transaction. Ignore blog posts, buying guides, and
                general articles. If fewer than 3 snippets contain real prices from actual
                marketplace listings, set confidencePercent ≤ 30.
                """.trimIndent()
        }

        private fun buildRequest(
            item: Item,
            evidence: SearchEvidence,
            model: String = MODEL,
        ): JsonObject {
            val systemPrompt = buildSystemPrompt(item)

            val userPayload =
                JsonObject().apply {
                    addProperty("category", item.category)
                    addProperty("brand", item.brand)
                    addProperty("model", item.model)
                    addProperty("condition", item.condition.name)
                    addProperty("size", item.size)
                    addProperty("color", item.color)
                    addProperty("quantity", item.quantity)
                    addProperty("originalPrice", item.originalPrice)
                    addProperty("description", item.description)
                    add("tags", JsonArray().apply { item.tags.forEach { add(it) } })
                    add(
                        "searchEvidence",
                        JsonArray().apply {
                            evidence.results.forEach { r ->
                                add(
                                    JsonObject().apply {
                                        addProperty("title", r.title)
                                        addProperty("url", r.url)
                                        addProperty("snippet", r.snippet)
                                        r.platformKey?.let { addProperty("platform", it) }
                                        r.price?.let { addProperty("price", it) }
                                        r.sold?.let { addProperty("sold", it) }
                                        if (r.date.isNotBlank()) addProperty("date", r.date)
                                    },
                                )
                            }
                        },
                    )
                }

            val messages =
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemPrompt)
                        },
                    )
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", gson.toJson(userPayload))
                        },
                    )
                }

            return JsonObject().apply {
                addProperty("model", model)
                if (isResponsesModel(model)) {
                    add("input", messages)
                    // Reasoning tokens count against max_output_tokens; leave headroom so the
                    // model can still emit the full JSON payload after thinking.
                    addProperty("max_output_tokens", 1500)
                    add("reasoning", JsonObject().apply { addProperty("effort", "low") })
                    // gpt-5 reasoning models reject temperature values other than the default.
                } else {
                    add("messages", messages)
                    addProperty("max_tokens", 900)
                    addProperty("temperature", 0.2)
                }
            }
        }

        private fun isResponsesModel(model: String): Boolean = model.startsWith("gpt-5")

        private fun parseResponse(
            responseJson: String,
            evidence: SearchEvidence,
            isResponsesApi: Boolean = false,
        ): PriceResearchResult =
            runCatching {
                val root = JsonParser.parseString(responseJson).asJsonObject
                val content =
                    if (isResponsesApi) {
                        extractResponsesText(root)
                    } else {
                        root
                            .getAsJsonArray("choices")
                            .get(0)
                            .asJsonObject
                            .getAsJsonObject("message")
                            .get("content")
                            .asString
                    }.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                val obj = JsonParser.parseString(content).asJsonObject

                val modelComps =
                    obj.getAsJsonArray("comps")?.mapNotNull { el ->
                        val c = el.asJsonObject
                        val platformKey = c.get("platform")?.asString ?: return@mapNotNull null
                        if (Platform.fromKey(platformKey) == null) return@mapNotNull null
                        MarketComp(
                            platformKey = platformKey,
                            title = c.get("title")?.asString ?: "",
                            price = c.get("price")?.asDouble ?: 0.0,
                            sold = c.get("sold")?.asBoolean ?: false,
                            date = c.get("date")?.asString ?: "",
                            sourceUrl = c.get("url")?.asString ?: "",
                        )
                    } ?: emptyList()
                val comps = mergeVerifiedComparableListings(modelComps, evidence)
                val comparableStats = comparableStats(comps)

                val suggestedPrices =
                    obj
                        .getAsJsonObject("suggestedPrices")
                        ?.entrySet()
                        ?.filter { Platform.fromKey(it.key) != null }
                        ?.associate { it.key to it.value.asDouble }
                        ?: emptyMap()

                // Citations must be sources that actually back the estimate: the listings behind
                // the comps, plus whatever the model deliberately cited. Previously *every* raw
                // search result was appended, so unrelated pages the model had already rejected
                // (a toner cartridge under an Intel NUC) still rendered under "Sources".
                val modelCitations =
                    obj.getAsJsonArray("citations")?.mapNotNull { el ->
                        val c = el.asJsonObject
                        val label = c.get("label")?.asString ?: return@mapNotNull null
                        Citation(label = label, url = c.get("url")?.asString ?: "")
                    } ?: emptyList()
                val compUrls = comps.map { it.sourceUrl }.filter { it.isNotBlank() }.toSet()
                val compCitations =
                    evidence.results
                        .filter { it.url in compUrls }
                        .map { Citation(label = it.title, url = it.url) }
                val citations =
                    (compCitations + modelCitations.filter { it.url.isBlank() || it.url in compUrls })
                        .distinctBy { it.url.ifBlank { it.label } }
                        .take(MAX_CITATIONS)

                val research =
                    MarketResearch(
                        comps = comps,
                        suggestedPrices = suggestedPrices,
                        averageSoldPrice = comparableStats?.average ?: obj.get("averageSoldPrice")?.asDouble ?: 0.0,
                        lowPrice = comparableStats?.low ?: obj.get("lowPrice")?.asDouble ?: 0.0,
                        highPrice = comparableStats?.high ?: obj.get("highPrice")?.asDouble ?: 0.0,
                        confidencePercent = confidenceFromComps(comps, obj.get("confidencePercent")?.asInt ?: 0),
                        citations = citations,
                        retrievedAt = System.currentTimeMillis(),
                        searchProviderKey = evidence.providerKey,
                        searchResultCount = evidence.results.size,
                        searchError = evidence.error,
                        soldDataUnavailable = evidence.results.isNotEmpty() && !evidence.soldCapable,
                    )
                PriceResearchResult(
                    research = research,
                    suggestedValue = obj.get("suggestedValue")?.asDouble,
                    hasWebEvidence = evidence.results.isNotEmpty(),
                )
            }.getOrElse {
                Log.w(TAG, "Failed to parse pricing response: ${it.javaClass.simpleName}")
                PriceResearchResult(error = ERROR_PARSE)
            }

        private fun comparableStats(comps: List<MarketComp>): ComparableStats? {
            val prices =
                comps
                    .filter { it.sold }
                    .map { it.price }
                    .ifEmpty { comps.map { it.price } }
            if (prices.isEmpty()) return null
            return ComparableStats(
                average = prices.average(),
                low = prices.min(),
                high = prices.max(),
            )
        }

        private fun confidenceFromComps(
            comps: List<MarketComp>,
            modelConfidence: Int,
        ): Int {
            if (comps.isEmpty()) return modelConfidence.coerceIn(0, MAX_AI_ONLY_CONFIDENCE)
            val soldCount = comps.count { it.sold }
            val platformCount = comps.map { it.platformKey }.distinct().size
            val confidenceFloor = (15 + soldCount.coerceAtMost(5) * 8 + platformCount.coerceAtMost(3) * 5).coerceAtMost(70)
            val confidenceCeiling = (30 + soldCount.coerceAtMost(5) * 9 + platformCount.coerceAtMost(3) * 5).coerceAtMost(85)
            return modelConfidence.coerceAtLeast(confidenceFloor).coerceAtMost(confidenceCeiling)
        }

        /**
         * Pulls the assistant text out of a Responses API payload. The `output` array
         * usually starts with a `reasoning` item for gpt-5 models, so the message item
         * must be located by type rather than by index.
         */
        private fun extractResponsesText(root: JsonObject): String {
            val output = root.getAsJsonArray("output")
            val message =
                output
                    .firstOrNull { el ->
                        el.asJsonObject.get("type")?.asString == "message"
                    }?.asJsonObject ?: error("No message item in Responses output")
            return message
                .getAsJsonArray("content")
                .mapNotNull { part ->
                    val obj = part.asJsonObject
                    if (obj.get("type")?.asString == "output_text") obj.get("text")?.asString else null
                }.joinToString("")
        }

        companion object {
            private const val TAG = "PriceResearchService"

            /**
             * OpenAI model used for price synthesis. A small/cheap reasoning model is
             * sufficient since the heavy lifting is the supplied search evidence. Kept
             * here so it can be swapped in one place as newer mini models ship.
             */
            private const val MODEL = "gpt-5-mini"

            private const val MAX_CITATIONS = 8
            private const val MAX_SEARCH_RESULTS = 12
            private const val MIN_RESULTS_EARLY_STOP = 5
            private const val MAX_AI_ONLY_CONFIDENCE = 30

            /**
             * How many real marketplace postings each provider should produce before it stops
             * being asked further queries. Providers are billed per call, so the goal is enough
             * corroborating postings per source to be worth its cost — not every query against
             * every provider, which mostly re-fetched the same listings.
             */
            private const val LEGIT_POSTINGS_PER_PROVIDER = 4

            /**
             * Minimum combined real marketplace postings from the non-Brave providers before
             * Brave is skipped as an unnecessary fallback call. Below this, the site:-honoring
             * providers (and Jina's broadening queries) didn't turn up enough to be confident,
             * so Brave's generic search is worth the extra call and latency.
             */
            private const val BRAVE_FALLBACK_THRESHOLD = LEGIT_POSTINGS_PER_PROVIDER

            /**
             * How many result pages the Jina Reader opens per distinct marketplace present in
             * the evidence set (eBay, Mercari, OfferUp, Craigslist, Facebook Marketplace), so
             * verification spreads across marketplaces instead of all landing on whichever one
             * ranked highest overall.
             */
            private const val READS_PER_MARKETPLACE = 2

            /**
             * Below this length, a Jina Reader response is treated as a failed read rather than
             * a real listing — dead-listing and bot-block pages tend to be short (a redirect
             * notice, a captcha prompt) where a real listing page has a title, price,
             * description, and often related items.
             */
            private const val MIN_CONFIRMED_LISTING_CHARS = 300

            /**
             * Phrases indicating the Reader loaded a dead listing or a bot-block page rather
             * than real content, checked case-insensitively. Not exhaustive — each marketplace
             * phrases this differently and phrasing drifts over time — so this catches the
             * common cases rather than guaranteeing detection.
             */
            private val DEAD_PAGE_PHRASES =
                listOf(
                    "listing has been removed",
                    "page not found",
                    "item not found",
                    "verify you are human",
                    "verify you're human",
                    "enable javascript and cookies",
                    "access denied",
                    "attention required",
                )

            /** Cap on each evidence snippet after appending read page text (keeps prompt small). */
            private const val MAX_SNIPPET_CHARS = 2_200

            internal const val ERROR_INVALID_KEY =
                "Invalid or missing OpenAI API key. Check Settings."
            internal const val ERROR_RATE_LIMITED =
                "Rate limited by OpenAI. Wait a moment and try again."
            internal const val ERROR_UNAVAILABLE =
                "Pricing service is temporarily unavailable. Try again shortly."
            internal const val ERROR_MODEL_NOT_FOUND =
                "Selected model isn't available. Try a different model in Settings → AI."
            internal const val ERROR_TIMEOUT =
                "Price research timed out. Check your connection and try again."
            internal const val ERROR_NETWORK =
                "Network error. Check your connection and try again."
            internal const val ERROR_PARSE =
                "Couldn't read the pricing response. Please try again."
            internal const val ERROR_UNKNOWN = "Something went wrong. Please try again."

            internal fun friendlyHttpError(code: Int): String =
                when {
                    code == 401 || code == 403 -> ERROR_INVALID_KEY
                    code == 404 -> ERROR_MODEL_NOT_FOUND
                    code == 429 -> ERROR_RATE_LIMITED
                    code in 500..599 -> ERROR_UNAVAILABLE
                    else -> ERROR_UNAVAILABLE
                }

            internal fun friendlyNetworkError(e: Throwable): String =
                when (e) {
                    is SocketTimeoutException -> ERROR_TIMEOUT
                    is IOException -> ERROR_NETWORK
                    else -> ERROR_UNKNOWN
                }
        }
    }

private data class ComparableStats(
    val average: Double,
    val low: Double,
    val high: Double,
)

internal fun mergeVerifiedComparableListings(
    modelComps: List<MarketComp>,
    evidence: SearchEvidence,
): List<MarketComp> {
    val evidenceUrls = evidence.results.map { it.url }.filter { it.isNotBlank() }.toSet()
    val verifiedModelComps =
        modelComps.filter { comp ->
            comp.price.isFinite() && comp.price > 0.0 && comp.sourceUrl in evidenceUrls
        }
    val structuredComps =
        evidence.results.mapNotNull { result ->
            val platformKey = result.platformKey ?: return@mapNotNull null
            if (Platform.fromKey(platformKey) == null || result.sold != true) return@mapNotNull null
            val price = result.price?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            MarketComp(
                platformKey = platformKey,
                title = result.title,
                price = price,
                sold = true,
                date = result.date.ifBlank { "Completed sale" },
                sourceUrl = result.url,
            )
        }
    return (structuredComps + verifiedModelComps)
        .distinctBy { it.sourceUrl }
        .take(MAX_COMPARABLES)
}

private const val MAX_COMPARABLES = 12
