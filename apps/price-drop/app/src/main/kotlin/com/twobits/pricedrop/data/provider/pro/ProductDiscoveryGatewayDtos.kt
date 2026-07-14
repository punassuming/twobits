package com.twobits.pricedrop.data.provider.pro

import com.google.gson.annotations.SerializedName

data class ProductDiscoveryGatewayResponse(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("requestId") val requestId: String = "",
    @SerializedName("products") val products: List<GatewayProduct> = emptyList(),
    @SerializedName("providerDiagnostics") val providerDiagnostics: List<GatewayDiagnostic> = emptyList(),
)

data class GatewayProduct(
    @SerializedName("canonicalProductId") val canonicalProductId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("identity") val identity: GatewayIdentity = GatewayIdentity(),
    @SerializedName("imageUrls") val imageUrls: List<String> = emptyList(),
    @SerializedName("sourceUrl") val sourceUrl: String = "",
    @SerializedName("provider") val provider: GatewayProvider = GatewayProvider(),
    @SerializedName("offers") val offers: List<GatewayOffer> = emptyList(),
)

data class GatewayIdentity(
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("manufacturerPartNumber") val manufacturerPartNumber: String? = null,
    @SerializedName("gtin") val gtin: String? = null,
    @SerializedName("upc") val upc: String? = null,
    @SerializedName("ean") val ean: String? = null,
    @SerializedName("isbn") val isbn: String? = null,
    @SerializedName("asin") val asin: String? = null,
)

data class GatewayProvider(
    @SerializedName("id") val id: String = "pro",
    @SerializedName("executionMode") val executionMode: String = "PRO",
)

data class GatewayMoney(
    @SerializedName("amountMinor") val amountMinor: Long = 0,
    @SerializedName("currency") val currency: String = "USD",
)

data class GatewayMerchant(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
)

data class GatewayOffer(
    @SerializedName("merchant") val merchant: GatewayMerchant = GatewayMerchant(),
    @SerializedName("listingId") val listingId: String? = null,
    @SerializedName("seller") val seller: String? = null,
    @SerializedName("itemPrice") val itemPrice: GatewayMoney = GatewayMoney(),
    @SerializedName("shippingPrice") val shippingPrice: GatewayMoney? = null,
    @SerializedName("totalPrice") val totalPrice: GatewayMoney = GatewayMoney(),
    @SerializedName("availability") val availability: String = "UNKNOWN",
    @SerializedName("condition") val condition: String = "UNKNOWN",
    @SerializedName("productUrl") val productUrl: String = "",
    @SerializedName("observedAt") val observedAt: String = "",
    @SerializedName("promotions") val promotions: List<GatewayPromotion> = emptyList(),
    @SerializedName("provider") val provider: GatewayProvider = GatewayProvider(),
)

data class GatewayPromotion(
    @SerializedName("code") val code: String? = null,
    @SerializedName("description") val description: String = "",
    @SerializedName("applicability") val applicability: String = "UNKNOWN",
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("source") val source: GatewayProvider = GatewayProvider(),
)

data class GatewayDiagnostic(
    @SerializedName("provider") val provider: String = "",
    @SerializedName("status") val status: String = "ERROR",
    @SerializedName("latencyMs") val latencyMs: Long = 0,
    @SerializedName("resultCount") val resultCount: Int = 0,
    @SerializedName("message") val message: String? = null,
)
