package dev.scrybe.core.model

enum class OpenAiProfileSuggestionModel(
    val apiName: String,
    val title: String,
    val supportingText: String,
) {
    GPT_5_MINI(
        apiName = "gpt-5-mini",
        title = "GPT-5 mini",
        supportingText = "Best default for profile drafting when the account has GPT-5 access.",
    ),
    GPT_5_NANO(
        apiName = "gpt-5-nano",
        title = "GPT-5 nano",
        supportingText = "Fastest and lowest-cost GPT-5 option for lightweight profile drafts.",
    ),
    GPT_5(
        apiName = "gpt-5",
        title = "GPT-5",
        supportingText = "Highest quality GPT-5 for complex or multi-stage profile drafts.",
    ),
    GPT_5_1(
        apiName = "gpt-5.1",
        title = "GPT-5.1",
        supportingText = "Incremental upgrade over GPT-5 with better instruction following for profile drafts.",
    ),
    GPT_5_4(
        apiName = "gpt-5.4",
        title = "GPT-5.4",
        supportingText = "Latest flagship model. Best overall quality for demanding profile generation tasks.",
    ),
    GPT_5_4_MINI(
        apiName = "gpt-5.4-mini",
        title = "GPT-5.4 mini",
        supportingText = "Compact GPT-5.4 variant — high quality at lower cost for profile drafts.",
    ),
    GPT_4_1_MINI(
        apiName = "gpt-4.1-mini",
        title = "GPT-4.1 mini",
        supportingText = "Reliable fallback if GPT-5 access is unavailable for the current API key.",
    ),
    GPT_4_1_NANO(
        apiName = "gpt-4.1-nano",
        title = "GPT-4.1 nano",
        supportingText = "Smallest fallback option when you only need a basic first draft.",
    ),
    ;

    companion object {
        val default: OpenAiProfileSuggestionModel = GPT_5_MINI

        fun fromApiName(value: String?): OpenAiProfileSuggestionModel = entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
