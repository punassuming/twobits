package com.twobits.pricedrop.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.providerStore: DataStore<Preferences>
    by preferencesDataStore(name = "pricedrop_providers")

/**
 * Stores per-provider access mode (Off/BYOK/Pro) and BYOK API keys. Keys live in a dedicated
 * DataStore separate from app settings. Reads/writes are keyed by [PriceDropProvider.key].
 */
@Singleton
class ProviderSettingsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun modeKey(p: PriceDropProvider) = stringPreferencesKey("mode_${p.key}")

        private fun apiKeyKey(p: PriceDropProvider) = stringPreferencesKey("key_${p.key}")

        fun observeMode(p: PriceDropProvider): Flow<ProviderMode> = context.providerStore.data.map { ProviderMode.fromValue(it[modeKey(p)]) }

        suspend fun getMode(p: PriceDropProvider): ProviderMode = ProviderMode.fromValue(context.providerStore.data.first()[modeKey(p)])

        suspend fun setMode(
            p: PriceDropProvider,
            mode: ProviderMode,
        ) {
            context.providerStore.edit { it[modeKey(p)] = mode.value }
        }

        suspend fun getKey(p: PriceDropProvider): String =
            context.providerStore.data
                .first()[apiKeyKey(p)]
                .orEmpty()

        fun observeKey(p: PriceDropProvider): Flow<String> = context.providerStore.data.map { it[apiKeyKey(p)].orEmpty() }

        suspend fun setKey(
            p: PriceDropProvider,
            key: String,
        ) {
            context.providerStore.edit { it[apiKeyKey(p)] = key }
        }

        suspend fun clearKey(p: PriceDropProvider) {
            context.providerStore.edit { it.remove(apiKeyKey(p)) }
        }
    }
