package dev.scrybe.core.model

data class ProviderConfig(
    val id: String,
    val providerType: ProviderType,
    val isEnabled: Boolean,
    val modelName: String,
    val apiKeyAlias: String,
)
