package com.twobits.pricedrop.data.provider

/** One selectable model for an [AiFeature] in BYOK mode. */
data class AiModelOption(
    val id: String,
    val name: String,
    val sub: String,
    val cost: String,
)

private val SEARCH_MODELS =
    listOf(
        AiModelOption("gpt-5.4-mini", "GPT-5.4 mini", "Recommended · fast · low cost", "~\$0.10/1M"),
        AiModelOption("gpt-5.4", "GPT-5.4", "Best accuracy · higher cost", "~\$1.50/1M"),
        AiModelOption("gpt-5-mini", "GPT-5 mini", "Previous generation · balanced", "~\$0.15/1M"),
    )

private val ASK_MODELS =
    listOf(
        AiModelOption("gpt-5.4-mini", "GPT-5.4 mini", "Good for most shopping questions", "~\$0.10/1M"),
        AiModelOption("gpt-5.4", "GPT-5.4", "Best reasoning · complex tasks", "~\$1.50/1M"),
        AiModelOption("gpt-5-mini", "GPT-5 mini", "Previous generation · balanced", "~\$0.15/1M"),
    )

private val COUPON_MODELS =
    listOf(
        AiModelOption("gpt-5.4-mini", "GPT-5.4 mini", "Assess coupon applicability", "~\$0.10/1M"),
        AiModelOption("none", "No LLM", "Return raw codes only — no assessment", "Free"),
    )

/**
 * Feature-oriented view of PriceDrop's AI surface. Each feature maps onto the existing
 * [PriceDropProvider] set (the routing source of truth) plus presentation metadata — call
 * estimates and optional model choices — taken from the design mockup.
 *
 * This is an ADDITIONAL config layer; per-provider mode + key in [ProviderSettingsStore] still
 * decides BYOK-vs-Pro routing at request time.
 */
enum class AiFeature(
    val key: String,
    val label: String,
    val description: String,
    val callEstimate: String,
    val callNote: String,
    val callWeight: Int,
    val providers: List<PriceDropProvider>,
    val models: List<AiModelOption>,
) {
    SEARCH(
        key = "search",
        label = "Product search",
        description = "Natural language queries, barcode lookup, URL parsing",
        callEstimate = "1–3 calls per search",
        callNote = "URL parse = 1 web call · Product match = 1 OpenAI call · Optional: 1 shopping call for non-Amazon",
        callWeight = 3,
        providers = listOf(PriceDropProvider.OPENAI, PriceDropProvider.WEB_SEARCH, PriceDropProvider.SHOPPING),
        models = SEARCH_MODELS,
    ),
    PRICE_CHECK(
        key = "pricecheck",
        label = "Price checking",
        description = "Background price polling for watchlist items",
        callEstimate = "1–2 calls per check",
        callNote = "Shopping or web for current price · Keepa for history update (1 token) · No LLM needed",
        callWeight = 2,
        providers = listOf(PriceDropProvider.SHOPPING, PriceDropProvider.KEEPA, PriceDropProvider.WEB_SEARCH, PriceDropProvider.RAINFOREST),
        models = emptyList(),
    ),
    COUPON(
        key = "coupon",
        label = "Coupon discovery",
        description = "Find and assess coupon codes per retailer",
        callEstimate = "1–2 calls per product check",
        callNote = "Coupon lookup (1 req/product) + optional OpenAI to assess which code applies to the item",
        callWeight = 2,
        providers = listOf(PriceDropProvider.COUPON, PriceDropProvider.OPENAI),
        models = COUPON_MODELS,
    ),
    DROPS(
        key = "drops",
        label = "Drop detection",
        description = "Community deal alerts + price threshold crossing",
        callEstimate = "0–2 calls per watchlist check",
        callNote = "Deal feed read (1 call) · OpenAI to match deal to watchlist item (1 call) · Only fires when new deals are found",
        callWeight = 1,
        providers = listOf(PriceDropProvider.WEB_SEARCH, PriceDropProvider.OPENAI),
        models = emptyList(),
    ),
    ASK(
        key = "ask",
        label = "Ask assistant",
        description = "AI shopping assistant — natural language Q&A",
        callEstimate = "2–4 calls per conversation turn",
        callNote = "Search grounding = 1–2 web calls · Answer generation = 1 OpenAI call · Optional: 1 tool call for structured product lookup",
        callWeight = 4,
        providers = listOf(PriceDropProvider.OPENAI, PriceDropProvider.WEB_SEARCH),
        models = ASK_MODELS,
    ),
    ;

    companion object {
        fun fromKey(k: String): AiFeature? = entries.firstOrNull { it.key == k }
    }
}
