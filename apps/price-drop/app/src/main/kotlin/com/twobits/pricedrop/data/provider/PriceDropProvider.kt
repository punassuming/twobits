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
    /** Step-by-step guide shown in the credential row when no key is configured. */
    val setupHint: String,
    /** Signup / API-keys page URL opened by the "Sign up" link in the credential row. */
    val signupUrl: String,
) {
    OPENAI(
        key = "openai",
        displayName = "OpenAI",
        byokBaseUrl = "https://api.openai.com/",
        description = "AI query understanding + answer generation",
        setupHint =
            "Sign in at platform.openai.com → open API Keys → click Create new secret key → " +
                "copy and paste here. Usage is billed per token — a typical session costs a few cents.",
        signupUrl = "https://platform.openai.com/api-keys",
    ),
    WEB_SEARCH(
        key = "web_search",
        displayName = "Jina AI",
        byokBaseUrl = "https://s.jina.ai/",
        description = "Web search + page reading via r.jina.ai",
        setupHint =
            "Create an account at jina.ai → open the dashboard → copy your API key. " +
                "Free tier includes 1 million tokens — enough for hundreds of price searches.",
        signupUrl = "https://jina.ai",
    ),
    SHOPPING(
        key = "shopping",
        displayName = "SerpAPI",
        byokBaseUrl = "https://serpapi.com/",
        description = "Google/Bing results · supplements Jina for broader coverage",
        setupHint =
            "Sign up at serpapi.com → open the Dashboard → copy your Private API key. " +
                "Free plan includes 100 searches per month.",
        signupUrl = "https://serpapi.com",
    ),
    KEEPA(
        key = "keepa",
        displayName = "Keepa",
        byokBaseUrl = "https://api.keepa.com/",
        description = "Amazon price history · optional for long-term trend charts",
        setupHint =
            "Subscribe to a Keepa API plan at keepa.com → open your account settings → copy the API key. " +
                "Paid plans only — the cheapest tier is sufficient for most use.",
        signupUrl = "https://keepa.com/keepa-api-subscribe",
    ),
    COUPON(
        key = "coupon",
        displayName = "Couponlayer",
        byokBaseUrl = "https://api.couponlayer.com/",
        description = "Coupon code discovery by retailer domain · used by Coupon section",
        setupHint =
            "Create an account at couponlayer.com → open your Dashboard → copy the API Access Key. " +
                "A free plan is available with limited monthly lookups.",
        signupUrl = "https://couponlayer.com",
    ),
    RAINFOREST(
        key = "rainforest",
        displayName = "Rainforest API",
        byokBaseUrl = "https://api.rainforestapi.com/",
        description = "Amazon product data · ASIN lookup + real-time price",
        setupHint =
            "Sign up at rainforestapi.com → open the Dashboard → copy your API key. " +
                "Optional — only needed for Amazon ASIN lookups and real-time product prices.",
        signupUrl = "https://rainforestapi.com",
    ),
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
