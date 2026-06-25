package dev.scrybe.core.transcription

import com.twobits.securestore.CredentialBridge
import com.twobits.securestore.SharedCredentialId
import dev.scrybe.core.model.ProviderType
import javax.inject.Inject

class ScrybeCredentialBridge
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProvider,
    ) : CredentialBridge {
        override suspend fun get(id: SharedCredentialId): String? =
            when (id) {
                SharedCredentialId.OPENAI -> apiKeyProvider.getApiKey(ProviderType.OPENAI)
                else -> null
            }

        override suspend fun set(
            id: SharedCredentialId,
            value: String,
        ) {
            when (id) {
                SharedCredentialId.OPENAI -> apiKeyProvider.setApiKey(ProviderType.OPENAI, value)
                else -> Unit
            }
        }

        override suspend fun clear(id: SharedCredentialId) {
            when (id) {
                SharedCredentialId.OPENAI -> apiKeyProvider.clearApiKey(ProviderType.OPENAI)
                else -> Unit
            }
        }
    }
