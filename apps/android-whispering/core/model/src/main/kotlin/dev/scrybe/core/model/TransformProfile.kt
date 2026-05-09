package dev.scrybe.core.model

data class TransformProfile(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val steps: List<String>,
    val providerType: ProviderType,
    val isDefault: Boolean,
    val modelName: String? = null,
)
