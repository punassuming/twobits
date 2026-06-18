package com.twobits.pricedrop.data.remote.dto

import com.google.gson.annotations.SerializedName

// Worker response shapes — these mirror /home/user/twobits-worker/worker.js handlers exactly.
// Keep field names in sync with pdSearch/pdPrice/pdHistory/pdCoupons/pdBarcode.

data class SearchResponseDto(
    @SerializedName("results") val results: List<SearchResultDto> = emptyList(),
)

data class SearchResultDto(
    @SerializedName("title") val title: String? = null,
    // SerpAPI returns price as a display string, e.g. "$49.99".
    @SerializedName("price") val price: String? = null,
    @SerializedName("source") val source: String? = null,
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
)

data class HistoryResponseDto(
    @SerializedName("history") val history: List<HistoryPointDto> = emptyList(),
    @SerializedName("lowestPrice") val lowestPrice: Double? = null,
)

data class HistoryPointDto(
    @SerializedName("ts") val ts: Long = 0L,
    @SerializedName("price") val price: Double = 0.0,
)

data class CouponsResponseDto(
    @SerializedName("coupons") val coupons: List<CouponDto> = emptyList(),
)

data class CouponDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("discount") val discount: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("expires") val expires: String? = null,
    @SerializedName("store") val store: String? = null,
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
