package com.twobits.pricedrop.data.remote.dto

import com.google.gson.annotations.SerializedName

// Legacy v1 enrichment DTOs. Application search consumes the provider-neutral v2 domain model.

data class SearchResponseDto(
    @SerializedName("results") val results: List<SearchResultDto> = emptyList(),
)

data class SearchResultDto(
    @SerializedName("title") val title: String? = null,
    // SearchAPI.io returns price as a display string, e.g. "$49.99".
    @SerializedName("price") val price: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("url") val url: String? = null,
)

data class OfferDto(
    @SerializedName("seller") val seller: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("shipping") val shipping: Double? = null,
    @SerializedName("availability") val availability: String? = null,
    @SerializedName("url") val url: String? = null,
)

data class PriceResponseDto(
    @SerializedName("found") val found: Boolean = false,
    @SerializedName("title") val title: String? = null,
    @SerializedName("asin") val asin: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("availability") val availability: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("offers") val offers: List<OfferDto> = emptyList(),
)

data class HistoryResponseDto(
    @SerializedName("history") val history: List<HistoryPointDto> = emptyList(),
    @SerializedName("lowestPrice") val lowestPrice: Double? = null,
)

data class HistoryPointDto(
    @SerializedName("ts") val ts: Long = 0L,
    @SerializedName("price") val price: Double = 0.0,
)

data class BarcodeResponseDto(
    @SerializedName("found") val found: Boolean = false,
    @SerializedName("title") val title: String? = null,
    @SerializedName("asin") val asin: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("url") val url: String? = null,
)

// OpenAI-compatible chat completions (Ask tab, via the Worker's /v1/chat/completions proxy).
data class ChatResponseDto(
    @SerializedName("choices") val choices: List<ChatChoiceDto> = emptyList(),
)

data class ChatChoiceDto(
    @SerializedName("message") val message: ChatMessageDto? = null,
)

data class ChatMessageDto(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null,
)
