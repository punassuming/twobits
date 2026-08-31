package com.twobits.pricedrop.credentials

import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.securestore.CredentialBridge
import com.twobits.securestore.SharedCredentialId
import javax.inject.Inject

class PriceDropCredentialBridge
    @Inject
    constructor(
        private val providerStore: ProviderSettingsStore,
    ) : CredentialBridge {
        override suspend fun get(id: SharedCredentialId): String? =
            when (id) {
                SharedCredentialId.OPENAI -> providerStore.getKey(PriceDropProvider.OPENAI).takeIf { it.isNotBlank() }
                SharedCredentialId.JINA -> providerStore.getKey(PriceDropProvider.WEB_SEARCH).takeIf { it.isNotBlank() }
                SharedCredentialId.SEARCHAPI -> providerStore.getKey(PriceDropProvider.SHOPPING).takeIf { it.isNotBlank() }
                SharedCredentialId.SERPER -> providerStore.getKey(PriceDropProvider.SERPER).takeIf { it.isNotBlank() }
                SharedCredentialId.COUPON -> null
                SharedCredentialId.RAINFOREST -> providerStore.getKey(PriceDropProvider.RAINFOREST).takeIf { it.isNotBlank() }
                SharedCredentialId.FIRECRAWL -> providerStore.getKey(PriceDropProvider.FIRECRAWL).takeIf { it.isNotBlank() }
                else -> null
            }

        override suspend fun set(
            id: SharedCredentialId,
            value: String,
        ) {
            when (id) {
                SharedCredentialId.OPENAI -> providerStore.setKey(PriceDropProvider.OPENAI, value)
                SharedCredentialId.JINA -> providerStore.setKey(PriceDropProvider.WEB_SEARCH, value)
                SharedCredentialId.SEARCHAPI -> providerStore.setKey(PriceDropProvider.SHOPPING, value)
                SharedCredentialId.SERPER -> providerStore.setKey(PriceDropProvider.SERPER, value)
                SharedCredentialId.COUPON -> Unit
                SharedCredentialId.RAINFOREST -> providerStore.setKey(PriceDropProvider.RAINFOREST, value)
                SharedCredentialId.FIRECRAWL -> providerStore.setKey(PriceDropProvider.FIRECRAWL, value)
                else -> Unit
            }
        }

        override suspend fun clear(id: SharedCredentialId) {
            when (id) {
                SharedCredentialId.OPENAI -> providerStore.clearKey(PriceDropProvider.OPENAI)
                SharedCredentialId.JINA -> providerStore.clearKey(PriceDropProvider.WEB_SEARCH)
                SharedCredentialId.SEARCHAPI -> providerStore.clearKey(PriceDropProvider.SHOPPING)
                SharedCredentialId.SERPER -> providerStore.clearKey(PriceDropProvider.SERPER)
                SharedCredentialId.COUPON -> Unit
                SharedCredentialId.RAINFOREST -> providerStore.clearKey(PriceDropProvider.RAINFOREST)
                SharedCredentialId.FIRECRAWL -> providerStore.clearKey(PriceDropProvider.FIRECRAWL)
                else -> Unit
            }
        }
    }
