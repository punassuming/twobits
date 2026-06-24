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

        // ── Feature-level config (presentation layer; routing still uses per-provider mode/key) ──

        private fun featureSourceKey(f: AiFeature) = stringPreferencesKey("feature_source_${f.key}")

        private fun featureModelKey(f: AiFeature) = stringPreferencesKey("feature_model_${f.key}")

        private fun featureProvidersKey(f: AiFeature) = stringPreferencesKey("feature_providers_${f.key}")

        fun observeFeatureSource(f: AiFeature): Flow<ProviderMode> =
            context.providerStore.data.map { prefs ->
                prefs[featureSourceKey(f)]?.let { ProviderMode.fromValue(it) } ?: ProviderMode.BYOK
            }

        suspend fun getFeatureSource(f: AiFeature): ProviderMode =
            context.providerStore.data
                .first()[featureSourceKey(f)]
                ?.let { ProviderMode.fromValue(it) } ?: ProviderMode.BYOK

        suspend fun setFeatureSource(
            f: AiFeature,
            mode: ProviderMode,
        ) {
            context.providerStore.edit { it[featureSourceKey(f)] = mode.value }
        }

        fun observeFeatureModel(f: AiFeature): Flow<String> = context.providerStore.data.map { it[featureModelKey(f)].orEmpty() }

        suspend fun getFeatureModel(f: AiFeature): String =
            context.providerStore.data
                .first()[featureModelKey(f)]
                .orEmpty()

        suspend fun setFeatureModel(
            f: AiFeature,
            modelId: String,
        ) {
            context.providerStore.edit { it[featureModelKey(f)] = modelId }
        }

        private fun defaultFeatureProviders(f: AiFeature): Set<String> = f.providers.map { it.key }.toSet()

        fun observeFeatureProviders(f: AiFeature): Flow<Set<String>> =
            context.providerStore.data.map { prefs ->
                val stored = prefs[featureProvidersKey(f)]
                if (stored == null) {
                    defaultFeatureProviders(f)
                } else {
                    stored.split(',').filter { it.isNotBlank() }.toSet()
                }
            }

        suspend fun setFeatureProviders(
            f: AiFeature,
            providerKeys: Set<String>,
        ) {
            context.providerStore.edit { it[featureProvidersKey(f)] = providerKeys.joinToString(",") }
        }
    }
