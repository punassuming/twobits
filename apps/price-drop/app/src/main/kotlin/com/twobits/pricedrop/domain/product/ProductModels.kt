package com.twobits.pricedrop.domain.product

import com.google.gson.JsonElement
import java.time.Instant

data class ProviderRef(
    val id: String,
    val executionMode: String,
)

data class ProductIdentity(
    val brand: String? = null,
    val model: String? = null,
    val manufacturerPartNumber: String? = null,
    val gtin: String? = null,
    val upc: String? = null,
    val ean: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
)

data class Money(
    val amountMinor: Long,
    val currency: String,
)

data class Merchant(
    val id: String,
    val name: String,
)

enum class Availability {
    IN_STOCK,
    OUT_OF_STOCK,
    PREORDER,
    UNKNOWN,
}

enum class ItemCondition {
    NEW,
    USED,
    REFURBISHED,
    UNKNOWN,
}

enum class PromotionApplicability {
    CONFIRMED_FOR_LISTING,
    CONFIRMED_FOR_PRODUCT,
    MERCHANT_WIDE,
    POSSIBLY_APPLICABLE,
    UNKNOWN,
}

data class Discount(
    val amount: Money? = null,
    val percentage: Double? = null,
)

data class Promotion(
    val code: String? = null,
    val description: String,
    val discount: Discount? = null,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
    val applicability: PromotionApplicability,
    val confidence: Double,
    val source: ProviderRef,
)

data class ProductOffer(
    val merchant: Merchant,
    val listingId: String? = null,
    val seller: String? = null,
    val itemPrice: Money,
    val shippingPrice: Money? = null,
    val totalPrice: Money,
    val availability: Availability,
    val condition: ItemCondition,
    val productUrl: String,
    val observedAt: Instant,
    val promotions: List<Promotion> = emptyList(),
    val provider: ProviderRef,
)

data class ProductCandidate(
    val provider: ProviderRef,
    val providerProductId: String? = null,
    val title: String,
    val identity: ProductIdentity = ProductIdentity(),
    val attributes: Map<String, String> = emptyMap(),
    val imageUrls: List<String> = emptyList(),
    val offer: ProductOffer? = null,
    val sourceUrl: String,
    val rawProviderData: Map<String, JsonElement> = emptyMap(),
)

data class ProductDetails(
    val title: String,
    val identity: ProductIdentity,
    val attributes: Map<String, String> = emptyMap(),
    val imageUrls: List<String> = emptyList(),
    val sourceUrl: String,
    val provider: ProviderRef,
)

enum class MatchClassification {
    EXACT,
    HIGH_CONFIDENCE,
    POSSIBLE_VARIANT,
    UNRELATED,
}

data class MatchEvidence(
    val signal: String,
    val score: Double,
    val detail: String,
)

data class MatchConflict(
    val field: String,
    val target: String,
    val candidate: String,
)

data class MatchAssessment(
    val score: Double,
    val classification: MatchClassification,
    val evidence: List<MatchEvidence>,
    val conflicts: List<MatchConflict>,
)

data class ResolvedProduct(
    val canonicalProductId: String,
    val candidate: ProductCandidate,
    val assessment: MatchAssessment,
    val offers: List<ProductOffer>,
)

enum class ProviderStatus {
    SUCCESS,
    PARTIAL,
    TIMEOUT,
    ERROR,
}

data class ProviderDiagnostic(
    val provider: String,
    val status: ProviderStatus,
    val latencyMs: Long,
    val resultCount: Int,
    val message: String? = null,
)

data class ProductDiscoveryResult(
    val query: String,
    val target: ProductIdentity,
    val products: List<ResolvedProduct>,
    val providerDiagnostics: List<ProviderDiagnostic>,
)
