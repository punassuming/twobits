package com.twobits.pricedrop.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twobits.securestore.CredentialCrypto
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
        private val crypto: CredentialCrypto,
    ) {
        private val couponProviderSchemaKey = stringPreferencesKey("schema_coupon_provider")

        private fun modeKey(p: PriceDropProvider) = stringPreferencesKey("mode_${p.key}")

        private fun apiKeyKey(p: PriceDropProvider) = stringPreferencesKey("key_${p.key}")

        // Whether the stored key last passed live validation — persisted so the
        // credential row shows "Connected" on launch without re-testing.
        private fun validatedKey(p: PriceDropProvider) = booleanPreferencesKey("valid_${p.key}")

        fun observeMode(p: PriceDropProvider): Flow<ProviderMode> = context.providerStore.data.map { ProviderMode.fromValue(it[modeKey(p)]) }

        suspend fun getMode(p: PriceDropProvider): ProviderMode = ProviderMode.fromValue(context.providerStore.data.first()[modeKey(p)])

        suspend fun setMode(
            p: PriceDropProvider,
            mode: ProviderMode,
        ) {
            context.providerStore.edit { it[modeKey(p)] = mode.value }
        }

        suspend fun getKey(p: PriceDropProvider): String {
            val raw = context.providerStore.data.first()[apiKeyKey(p)] ?: return ""
            return crypto.tryDecryptOrPassthrough(raw)
        }

        fun observeKey(p: PriceDropProvider): Flow<String> =
            context.providerStore.data.map { prefs ->
                val raw = prefs[apiKeyKey(p)] ?: ""
                crypto.tryDecryptOrPassthrough(raw)
            }

        suspend fun setKey(
            p: PriceDropProvider,
            key: String,
        ) {
            context.providerStore.edit { it[apiKeyKey(p)] = crypto.encrypt(key) }
        }

        suspend fun clearKey(p: PriceDropProvider) {
            context.providerStore.edit {
                it.remove(apiKeyKey(p))
                it.remove(validatedKey(p))
            }
        }

        fun observeValidated(p: PriceDropProvider): Flow<Boolean> = context.providerStore.data.map { it[validatedKey(p)] ?: false }

        suspend fun setValidated(
            p: PriceDropProvider,
            valid: Boolean,
        ) {
            context.providerStore.edit { it[validatedKey(p)] = valid }
        }

        /** Clears legacy coupon-provider routing. Promotions now come from offers or manual codes. */
        suspend fun migrateCouponProvider(): Boolean {
            var migrated = false
            context.providerStore.edit { prefs ->
                if (prefs[couponProviderSchemaKey] == COUPON_PROVIDER_SCHEMA) return@edit
                prefs.remove(stringPreferencesKey("key_coupon"))
                prefs.remove(booleanPreferencesKey("valid_coupon"))
                prefs[stringPreferencesKey("mode_coupon")] = ProviderMode.OFF.value
                prefs[stringPreferencesKey("feature_source_coupon")] = ProviderMode.OFF.value
                prefs[couponProviderSchemaKey] = COUPON_PROVIDER_SCHEMA
                migrated = true
            }
            return migrated
        }

        // ── Feature-level config (presentation layer; routing still uses per-provider mode/key) ──

        private fun featureSourceKey(f: AiFeature) = stringPreferencesKey("feature_source_${f.key}")

        private fun featureModelKey(f: AiFeature) = stringPreferencesKey("feature_model_${f.key}")

        private fun featureProvidersKey(f: AiFeature) = stringPreferencesKey("feature_providers_${f.key}")

        /**
         * Features with exactly one real provider that already does its own Off/BYOK/Pro
         * routing (PriceDropApiClient branches directly on this provider's mode) have no
         * independent "feature source" to track — the AI Config screen's Source picker for
         * these features IS that provider's mode, not a second, disconnected preference that
         * would need its own reconciliation. SEARCH multiplexes several providers behind one
         * Pro-shortcut flag (see ProviderRegistry) and keeps its own stored value.
         */
        private fun primaryGatingProvider(f: AiFeature): PriceDropProvider? =
            when (f) {
                AiFeature.PRICE_CHECK -> PriceDropProvider.RAINFOREST
                AiFeature.ASK -> PriceDropProvider.OPENAI
                else -> null
            }

        fun observeFeatureSource(f: AiFeature): Flow<ProviderMode> =
            primaryGatingProvider(f)?.let { observeMode(it) }
                ?: context.providerStore.data.map { prefs ->
                    prefs[featureSourceKey(f)]?.let { ProviderMode.fromValue(it) } ?: ProviderMode.BYOK
                }

        suspend fun getFeatureSource(f: AiFeature): ProviderMode =
            primaryGatingProvider(f)?.let { getMode(it) }
                ?: context.providerStore.data
                    .first()[featureSourceKey(f)]
                    ?.let { ProviderMode.fromValue(it) } ?: ProviderMode.BYOK

        suspend fun setFeatureSource(
            f: AiFeature,
            mode: ProviderMode,
        ) {
            val primary = primaryGatingProvider(f)
            if (primary != null) {
                setMode(primary, mode)
            } else {
                context.providerStore.edit { it[featureSourceKey(f)] = mode.value }
            }
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

        suspend fun getFeatureProviders(f: AiFeature): Set<String> {
            val stored = context.providerStore.data.first()[featureProvidersKey(f)]
            return stored?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: defaultFeatureProviders(f)
        }

        suspend fun setFeatureProviders(
            f: AiFeature,
            providerKeys: Set<String>,
        ) {
            context.providerStore.edit { it[featureProvidersKey(f)] = providerKeys.joinToString(",") }
        }

        private companion object {
            const val COUPON_PROVIDER_SCHEMA = "embedded_promotions_v2"
        }
    }
