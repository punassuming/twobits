package com.twobits.apikeys

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiKeyDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "twobits_api_keys")

@Singleton
class KeystoreApiKeyProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ApiKeyProvider {
        override suspend fun getApiKey(providerType: ProviderType): String? {
            val key = stringPreferencesKey(providerType.name)
            return context.apiKeyDataStore.data.first()[key]
        }

        override suspend fun setApiKey(
            providerType: ProviderType,
            apiKey: String,
        ) {
            val key = stringPreferencesKey(providerType.name)
            context.apiKeyDataStore.edit { prefs -> prefs[key] = apiKey }
        }

        override suspend fun clearApiKey(providerType: ProviderType) {
            val key = stringPreferencesKey(providerType.name)
            context.apiKeyDataStore.edit { prefs -> prefs.remove(key) }
        }
    }
