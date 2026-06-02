package com.twobits.apikeys

interface ApiKeyProvider {
    suspend fun getApiKey(providerType: ProviderType): String?

    suspend fun setApiKey(providerType: ProviderType, apiKey: String)

    suspend fun clearApiKey(providerType: ProviderType)
}
