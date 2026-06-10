package com.shelfsnap.app.data.model

enum class ReasoningModel(
    val apiName: String,
    val displayName: String,
    val supportingText: String,
    val costLabel: String,
) {
    GPT_5_MINI(
        apiName = "gpt-5-mini",
        displayName = "GPT-5 mini",
        supportingText = "Fast and affordable",
        costLabel = "\$0.25 / \$2.00",
    ),
    GPT_5_4_NANO(
        apiName = "gpt-5.4-nano",
        displayName = "GPT-5.4 nano",
        supportingText = "Cheapest",
        costLabel = "\$0.20 / \$1.25",
    ),
    GPT_5_4_MINI(
        apiName = "gpt-5.4-mini",
        displayName = "GPT-5.4 mini",
        supportingText = "Newest mini",
        costLabel = "\$0.75 / \$4.50",
    ),
    GPT_5(
        apiName = "gpt-5",
        displayName = "GPT-5",
        supportingText = "Best accuracy",
        costLabel = "\$1.25 / \$10.00",
    ),
    ;

    companion object {
        val default: ReasoningModel = GPT_5_MINI

        fun fromApiName(value: String?): ReasoningModel = entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
