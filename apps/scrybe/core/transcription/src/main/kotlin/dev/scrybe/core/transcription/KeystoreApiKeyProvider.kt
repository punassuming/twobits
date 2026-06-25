package dev.scrybe.core.transcription

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twobits.securestore.CredentialCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiKeyDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "api_keys")

@Singleton
class KeystoreApiKeyProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val crypto: CredentialCrypto,
    ) : ApiKeyProvider {
        override suspend fun getApiKey(providerType: ProviderType): String? {
            val raw =
                context.apiKeyDataStore.data.first()[stringPreferencesKey(providerType.name)]
                    ?: return null
            return crypto.tryDecryptOrPassthrough(raw).takeIf { it.isNotBlank() }
        }

        override suspend fun setApiKey(
            providerType: ProviderType,
            apiKey: String,
        ) {
            val encrypted = crypto.encrypt(apiKey)
            context.apiKeyDataStore.edit { prefs -> prefs[stringPreferencesKey(providerType.name)] = encrypted }
        }

        override suspend fun clearApiKey(providerType: ProviderType) {
            context.apiKeyDataStore.edit { prefs -> prefs.remove(stringPreferencesKey(providerType.name)) }
        }
    }
