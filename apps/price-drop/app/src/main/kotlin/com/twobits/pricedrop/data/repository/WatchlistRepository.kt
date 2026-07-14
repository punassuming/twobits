package com.twobits.pricedrop.data.repository

import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.twobits.pricedrop.data.local.ActivityDao
import com.twobits.pricedrop.data.local.CouponDao
import com.twobits.pricedrop.data.local.OfferDao
import com.twobits.pricedrop.data.local.PriceEventDao
import com.twobits.pricedrop.data.local.WatchedProductDao
import com.twobits.pricedrop.data.model.Activity
import com.twobits.pricedrop.data.model.ActivityType
import com.twobits.pricedrop.data.model.Coupon
import com.twobits.pricedrop.data.model.CouponState
import com.twobits.pricedrop.data.model.Offer
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import com.twobits.pricedrop.data.remote.model.BarcodeMatch
import com.twobits.pricedrop.data.remote.model.SearchHit
import com.twobits.pricedrop.domain.EffectivePrice
import com.twobits.pricedrop.domain.aggregation.ProductDiscoveryCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of attempting to add a product to the watchlist. */
sealed interface AddResult {
    /** Product was added; [id] is its new row id. */
    data class Added(
        val id: Long,
    ) : AddResult

    /** Free plan's active-product limit was reached; the product was not added. */
    data object LimitReached : AddResult
}

@Singleton
class WatchlistRepository
    @Inject
    constructor(
        private val watchedProductDao: WatchedProductDao,
        private val priceEventDao: PriceEventDao,
        private val couponDao: CouponDao,
        private val activityDao: ActivityDao,
        private val offerDao: OfferDao,
        private val api: PriceDropApiClient,
        private val discoveryCoordinator: ProductDiscoveryCoordinator,
        private val observationRepository: PriceObservationRepository,
        private val subscriptionRepository: SubscriptionRepository,
    ) {
        private val discoveryRefreshMutex = Mutex()
        private val discoveryRefreshes = mutableMapOf<String, Long>()

        fun observeAll(): Flow<List<WatchedProduct>> = watchedProductDao.observeAll()

        suspend fun getById(id: Long): WatchedProduct? = watchedProductDao.getById(id)

        /**
         * Adds a product to the watchlist, enforcing the free-plan active-product cap.
         * Pro users have no limit; free users may actively track up to [FREE_ACTIVE_LIMIT].
         */
        suspend fun add(product: WatchedProduct): AddResult {
            // Refresh first so a cold-started Pro subscriber isn't capped as Free.
            subscriptionRepository.ensureFresh()
            val isPro = subscriptionRepository.subscriptionTier.value is SubscriptionTier.Pro
            if (!isPro && watchedProductDao.countActive() >= FREE_ACTIVE_LIMIT) {
                return AddResult.LimitReached
            }
            val id = watchedProductDao.insert(product)
            activityDao.insert(Activity(productId = id, type = ActivityType.ADDED.value))
            return AddResult.Added(id)
        }

        /**
         * Best-effort: backfill pre-tracking price history for Amazon items so the chart isn't
         * empty until enough scheduled checks accumulate their own points. A failure here (no
         * Rainforest key/not Pro, or an upstream error) is swallowed — never surfaced to the
         * caller. This is a network call, so callers should launch it rather than await it
         * alongside [add] so a slow history fetch doesn't make the add flow look stuck.
         */
        suspend fun backfillHistoryIfNeeded(
            productId: Long,
            asin: String,
        ) {
            if (asin.isBlank()) return
            runCatching { fetchHistory(productId, asin) }
        }

        suspend fun update(product: WatchedProduct) = watchedProductDao.update(product)

        suspend fun remove(id: Long) = watchedProductDao.deactivate(id)

        fun observePriceHistory(productId: Long): Flow<List<PriceEvent>> = priceEventDao.observeForProduct(productId)

        fun observeCoupons(productId: Long): Flow<List<Coupon>> = couponDao.observeForProduct(productId)

        suspend fun addManualCoupon(
            productId: Long,
            code: String,
            description: String,
        ) {
            val normalizedCode = code.trim().uppercase()
            if (normalizedCode.isBlank()) return
            couponDao.insertAll(
                listOf(
                    Coupon(
                        productId = productId,
                        code = normalizedCode,
                        description = description.trim(),
                        state = CouponState.UNVERIFIED.value,
                        source = "manual",
                    ),
                ),
            )
            activityDao.insert(
                Activity(
                    productId = productId,
                    type = ActivityType.COUPON_FOUND.value,
                    detail = normalizedCode,
                ),
            )
        }

        fun observeActivity(productId: Long): Flow<List<Activity>> = activityDao.observeForProduct(productId)

        fun observeOffers(productId: Long): Flow<List<Offer>> = offerDao.observeForProduct(productId)

        /** Refresh competing-seller offers for a product from the price endpoint. */
        suspend fun refreshOffers(productId: Long) {
            val product = watchedProductDao.getById(productId) ?: return
            val resp =
                when {
                    product.asin.isNotBlank() -> api.price(asin = product.asin)
                    product.upc.isNotBlank() -> api.price(upc = product.upc)
                    else -> return
                }
            if (!resp.found) return
            val offers =
                resp.offers.mapNotNull { dto ->
                    val base = dto.price ?: return@mapNotNull null
                    val ship = dto.shipping ?: 0.0
                    Offer(
                        productId = productId,
                        retailer = "amazon.com",
                        seller = dto.seller.orEmpty(),
                        basePrice = base,
                        shipping = ship,
                        effectivePrice = EffectivePrice.compute(base = base, shipping = ship),
                        availability = dto.availability ?: "unknown",
                        url = dto.url.orEmpty(),
                        source = "rainforest",
                    )
                }
            offerDao.deleteForProduct(productId)
            offerDao.insertAll(offers)
        }

        /** Record an observed price, maintain tracked stats, and append to the activity log. */
        suspend fun recordPrice(
            productId: Long,
            price: Double,
            retailer: String,
            effectivePrice: Double = price,
            recordObservation: Boolean = true,
        ) {
            val product = watchedProductDao.getById(productId) ?: return
            val observedAt = System.currentTimeMillis()
            if (recordObservation) {
                observationRepository.recordLegacyPrice(
                    product = product,
                    price = price,
                    effectivePrice = effectivePrice,
                    retailer = retailer,
                    observedAt = observedAt,
                    provider = product.source.ifBlank { "price_refresh" },
                    provenance = "local_observation",
                )
            }
            priceEventDao.insert(
                PriceEvent(
                    productId = productId,
                    price = price,
                    effectivePrice = effectivePrice,
                    retailer = retailer,
                    recordedAt = observedAt,
                ),
            )
            watchedProductDao.updatePrice(productId, price, System.currentTimeMillis())
            val updatedProduct = watchedProductDao.getById(productId) ?: return
            val events = priceEventDao.getForProduct(productId)
            val prices = events.map { it.price }
            watchedProductDao.update(
                updatedProduct.copy(
                    trackedLow = prices.minOrNull() ?: price,
                    trackedHigh = prices.maxOrNull() ?: price,
                    trackedAvg = if (prices.isNotEmpty()) prices.average() else price,
                ),
            )
        }

        // ── Remote ────────────────────────────────────────────────────────────────

        /** Aggregated product search through every enabled provider for the selected mode. */
        suspend fun searchProducts(query: String): List<SearchHit> =
            discoveryCoordinator.discover(ProductSearchRequest(query)).products.map { product ->
                val candidate = product.candidate
                val bestOffer = product.offers.firstOrNull() ?: candidate.offer
                SearchHit(
                    title = candidate.title,
                    price = bestOffer?.totalPrice?.amountMinor?.div(100.0),
                    source = product.offers.map { it.provider.id }.distinct().joinToString().ifBlank { candidate.provider.id },
                    url = bestOffer?.productUrl ?: candidate.sourceUrl,
                    confidence = (product.assessment.score * 100).toInt(),
                    canonicalProductId = product.canonicalProductId,
                    identity = candidate.identity,
                    imageUrl = candidate.imageUrls.firstOrNull().orEmpty(),
                    offers = product.offers,
                )
            }

        suspend fun recordDiscoveredOffers(
            productId: Long,
            canonicalProductId: String,
            offers: List<com.twobits.pricedrop.domain.product.ProductOffer>,
            confidence: Int,
        ) {
            if (offers.isEmpty()) return
            offerDao.deleteForProduct(productId)
            offerDao.insertAll(
                offers.map { offer ->
                    Offer(
                        productId = productId,
                        retailer = offer.merchant.name,
                        seller = offer.seller.orEmpty(),
                        basePrice = offer.itemPrice.amountMinor / 100.0,
                        shipping = (offer.shippingPrice?.amountMinor ?: 0L) / 100.0,
                        effectivePrice = offer.totalPrice.amountMinor / 100.0,
                        availability = offer.availability.name.lowercase(),
                        confidence = confidence,
                        url = offer.productUrl,
                        source = offer.provider.id,
                        lastCheckedAt = offer.observedAt.toEpochMilli(),
                    )
                },
            )
            offers.forEach { offer ->
                observationRepository.recordOffer(productId, canonicalProductId, offer)
            }
            val promotions = offers.flatMap { it.promotions }.distinctBy { listOf(it.code, it.description, it.source.id) }
            couponDao.deleteProviderPromotions(productId)
            if (promotions.isNotEmpty()) {
                couponDao.insertAll(
                    promotions.map { promotion ->
                        Coupon(
                            productId = productId,
                            code = promotion.code.orEmpty(),
                            description = promotion.description,
                            state = CouponState.UNVERIFIED.value,
                            source = promotion.source.id,
                            applicability = promotion.applicability.name,
                            confidence = promotion.confidence,
                        )
                    },
                )
            }
        }

        /** Refresh the current price for a product keyed by ASIN/UPC; no-op for manual items. */
        suspend fun refreshPrice(
            productId: Long,
            force: Boolean = false,
        ) {
            val product = watchedProductDao.getById(productId) ?: return
            val now = System.currentTimeMillis()
            if (!force && now - product.lastCheckedAt < DISCOVERY_FRESHNESS_MS) return
            val refreshKey = product.canonicalProductId.ifBlank { product.title.lowercase() }
            val shouldRefresh =
                discoveryRefreshMutex.withLock {
                    val lastRefresh = discoveryRefreshes[refreshKey] ?: 0L
                    if (!force && now - lastRefresh < DISCOVERY_FRESHNESS_MS) {
                        false
                    } else {
                        discoveryRefreshes[refreshKey] = now
                        true
                    }
                }
            if (!shouldRefresh) return
            val discovery =
                discoveryCoordinator.discover(
                    ProductSearchRequest(
                        query = product.title,
                        identifiers =
                            com.twobits.pricedrop.domain.product.ProductIdentity(
                                brand = product.brand.ifBlank { null },
                                model = product.model.ifBlank { null },
                                manufacturerPartNumber = product.manufacturerPartNumber.ifBlank { null },
                                gtin = product.gtin.ifBlank { null },
                                upc = product.upc.ifBlank { null },
                                ean = product.ean.ifBlank { null },
                                asin = product.asin.ifBlank { null },
                            ),
                    ),
                )
            val resolved =
                discovery.products.firstOrNull { it.canonicalProductId == product.canonicalProductId }
                    ?: discovery.products.firstOrNull()
            val offer = resolved?.offers?.firstOrNull()
            if (resolved != null && offer != null) {
                val confidence = (resolved.assessment.score * 100).toInt()
                recordDiscoveredOffers(productId, resolved.canonicalProductId, resolved.offers, confidence)
                val total = offer.totalPrice.amountMinor / 100.0
                recordPrice(productId, total, offer.merchant.id, total, recordObservation = false)
                activityDao.insert(
                    Activity(productId = productId, type = ActivityType.CHECKED.value, detail = formatUsd(total)),
                )
                return
            }
            val resp =
                when {
                    product.asin.isNotBlank() -> api.price(asin = product.asin)
                    product.upc.isNotBlank() -> api.price(upc = product.upc)
                    else -> return
                }
            if (resp.found && resp.price != null) {
                recordPrice(productId, resp.price, retailerOf(resp.url))
                activityDao.insert(
                    Activity(productId = productId, type = ActivityType.CHECKED.value, detail = formatUsd(resp.price)),
                )
            } else {
                discoveryRefreshMutex.withLock { discoveryRefreshes.remove(refreshKey) }
            }
        }

        /** Import Rainforest history as external observations without replacing local history. */
        suspend fun fetchHistory(
            productId: Long,
            asin: String,
        ) {
            val resp = api.history(asin)
            if (resp.history.isEmpty()) return
            val product = watchedProductDao.getById(productId) ?: return
            resp.history.forEach { pt ->
                val inserted =
                    observationRepository.recordLegacyPrice(
                        product = product,
                        price = pt.price,
                        effectivePrice = pt.price,
                        retailer = "amazon.com",
                        observedAt = pt.ts,
                        provider = "rainforest",
                        provenance = "external_history",
                    )
                if (inserted != -1L) {
                    priceEventDao.insert(
                        PriceEvent(
                            productId = productId,
                            price = pt.price,
                            effectivePrice = pt.price,
                            retailer = "amazon.com",
                            recordedAt = pt.ts,
                        ),
                    )
                }
            }
        }

        /** Return embedded or manually entered promotions; no broad coupon API is assumed. */
        suspend fun fetchCoupons(
            productId: Long,
            query: String,
        ): List<Coupon> = couponDao.getForProduct(productId)

        /** Stamp the last coupon-check time for a product (used by the background worker to throttle). */
        suspend fun updateCouponCheckedAt(
            productId: Long,
            ts: Long,
        ) = watchedProductDao.updateCouponCheckedAt(productId, ts)

        /** Resolve a scanned UPC to the best matching product. */
        suspend fun resolveBarcode(upc: String): BarcodeMatch {
            val r = api.barcode(upc)
            return BarcodeMatch(
                found = r.found,
                title = r.title.orEmpty(),
                asin = r.asin.orEmpty(),
                imageUrl = r.imageUrl.orEmpty(),
                price = r.price,
                url = r.url.orEmpty(),
            )
        }

        private fun retailerOf(url: String?): String =
            url
                ?.let {
                    runCatching {
                        java.net
                            .URI(it)
                            .host
                            ?.removePrefix("www.")
                    }.getOrNull()
                }.orEmpty()

        private fun formatUsd(value: Double): String = "$" + String.format(java.util.Locale.US, "%.2f", value)

        companion object {
            /** Maximum simultaneously-tracked products on the free plan. Pro is unlimited. */
            const val FREE_ACTIVE_LIMIT = 3
            const val DISCOVERY_FRESHNESS_MS = 6 * 60 * 60 * 1_000L
        }
    }
