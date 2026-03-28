package dev.scrybe.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scrybe_prefs")

@Singleton
class AppPreferencesDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
            val DEFAULT_TRANSFORM_PROFILE_ID = stringPreferencesKey("default_transform_profile_id")
            val AUTO_TRANSCRIBE = booleanPreferencesKey("auto_transcribe")
            val MAX_RECORDING_DURATION_MS = stringPreferencesKey("max_recording_duration_ms")
            val AUDIO_FORMAT = stringPreferencesKey("audio_format")
            val SAMPLE_RATE_HZ = intPreferencesKey("sample_rate_hz")
            val ENCODING_BIT_RATE = intPreferencesKey("encoding_bit_rate")
            val CHANNEL_COUNT = intPreferencesKey("channel_count")
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
            val SHOW_RENAME_AFTER_RECORDING = booleanPreferencesKey("show_rename_after_recording")
            val CONFIRM_RECORD_SWIPE_ACTIONS = booleanPreferencesKey("confirm_record_swipe_actions")
            val POST_STOP_DESTINATION = stringPreferencesKey("post_stop_destination")
            val LAST_SEEN_WHATS_NEW_VERSION_CODE = stringPreferencesKey("last_seen_whats_new_version_code")
        }

        val defaultProvider: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.DEFAULT_PROVIDER] ?: "OPENAI"
            }

        val autoTranscribe: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.AUTO_TRANSCRIBE] ?: false
            }

        val defaultTransformProfileId: Flow<String?> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.DEFAULT_TRANSFORM_PROFILE_ID]
            }

        val audioFormat: Flow<AudioFormat> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.AUDIO_FORMAT]
                    ?.let { value -> runCatching { AudioFormat.valueOf(value) }.getOrNull() }
                    ?: AudioFormat.AAC
            }

        val sampleRateHz: Flow<Int> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.SAMPLE_RATE_HZ] ?: 48_000
            }

        val encodingBitRate: Flow<Int> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.ENCODING_BIT_RATE] ?: 128_000
            }

        val channelCount: Flow<Int> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.CHANNEL_COUNT] ?: 1
            }

        val themeMode: Flow<ThemeMode> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.THEME_MODE]
                    ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
            }

        val keepScreenOn: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.KEEP_SCREEN_ON] ?: true
            }

        val showRenameAfterRecording: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.SHOW_RENAME_AFTER_RECORDING] ?: true
            }

        val confirmRecordSwipeActions: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.CONFIRM_RECORD_SWIPE_ACTIONS] ?: true
            }

        val postStopDestination: Flow<PostStopDestination> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.POST_STOP_DESTINATION]
                    ?.let { value -> runCatching { PostStopDestination.valueOf(value) }.getOrNull() }
                    ?: PostStopDestination.HOME
            }

        val lastSeenWhatsNewVersionCode: Flow<Long> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.LAST_SEEN_WHATS_NEW_VERSION_CODE]?.toLongOrNull() ?: 0L
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

        suspend fun setAudioFormat(audioFormat: AudioFormat) {
            context.dataStore.edit { prefs -> prefs[Keys.AUDIO_FORMAT] = audioFormat.name }
        }

        suspend fun setSampleRateHz(sampleRateHz: Int) {
            context.dataStore.edit { prefs -> prefs[Keys.SAMPLE_RATE_HZ] = sampleRateHz }
        }

        suspend fun setEncodingBitRate(bitRate: Int) {
            context.dataStore.edit { prefs -> prefs[Keys.ENCODING_BIT_RATE] = bitRate }
        }

        suspend fun setChannelCount(channelCount: Int) {
            context.dataStore.edit { prefs -> prefs[Keys.CHANNEL_COUNT] = channelCount }
        }

        suspend fun setThemeMode(themeMode: ThemeMode) {
            context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = themeMode.name }
        }

        suspend fun setKeepScreenOn(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.KEEP_SCREEN_ON] = enabled }
        }

        suspend fun setShowRenameAfterRecording(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.SHOW_RENAME_AFTER_RECORDING] = enabled }
        }

        suspend fun setConfirmRecordSwipeActions(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.CONFIRM_RECORD_SWIPE_ACTIONS] = enabled }
        }

        suspend fun setPostStopDestination(destination: PostStopDestination) {
            context.dataStore.edit { prefs -> prefs[Keys.POST_STOP_DESTINATION] = destination.name }
        }

        suspend fun setLastSeenWhatsNewVersionCode(versionCode: Long) {
            context.dataStore.edit { prefs ->
                prefs[Keys.LAST_SEEN_WHATS_NEW_VERSION_CODE] = versionCode.toString()
            }
        }
    }
