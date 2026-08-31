package com.shelfsnap.app.credentials

import com.shelfsnap.app.data.repository.ItemRepository
import com.twobits.securestore.CredentialBridge
import com.twobits.securestore.SharedCredentialId
import javax.inject.Inject

class ShelfSnapCredentialBridge
    @Inject
    constructor(
        private val repository: ItemRepository,
    ) : CredentialBridge {
        override suspend fun get(id: SharedCredentialId): String? =
            when (id) {
                SharedCredentialId.OPENAI -> repository.getApiKey().takeIf { it.isNotBlank() }
                SharedCredentialId.JINA -> repository.getJinaApiKey().takeIf { it.isNotBlank() }
                SharedCredentialId.BRAVE -> repository.getBraveApiKey().takeIf { it.isNotBlank() }
                SharedCredentialId.SEARCHAPI -> repository.getSearchapiApiKey().takeIf { it.isNotBlank() }
                SharedCredentialId.SERPER -> repository.getSerperApiKey().takeIf { it.isNotBlank() }
                SharedCredentialId.FIRECRAWL -> repository.getFirecrawlApiKey().takeIf { it.isNotBlank() }
                else -> null
            }

        override suspend fun set(
            id: SharedCredentialId,
            value: String,
        ) {
            when (id) {
                SharedCredentialId.OPENAI -> repository.saveApiKey(value)
                SharedCredentialId.JINA -> repository.saveJinaApiKey(value)
                SharedCredentialId.BRAVE -> repository.saveBraveApiKey(value)
                SharedCredentialId.SEARCHAPI -> repository.saveSearchapiApiKey(value)
                SharedCredentialId.SERPER -> repository.saveSerperApiKey(value)
                SharedCredentialId.FIRECRAWL -> repository.saveFirecrawlApiKey(value)
                else -> Unit
            }
        }

        override suspend fun clear(id: SharedCredentialId) {
            when (id) {
                SharedCredentialId.OPENAI -> repository.saveApiKey("")
                SharedCredentialId.JINA -> repository.saveJinaApiKey("")
                SharedCredentialId.BRAVE -> repository.saveBraveApiKey("")
                SharedCredentialId.SEARCHAPI -> repository.saveSearchapiApiKey("")
                SharedCredentialId.SERPER -> repository.saveSerperApiKey("")
                SharedCredentialId.FIRECRAWL -> repository.saveFirecrawlApiKey("")
                else -> Unit
            }
        }
    }
