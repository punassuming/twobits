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
    /** Short (~6-10 word) subtitle for compact rows — the credentials list, per-feature provider
     *  toggles. [description] is the long explanation shown only in the expanded detail view. */
    val summary: String,
    val description: String,
    /** Step-by-step guide shown in the credential row when no key is configured. */
    val setupHint: String,
    /** Signup / API-keys page URL opened by the "Sign up" link in the credential row. */
    val signupUrl: String,
    /** Short BYOK cost-transparency note shown under the key field in AI Config. */
    val costEstimate: String,
) {
    OPENAI(
        key = "openai",
        displayName = "OpenAI",
        byokBaseUrl = "https://api.openai.com/",
        summary = "Required for Ask and turning a pasted URL into a product",
        description =
            "Required for the Ask assistant and for turning a pasted product URL into a " +
                "watchlist item — both fail without it. Keyword search and price tracking don't need it.",
        setupHint =
            "Sign in at platform.openai.com → open API Keys → click Create new secret key → " +
                "copy and paste here. Usage is billed per token — a typical session costs a few cents.",
        signupUrl = "https://platform.openai.com/api-keys",
        costEstimate = "Est. cost: a few cents per AI query session (pay-per-token)",
    ),
    WEB_SEARCH(
        key = "web_search",
        displayName = "Jina AI",
        byokBaseUrl = "https://s.jina.ai/",
        summary = "Required for URL-paste; fallback for keyword search",
        description =
            "Required to turn a pasted product URL into a watchlist item (paired with OpenAI). " +
                "Also works as a keyword-search fallback when SearchAPI.io/Serper aren't configured, " +
                "but plain web search has no price data — SearchAPI.io or Serper are needed for that.",
        setupHint =
            "Create an account at jina.ai → open the dashboard → copy your API key. " +
                "Free tier includes 1 million tokens — enough for hundreds of price searches.",
        signupUrl = "https://jina.ai",
        costEstimate = "Est. cost: free tier covers hundreds of searches/month",
    ),
    SHOPPING(
        key = "shopping",
        displayName = "SearchAPI.io",
        byokBaseUrl = "https://www.searchapi.io/",
        summary = "Recommended — keyword search with real prices",
        description =
            "Recommended for keyword search — the only BYOK search path with real shopping " +
                "price data. Without it (or Serper), search falls back to Jina and shows no prices.",
        setupHint =
            "Sign up at searchapi.io → open the Dashboard → copy your API key. " +
                "Developer plan includes 100 free searches per month.",
        signupUrl = "https://www.searchapi.io",
        costEstimate = "Est. cost: ~\$0.004 per search · 100 free/month",
    ),
    SERPER(
        key = "serper",
        displayName = "Serper.dev",
        byokBaseUrl = "https://google.serper.dev/",
        summary = "Recommended — cheaper alternative to SearchAPI.io",
        description =
            "Recommended for keyword search — a materially cheaper alternative to SearchAPI.io " +
                "that also returns real shopping price data. Without it (or SearchAPI.io), search " +
                "falls back to Jina and shows no prices.",
        setupHint =
            "Sign up at serper.dev → open the Dashboard → copy your API key. " +
                "2,500 free searches, no card required.",
        signupUrl = "https://serper.dev",
        costEstimate = "Est. cost: ~\$0.30–1.00 per 1,000 searches · 2,500 free",
    ),
    COUPON(
        key = "coupon",
        displayName = "LinkMyDeals",
        byokBaseUrl = "https://feed.linkmydeals.com/",
        summary = "Required for the Coupon section",
        description = "Required for the Coupon section — without it, coupon lookups return nothing.",
        setupHint =
            "Create a publisher account at linkmydeals.com → open Development → copy your API key. " +
                "Plans include a limited free-store option and paid complete feeds.",
        signupUrl = "https://linkmydeals.com/pricing/",
        costEstimate = "Est. cost: limited free stores; paid plans unlock complete feeds",
    ),
    RAINFOREST(
        key = "rainforest",
        displayName = "Rainforest API",
        byokBaseUrl = "https://api.rainforestapi.com/",
        summary = "Required — price, history, and barcode lookup",
        description =
            "Required for price tracking, price history, and barcode lookup — the app's core " +
                "watchlist feature. SearchAPI.io/Jina/Serper cannot substitute for this; without a " +
                "Rainforest key (or Pro), tracked Amazon items never update.",
        setupHint =
            "Sign up at rainforestapi.com → open the Dashboard → copy your API key. " +
                "Needed for Amazon ASIN price checks, price history, and barcode lookups.",
        signupUrl = "https://rainforestapi.com",
        costEstimate = "Est. cost: ~\$0.003 per product / price lookup",
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
