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
        description = "Aggregated shopping offers with explainable product matching",
        callEstimate = "1 call per enabled provider · 2 calls for URL paste",
        callNote =
            "Keyword search queries every enabled shopping provider · Paste a product URL = " +
                "1 web read + 1 OpenAI call to extract the product",
        callWeight = 3,
        providers = listOf(PriceDropProvider.OPENAI, PriceDropProvider.WEB_SEARCH, PriceDropProvider.SHOPPING, PriceDropProvider.SERPER),
        models = SEARCH_MODELS,
    ),
    PRICE_CHECK(
        key = "pricecheck",
        label = "Price checking",
        description = "Background price polling for watchlist items",
        callEstimate = "1 call per check",
        callNote =
            "Rainforest (BYOK) or Pro for current price, 1 call per item per check · price history " +
                "is backfilled from Rainforest once when a product is added · no LLM involved",
        callWeight = 2,
        providers = listOf(PriceDropProvider.RAINFOREST),
        models = emptyList(),
    ),
    COUPON(
        key = "coupon",
        label = "Promotions",
        description = "Offer promotions and manually entered coupon codes",
        callEstimate = "0 dedicated calls",
        callNote = "Uses provider offer metadata; broad coupon aggregation remains experimental",
        callWeight = 1,
        providers = emptyList(),
        models = emptyList(),
    ),
    DROPS(
        key = "drops",
        label = "Drop detection",
        description = "Price threshold and drop alerts computed from your own tracked history",
        callEstimate = "0 calls",
        callNote = "Computed entirely on-device from already-polled price history — no network calls, no provider needed",
        callWeight = 1,
        providers = emptyList(),
        models = emptyList(),
    ),
    ASK(
        key = "ask",
        label = "Ask assistant",
        description = "AI shopping assistant — natural language Q&A",
        callEstimate = "1–2 calls per conversation turn",
        callNote =
            "Answer generation = 1 OpenAI call · optional grounding = 1 Jina search, but only when " +
                "Web search is set to BYOK — Pro-tier Ask does not ground its answers in live search results",
        callWeight = 2,
        providers = listOf(PriceDropProvider.OPENAI, PriceDropProvider.WEB_SEARCH),
        models = ASK_MODELS,
    ),
    ;

    companion object {
        fun fromKey(k: String): AiFeature? = entries.firstOrNull { it.key == k }
    }
}
