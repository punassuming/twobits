package com.twobits.pricedrop.data.provider.rainforest

import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.provider.contracts.OfferProvider
import com.twobits.pricedrop.data.provider.contracts.ProductDetailsProvider
import com.twobits.pricedrop.data.provider.contracts.ProviderCapability
import com.twobits.pricedrop.data.provider.contracts.ProviderDescriptor
import com.twobits.pricedrop.data.provider.contracts.ProviderResult
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import com.twobits.pricedrop.domain.product.Availability
import com.twobits.pricedrop.domain.product.ItemCondition
import com.twobits.pricedrop.domain.product.Merchant
import com.twobits.pricedrop.domain.product.Money
import com.twobits.pricedrop.domain.product.ProductDetails
import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProductOffer
import com.twobits.pricedrop.domain.product.ProviderRef
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RainforestAmazonProvider
    @Inject
    constructor(
        private val api: PriceDropApiClient,
        private val settings: ProviderSettingsStore,
    ) : ProductDetailsProvider,
        OfferProvider {
        override val descriptor =
            ProviderDescriptor(
                id = "rainforest",
                displayName = "Rainforest Amazon enrichment",
                capabilities = setOf(ProviderCapability.DETAILS, ProviderCapability.OFFERS),
            )

        override suspend fun getProduct(identity: ProductIdentity): ProviderResult<ProductDetails> =
            runCatching {
                val response = api.price(identity.asin, identity.upc)
                require(response.found) { "Amazon product was not found" }
                ProductDetails(
                    title = response.title.orEmpty(),
                    identity = identity.copy(asin = response.asin ?: identity.asin),
                    sourceUrl = response.url.orEmpty(),
                    provider = providerRef(),
                )
            }.fold(
                onSuccess = { ProviderResult.Success(it) },
                onFailure = { ProviderResult.Failure(it.message ?: "Rainforest details failed", it) },
            )

        override suspend fun getOffers(identity: ProductIdentity): ProviderResult<List<ProductOffer>> =
            runCatching {
                val response = api.price(identity.asin, identity.upc)
                if (!response.found) return@runCatching emptyList()
                val providerRef = providerRef()
                response.offers.mapNotNull { offer ->
                    val itemPrice = offer.price ?: return@mapNotNull null
                    val shipping = offer.shipping ?: 0.0
                    val currency = response.currency ?: "USD"
                    ProductOffer(
                        merchant = Merchant("amazon", "Amazon"),
                        seller = offer.seller,
                        itemPrice = Money(itemPrice.toMinor(), currency),
                        shippingPrice = Money(shipping.toMinor(), currency),
                        totalPrice = Money((itemPrice + shipping).toMinor(), currency),
                        availability = availability(offer.availability),
                        condition = ItemCondition.UNKNOWN,
                        productUrl = offer.url.orEmpty(),
                        observedAt = Instant.now(),
                        provider = providerRef,
                    )
                }
            }.fold(
                onSuccess = { ProviderResult.Success(it) },
                onFailure = { ProviderResult.Failure(it.message ?: "Rainforest offers failed", it) },
            )

        private fun Double.toMinor(): Long =
            BigDecimal.valueOf(this)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()

        private fun availability(value: String?): Availability =
            when {
                value == null -> Availability.UNKNOWN
                value.contains("out", ignoreCase = true) -> Availability.OUT_OF_STOCK
                else -> Availability.IN_STOCK
            }

        private suspend fun providerRef(): ProviderRef =
            ProviderRef(descriptor.id, settings.getMode(PriceDropProvider.RAINFOREST).name)
    }
