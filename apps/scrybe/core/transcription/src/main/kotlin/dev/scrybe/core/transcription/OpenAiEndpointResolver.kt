package dev.scrybe.core.transcription

import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Where an OpenAI request should go and how it should authenticate. */
data class OpenAiEndpoint(
    val baseUrl: String,
    val authToken: String,
)

/**
 * Single source of truth for routing Scrybe's OpenAI calls.
 *
 * - **Pro**: requests go through the TwoBits managed proxy ([PRO_BASE_URL]); the bearer
 *   token is the RevenueCat app user id and the user's own OpenAI key is never sent.
 * - **Free / BYOK**: requests go directly to [OPENAI_BASE_URL] with the user's stored key.
 *
 * Every OpenAI-calling service injects this instead of hard-coding the base URL and key,
 * so the Pro/BYOK decision lives in exactly one place.
 */
@Singleton
class OpenAiEndpointResolver
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProvider,
        private val subscriptionRepository: SubscriptionRepository,
    ) {
        suspend fun resolve(): OpenAiEndpoint {
            val tier = subscriptionRepository.subscriptionTier.first()
            return if (tier is SubscriptionTier.Pro) {
                OpenAiEndpoint(baseUrl = PRO_BASE_URL, authToken = subscriptionRepository.getAppUserId())
            } else {
                val key =
                    apiKeyProvider.getApiKey(ProviderType.OPENAI)
                        ?: throw IllegalStateException("No API key configured for OpenAI")
                OpenAiEndpoint(baseUrl = OPENAI_BASE_URL, authToken = key)
            }
        }

        companion object {
            const val PRO_BASE_URL = "https://api.twobits.app"
            const val OPENAI_BASE_URL = "https://api.openai.com"
        }
    }
