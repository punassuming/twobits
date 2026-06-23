package com.twobits.pricedrop.data.provider

/**
 * Providers PriceDrop can talk to. Kept local to the app (rather than extending the shared
 * `com.twobits.apikeys.ProviderType` enum) so that adding shopping/search/coupon providers does
 * not ripple into Scrybe/Shelf Snap's exhaustive `when (ProviderType)` sites.
 */
enum class PriceDropProvider(
    val key: String,
    val displayName: String,
    /** Direct base URL used in BYOK mode. Pro mode always routes through api.twobits.app. */
    val byokBaseUrl: String,
    val description: String,
) {
    OPENAI("openai", "OpenAI", "https://api.openai.com/", "AI query understanding + answer generation"),
    WEB_SEARCH("web_search", "Jina AI", "https://s.jina.ai/", "Web search + page reading via r.jina.ai"),
    SHOPPING("shopping", "SerpAPI", "https://serpapi.com/", "Google/Bing results · supplements Jina for broader coverage"),
    KEEPA("keepa", "Keepa", "https://api.keepa.com/", "Amazon price history · optional for long-term trend charts"),
    COUPON("coupon", "Coupon provider", "https://api.couponlayer.com/", "Coupon code discovery by retailer domain · used by Coupon section"),
    RAINFOREST("rainforest", "Rainforest API", "https://api.rainforestapi.com/", "Amazon product data · ASIN lookup + real-time price"),
    ;

    companion object {
        fun fromKey(k: String): PriceDropProvider? = entries.firstOrNull { it.key == k }
    }
}

/** Per-provider access mode chosen by the user in Settings. */
enum class ProviderMode(
    val value: String,
) {
    OFF("off"),
    BYOK("byok"),
    PRO("pro"),
    ;

    companion object {
        fun fromValue(v: String?): ProviderMode = entries.firstOrNull { it.value == v } ?: OFF
    }
}
