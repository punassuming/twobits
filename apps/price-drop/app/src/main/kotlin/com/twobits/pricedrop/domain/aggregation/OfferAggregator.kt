package com.twobits.pricedrop.domain.aggregation

import com.twobits.pricedrop.domain.product.ProductOffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfferAggregator
    @Inject
    constructor() {
        fun deduplicate(offers: List<ProductOffer>): List<ProductOffer> =
            offers
                .groupBy { offer ->
                    listOf(
                        offer.merchant.id,
                        offer.listingId.orEmpty(),
                        offer.productUrl.substringBefore('?'),
                    ).joinToString("|")
                }.values
                .map { duplicates -> duplicates.minBy { it.totalPrice.amountMinor } }
                .sortedBy { it.totalPrice.amountMinor }
    }
