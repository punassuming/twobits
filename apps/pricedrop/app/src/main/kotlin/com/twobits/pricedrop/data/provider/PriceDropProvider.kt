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
) {
    OPENAI("openai", "OpenAI", "https://api.openai.com/"),
    WEB_SEARCH("web_search", "Web search", "https://s.jina.ai/"),
    SHOPPING("shopping", "Shopping search", "https://serpapi.com/"),
    KEEPA("keepa", "Amazon / Keepa", "https://api.keepa.com/"),
    COUPON("coupon", "Coupon provider", "https://api.couponlayer.com/"),
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
