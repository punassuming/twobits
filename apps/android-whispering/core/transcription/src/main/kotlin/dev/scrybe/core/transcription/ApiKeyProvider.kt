package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType

interface ApiKeyProvider {
    suspend fun getApiKey(providerType: ProviderType): String?

    suspend fun setApiKey(
        providerType: ProviderType,
        apiKey: String,
    )

    suspend fun clearApiKey(providerType: ProviderType)
}
