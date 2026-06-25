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
            }

        override suspend fun set(
            id: SharedCredentialId,
            value: String,
        ) {
            when (id) {
                SharedCredentialId.OPENAI -> providerStore.setKey(PriceDropProvider.OPENAI, value)
                SharedCredentialId.JINA -> providerStore.setKey(PriceDropProvider.WEB_SEARCH, value)
            }
        }

        override suspend fun clear(id: SharedCredentialId) {
            when (id) {
                SharedCredentialId.OPENAI -> providerStore.clearKey(PriceDropProvider.OPENAI)
                SharedCredentialId.JINA -> providerStore.clearKey(PriceDropProvider.WEB_SEARCH)
            }
        }
    }
