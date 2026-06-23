package dev.scrybe.core.transforms

data class ProfileSuggestion(
    val name: String,
    val description: String,
    val steps: List<String>,
    val tokensUsed: Int = 0,
)
