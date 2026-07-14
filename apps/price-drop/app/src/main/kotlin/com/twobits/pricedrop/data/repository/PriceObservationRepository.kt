package com.twobits.pricedrop.data.repository

import com.twobits.pricedrop.data.local.PriceObservationDao
import com.twobits.pricedrop.data.model.PriceObservationEntity
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.domain.product.ProductOffer
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceObservationRepository
    @Inject
    constructor(
        private val dao: PriceObservationDao,
    ) {
        suspend fun recordOffer(
            productId: Long,
            canonicalProductId: String,
            offer: ProductOffer,
            provenance: String = "local_observation",
        ): Long =
            dao.insert(
                PriceObservationEntity(
                    productId = productId,
                    canonicalProductId = canonicalProductId,
                    merchantId = offer.merchant.id,
                    listingId = offer.listingId ?: offer.productUrl.substringBefore('?'),
                    itemPriceMinor = offer.itemPrice.amountMinor,
                    shippingPriceMinor = offer.shippingPrice?.amountMinor,
                    totalPriceMinor = offer.totalPrice.amountMinor,
                    currency = offer.totalPrice.currency,
                    availability = offer.availability.name,
                    observedAt = offer.observedAt.toEpochMilli(),
                    provider = offer.provider.id,
                    provenance = provenance,
                ),
            )

        suspend fun recordLegacyPrice(
            product: WatchedProduct,
            price: Double,
            effectivePrice: Double,
            retailer: String,
            observedAt: Long,
            provider: String,
            provenance: String,
        ): Long =
            dao.insert(
                PriceObservationEntity(
                    productId = product.id,
                    canonicalProductId = product.canonicalProductId.ifBlank { "legacy:${product.id}" },
                    merchantId = retailer.ifBlank { "unknown" },
                    listingId = "observation:$observedAt",
                    itemPriceMinor = price.toMinor(),
                    totalPriceMinor = effectivePrice.toMinor(),
                    currency = "USD",
                    availability = "UNKNOWN",
                    observedAt = observedAt,
                    provider = provider,
                    provenance = provenance,
                ),
            )

        private fun Double.toMinor(): Long =
            BigDecimal.valueOf(this)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
    }
