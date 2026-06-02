package com.twobits.apikeys

import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ApiKeyRouter
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProvider,
        private val subscriptionRepository: SubscriptionRepository,
        private val proUserIdProvider: ProUserIdProvider,
    ) {
        suspend fun resolveApiConfig(providerType: ProviderType): ApiConfig {
            val tier = subscriptionRepository.subscriptionTier.first()
            return when (tier) {
                is SubscriptionTier.Pro ->
                    ApiConfig(
                        baseUrl = "https://api.twobits.app/",
                        authToken = proUserIdProvider.getUserId(),
                    )
                is SubscriptionTier.Free -> {
                    val key = apiKeyProvider.getApiKey(providerType)
                        ?: throw NoApiKeyException(
                            "No API key stored. Please add your OpenAI key in Settings.",
                        )
                    ApiConfig(
                        baseUrl = "https://api.openai.com/",
                        authToken = key,
                    )
                }
            }
        }
    }
