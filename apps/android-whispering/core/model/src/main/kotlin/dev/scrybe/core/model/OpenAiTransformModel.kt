package dev.scrybe.core.model

/**
 * Available OpenAI models for running transform profiles. Each entry carries a
 * display title and a supporting text describing the trade-off (quality vs
 * speed/cost). Cost estimates are approximate USD-per-million-tokens values
 * published by OpenAI; they are intended for UI display only.
 */
enum class OpenAiTransformModel(
    val apiName: String,
    val title: String,
    val supportingText: String,
    val inputCostPerMillionUsd: Double,
    val outputCostPerMillionUsd: Double,
) {
    GPT_5_MINI(
        apiName = "gpt-5-mini",
        title = "GPT-5 mini",
        supportingText = "Balanced default for transformations. Fast and inexpensive.",
        inputCostPerMillionUsd = 0.25,
        outputCostPerMillionUsd = 2.00,
    ),
    GPT_5_NANO(
        apiName = "gpt-5-nano",
        title = "GPT-5 nano",
        supportingText = "Lowest-cost GPT-5 variant for short transforms.",
        inputCostPerMillionUsd = 0.10,
        outputCostPerMillionUsd = 0.80,
    ),
    GPT_5(
        apiName = "gpt-5",
        title = "GPT-5",
        supportingText = "Highest quality GPT-5 for complex multi-step transforms.",
        inputCostPerMillionUsd = 1.25,
        outputCostPerMillionUsd = 10.00,
    ),
    GPT_5_1(
        apiName = "gpt-5.1",
        title = "GPT-5.1",
        supportingText = "Incremental upgrade over GPT-5 with better instruction following.",
        inputCostPerMillionUsd = 0.63,
        outputCostPerMillionUsd = 5.00,
    ),
    GPT_5_4(
        apiName = "gpt-5.4",
        title = "GPT-5.4",
        supportingText = "Latest flagship model (March 2026). Best overall quality.",
        inputCostPerMillionUsd = 2.50,
        outputCostPerMillionUsd = 15.00,
    ),
    GPT_5_4_MINI(
        apiName = "gpt-5.4-mini",
        title = "GPT-5.4 mini",
        supportingText = "Compact GPT-5.4 variant with very large context.",
        inputCostPerMillionUsd = 0.75,
        outputCostPerMillionUsd = 4.50,
    ),
    GPT_4_1_MINI(
        apiName = "gpt-4.1-mini",
        title = "GPT-4.1 mini",
        supportingText = "Reliable fallback if GPT-5 access is unavailable.",
        inputCostPerMillionUsd = 0.40,
        outputCostPerMillionUsd = 1.60,
    ),
    GPT_4_1_NANO(
        apiName = "gpt-4.1-nano",
        title = "GPT-4.1 nano",
        supportingText = "Smallest fallback option for simple transforms.",
        inputCostPerMillionUsd = 0.10,
        outputCostPerMillionUsd = 0.40,
    ),
    ;

    /** Formatted blended cost string for display (e.g. "$0.25 / $2.00 per 1M tokens"). */
    val costSummary: String
        get() = "$%.2f / $%.2f per 1M tokens".format(inputCostPerMillionUsd, outputCostPerMillionUsd)

    companion object {
        val default: OpenAiTransformModel = GPT_5_MINI

        fun fromApiName(value: String?): OpenAiTransformModel =
            entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
