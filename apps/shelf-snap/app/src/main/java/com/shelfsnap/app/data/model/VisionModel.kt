package com.shelfsnap.app.data.model

enum class VisionModel(
    val apiName: String,
    val displayName: String,
    val supportingText: String,
) {
    GPT_4O(
        apiName = "gpt-4o",
        displayName = "GPT-4o",
        supportingText = "Best accuracy · $2.50 / $10.00 per 1M tokens",
    ),
    GPT_4O_MINI(
        apiName = "gpt-4o-mini",
        displayName = "GPT-4o mini",
        supportingText = "Fast and affordable · $0.15 / $0.60 per 1M tokens",
    ),
    GPT_4_1_MINI(
        apiName = "gpt-4.1-mini",
        displayName = "GPT-4.1 mini",
        supportingText = "Economical with solid vision · $0.40 / $1.60 per 1M tokens",
    );

    companion object {
        val default: VisionModel = GPT_4O
        fun fromApiName(value: String?): VisionModel =
            entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
