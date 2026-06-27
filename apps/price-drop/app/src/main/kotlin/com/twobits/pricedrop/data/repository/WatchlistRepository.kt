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
import com.twobits.pricedrop.data.model.DiscountType
import com.twobits.pricedrop.data.model.Offer
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import com.twobits.pricedrop.data.remote.PriceParser
import com.twobits.pricedrop.data.remote.model.BarcodeMatch
import com.twobits.pricedrop.data.remote.model.SearchHit
import com.twobits.pricedrop.domain.EffectivePrice
import kotlinx.coroutines.flow.Flow
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
        private val subscriptionRepository: SubscriptionRepository,
    ) {
        fun observeAll(): Flow<List<WatchedProduct>> = watchedProductDao.observeAll()

        suspend fun getById(id: Long): WatchedProduct? = watchedProductDao.getById(id)

        /**
         * Adds a product to the watchlist, enforcing the free-plan active-product cap.
         * Pro users have no limit; free users may actively track up to [FREE_ACTIVE_LIMIT].
         */
        suspend fun add(product: WatchedProduct): AddResult {
            val isPro = subscriptionRepository.subscriptionTier.value is SubscriptionTier.Pro
            if (!isPro && watchedProductDao.countActive() >= FREE_ACTIVE_LIMIT) {
                return AddResult.LimitReached
            }
            val id = watchedProductDao.insert(product)
            activityDao.insert(Activity(productId = id, type = ActivityType.ADDED.value))
            return AddResult.Added(id)
        }

        suspend fun update(product: WatchedProduct) = watchedProductDao.update(product)

        suspend fun remove(id: Long) = watchedProductDao.deactivate(id)

        fun observePriceHistory(productId: Long): Flow<List<PriceEvent>> = priceEventDao.observeForProduct(productId)

        fun observeCoupons(productId: Long): Flow<List<Coupon>> = couponDao.observeForProduct(productId)

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
        ) {
            priceEventDao.insert(
                PriceEvent(productId = productId, price = price, effectivePrice = effectivePrice, retailer = retailer),
            )
            watchedProductDao.updatePrice(productId, price, System.currentTimeMillis())
            val product = watchedProductDao.getById(productId) ?: return
            val events = priceEventDao.getForProduct(productId)
            val prices = events.map { it.price }
            watchedProductDao.update(
                product.copy(
                    trackedLow = prices.minOrNull() ?: price,
                    trackedHigh = prices.maxOrNull() ?: price,
                    trackedAvg = if (prices.isNotEmpty()) prices.average() else price,
                ),
            )
        }

        // ── Remote ────────────────────────────────────────────────────────────────

        /** Natural-language / keyword product search via the Worker. */
        suspend fun searchProducts(query: String): List<SearchHit> =
            api.search(query).results.mapNotNull { r ->
                val title = r.title ?: return@mapNotNull null
                SearchHit(
                    title = title,
                    price = PriceParser.parse(r.price),
                    source = r.source.orEmpty(),
                    url = r.url.orEmpty(),
                )
            }

        /** Refresh the current price for a product keyed by ASIN/UPC; no-op for manual items. */
        suspend fun refreshPrice(productId: Long) {
            val product = watchedProductDao.getById(productId) ?: return
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
            }
        }

        /** Backfill historical price points (Keepa) as observed price events. */
        suspend fun fetchHistory(
            productId: Long,
            asin: String,
        ) {
            val resp = api.history(asin)
            if (resp.history.isEmpty()) return
            priceEventDao.deleteForProduct(productId)
            resp.history.forEach { pt ->
                priceEventDao.insert(
                    PriceEvent(
                        productId = productId,
                        price = pt.price,
                        effectivePrice = pt.price,
                        recordedAt = pt.ts,
                    ),
                )
            }
        }

        /** Replace the coupon set for a product with the latest from the provider. */
        suspend fun fetchCoupons(
            productId: Long,
            query: String,
        ): List<Coupon> {
            val resp = api.coupons(query)
            val coupons =
                resp.coupons.mapNotNull { c ->
                    val code = c.code ?: return@mapNotNull null
                    Coupon(
                        productId = productId,
                        code = code,
                        description = c.description.orEmpty(),
                        discountType = DiscountType.fromValue(c.type).value,
                        discountValue = PriceParser.parse(c.discount) ?: 0.0,
                        state = CouponState.UNVERIFIED.value,
                        source = "coupon",
                        store = c.store.orEmpty(),
                        expiresAt = c.expires.orEmpty(),
                    )
                }
            couponDao.deleteForProduct(productId)
            couponDao.insertAll(coupons)
            if (coupons.isNotEmpty()) {
                activityDao.insert(
                    Activity(
                        productId = productId,
                        type = ActivityType.COUPON_FOUND.value,
                        detail = coupons.first().code,
                    ),
                )
            }
            return coupons
        }

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
        }
    }
