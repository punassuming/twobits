package com.shelfsnap.app.data.model

enum class ReasoningModel(
    val apiName: String,
    val displayName: String,
    val supportingText: String,
) {
    GPT_4O_MINI(
        apiName = "gpt-4o-mini",
        displayName = "GPT-4o mini",
        supportingText = "Fast & affordable · \$0.15 / \$0.60 per 1M tokens",
    ),
    GPT_4_1_MINI(
        apiName = "gpt-4.1-mini",
        displayName = "GPT-4.1 mini",
        supportingText = "Solid reasoning, economical · \$0.40 / \$1.60 per 1M tokens",
    ),
    GPT_4O(
        apiName = "gpt-4o",
        displayName = "GPT-4o",
        supportingText = "Best accuracy · \$2.50 / \$10.00 per 1M tokens",
    ),
    GPT_5_4_MINI(
        apiName = "gpt-5.4-mini",
        displayName = "GPT-5.4 mini",
        supportingText = "High quality at lower cost · \$0.75 / \$4.50 per 1M tokens",
    );

    companion object {
        val default: ReasoningModel = GPT_4O_MINI
        fun fromApiName(value: String?): ReasoningModel =
            entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
