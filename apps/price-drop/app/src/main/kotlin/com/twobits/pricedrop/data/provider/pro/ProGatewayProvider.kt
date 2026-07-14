package com.twobits.pricedrop.data.provider.pro

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.twobits.billing.SubscriptionRepository
import com.twobits.pricedrop.data.provider.contracts.ProductSearchProvider
import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.data.provider.contracts.ProviderCapability
import com.twobits.pricedrop.data.provider.contracts.ProviderDescriptor
import com.twobits.pricedrop.data.provider.contracts.ProviderResult
import com.twobits.pricedrop.domain.product.Availability
import com.twobits.pricedrop.domain.product.ItemCondition
import com.twobits.pricedrop.domain.product.Merchant
import com.twobits.pricedrop.domain.product.Money
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProductOffer
import com.twobits.pricedrop.domain.product.Promotion
import com.twobits.pricedrop.domain.product.PromotionApplicability
import com.twobits.pricedrop.domain.product.ProviderDiagnostic
import com.twobits.pricedrop.domain.product.ProviderRef
import com.twobits.pricedrop.domain.product.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProGatewayProvider
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val gson: Gson,
        private val subscriptions: SubscriptionRepository,
    ) : ProductSearchProvider {
        override val descriptor =
            ProviderDescriptor(
                id = "pro_gateway",
                displayName = "TwoBits Pro product discovery",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.OFFERS, ProviderCapability.PROMOTIONS),
            )

        override suspend fun search(request: ProductSearchRequest): ProviderResult<List<ProductCandidate>> =
            runCatching {
                val body =
                    JsonObject().apply {
                        addProperty("query", request.query)
                        add("identifiers", gson.toJsonTree(request.identifiers))
                        addProperty("locale", request.locale)
                        addProperty("country", request.country)
                        request.postalCode?.let { addProperty("postalCode", it) }
                        addProperty("maxCandidates", request.maxCandidates)
                        add("requestedCapabilities", gson.toJsonTree(listOf("SEARCH", "OFFERS", "PROMOTIONS")))
                    }
                withContext(Dispatchers.IO) {
                    val call =
                        Request
                            .Builder()
                            .url("https://api.twobits.app/v2/products/discover")
                            .addHeader("Authorization", "Bearer ${subscriptions.getAppUserId()}")
                            .addHeader("X-TwoBits-App", "pricedrop")
                            .post(body.toString().toRequestBody(JSON_MEDIA))
                            .build()
                    client.newCall(call).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("Product discovery returned HTTP ${response.code}")
                        val gateway = gson.fromJson(text, ProductDiscoveryGatewayResponse::class.java)
                        require(gateway.schemaVersion == 2) { "Unsupported product discovery schema ${gateway.schemaVersion}" }
                        gateway.products.flatMap { it.toCandidates() } to
                            gateway.providerDiagnostics.map { it.toDomain() }
                    }
                }
            }.fold(
                onSuccess = { (candidates, diagnostics) -> ProviderResult.Success(candidates, diagnostics) },
                onFailure = { ProviderResult.Failure(it.message ?: "Pro product discovery failed", it) },
            )

        private fun GatewayDiagnostic.toDomain(): ProviderDiagnostic =
            ProviderDiagnostic(
                provider = provider,
                status = enumValueOrDefault(status, ProviderStatus.ERROR),
                latencyMs = latencyMs,
                resultCount = resultCount,
                message = message,
            )

        private fun GatewayProduct.toCandidates(): List<ProductCandidate> {
            val ref = ProviderRef(provider.id, provider.executionMode)
            val mappedOffers = offers.map { it.toDomain() }
            val offerOptions: List<ProductOffer?> = if (mappedOffers.isEmpty()) listOf(null) else mappedOffers
            val mappedIdentity =
                ProductIdentity(
                    brand = identity.brand,
                    model = identity.model,
                    manufacturerPartNumber = identity.manufacturerPartNumber,
                    gtin = identity.gtin,
                    upc = identity.upc,
                    ean = identity.ean,
                    isbn = identity.isbn,
                    asin = identity.asin,
                )
            return offerOptions.map { offer ->
                ProductCandidate(
                    provider = offer?.provider ?: ref,
                    providerProductId = canonicalProductId,
                    title = title,
                    identity = mappedIdentity,
                    imageUrls = imageUrls,
                    offer = offer,
                    sourceUrl = offer?.productUrl ?: sourceUrl,
                )
            }
        }

        private fun GatewayOffer.toDomain(): ProductOffer =
            ProductOffer(
                merchant = Merchant(merchant.id, merchant.name),
                listingId = listingId,
                seller = seller,
                itemPrice = Money(itemPrice.amountMinor, itemPrice.currency),
                shippingPrice = shippingPrice?.let { Money(it.amountMinor, it.currency) },
                totalPrice = Money(totalPrice.amountMinor, totalPrice.currency),
                availability = enumValueOrDefault(availability, Availability.UNKNOWN),
                condition = enumValueOrDefault(condition, ItemCondition.UNKNOWN),
                productUrl = productUrl,
                observedAt = runCatching { Instant.parse(observedAt) }.getOrDefault(Instant.now()),
                promotions =
                    promotions.map { promotion ->
                        Promotion(
                            code = promotion.code,
                            description = promotion.description,
                            applicability =
                                enumValueOrDefault(
                                    promotion.applicability,
                                    PromotionApplicability.UNKNOWN,
                                ),
                            confidence = promotion.confidence,
                            source = ProviderRef(promotion.source.id, promotion.source.executionMode),
                        )
                    },
                provider = ProviderRef(provider.id, provider.executionMode),
            )

        private inline fun <reified T : Enum<T>> enumValueOrDefault(
            value: String,
            fallback: T,
        ): T = enumValues<T>().firstOrNull { it.name == value.uppercase() } ?: fallback

        private companion object {
            val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        }
    }
