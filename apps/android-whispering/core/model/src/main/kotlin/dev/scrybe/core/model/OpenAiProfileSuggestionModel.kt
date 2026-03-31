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

        fun fromApiName(value: String?): OpenAiProfileSuggestionModel =
            entries.firstOrNull { it.apiName.equals(value, ignoreCase = true) } ?: default
    }
}
