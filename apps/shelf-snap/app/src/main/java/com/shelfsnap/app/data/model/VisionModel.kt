package com.shelfsnap.app.data.model

enum class VisionModel(
    val apiName: String,
    val displayName: String,
    val supportingText: String,
    val costLabel: String,
) {
    GPT_5(
        apiName = "gpt-5",
        displayName = "GPT-5",
        supportingText = "Best accuracy",
        costLabel = "$1.25 / $10.00",
    ),
    GPT_5_MINI(
        apiName = "gpt-5-mini",
        displayName = "GPT-5 mini",
        supportingText = "Fast and affordable",
        costLabel = "$0.25 / $2.00",
    ),
    GPT_5_4(
        apiName = "gpt-5.4",
        displayName = "GPT-5.4",
        supportingText = "Frontier vision",
        costLabel = "$2.50 / $15.00",
    ),
    GPT_5_4_MINI(
        apiName = "gpt-5.4-mini",
        displayName = "GPT-5.4 mini",
        supportingText = "Newest mini, strong vision",
        costLabel = "$0.75 / $4.50",
    ),
    ;

    companion object {
        val default: VisionModel = GPT_5

        fun fromApiName(value: String?): VisionModel = entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
