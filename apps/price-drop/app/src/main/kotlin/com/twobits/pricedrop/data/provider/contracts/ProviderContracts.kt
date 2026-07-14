package com.twobits.pricedrop.data.provider.contracts

import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProductDetails
import com.twobits.pricedrop.domain.product.ProductIdentity
import com.twobits.pricedrop.domain.product.ProductOffer
import com.twobits.pricedrop.domain.product.Promotion
import com.twobits.pricedrop.domain.product.ProviderDiagnostic

enum class ProviderCapability {
    SEARCH,
    DETAILS,
    OFFERS,
    PROMOTIONS,
}

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: Set<ProviderCapability>,
)

data class ProductSearchRequest(
    val query: String,
    val identifiers: ProductIdentity = ProductIdentity(),
    val locale: String = "en-US",
    val country: String = "US",
    val postalCode: String? = null,
    val maxCandidates: Int = 30,
)

sealed interface ProviderResult<out T> {
    data class Success<T>(
        val value: T,
        val diagnostics: List<ProviderDiagnostic> = emptyList(),
    ) : ProviderResult<T>

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : ProviderResult<Nothing>
}

interface ProductSearchProvider {
    val descriptor: ProviderDescriptor

    suspend fun search(request: ProductSearchRequest): ProviderResult<List<ProductCandidate>>
}

interface ProductDetailsProvider {
    val descriptor: ProviderDescriptor

    suspend fun getProduct(identity: ProductIdentity): ProviderResult<ProductDetails>
}

interface OfferProvider {
    val descriptor: ProviderDescriptor

    suspend fun getOffers(identity: ProductIdentity): ProviderResult<List<ProductOffer>>
}

data class PromotionRequest(
    val identity: ProductIdentity,
    val merchantId: String? = null,
)

interface PromotionProvider {
    val descriptor: ProviderDescriptor

    suspend fun getPromotions(request: PromotionRequest): ProviderResult<List<Promotion>>
}
