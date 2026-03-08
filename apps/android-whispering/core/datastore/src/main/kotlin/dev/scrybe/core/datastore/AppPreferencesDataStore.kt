package dev.scrybe.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scrybe_prefs")

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_TRANSFORM_PROFILE_ID = stringPreferencesKey("default_transform_profile_id")
        val AUTO_TRANSCRIBE = booleanPreferencesKey("auto_transcribe")
        val MAX_RECORDING_DURATION_MS = stringPreferencesKey("max_recording_duration_ms")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
    }

    val defaultProvider: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_PROVIDER] ?: "OPENAI"
    }

    val autoTranscribe: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_TRANSCRIBE] ?: false
    }

    val defaultTransformProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_TRANSFORM_PROFILE_ID]
    }

    suspend fun setDefaultProvider(provider: String) {
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_PROVIDER] = provider }
    }

    suspend fun setAutoTranscribe(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_TRANSCRIBE] = enabled }
    }

    suspend fun setDefaultTransformProfileId(profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId != null) {
                prefs[Keys.DEFAULT_TRANSFORM_PROFILE_ID] = profileId
            } else {
                prefs.remove(Keys.DEFAULT_TRANSFORM_PROFILE_ID)
            }
        }
    }
}
