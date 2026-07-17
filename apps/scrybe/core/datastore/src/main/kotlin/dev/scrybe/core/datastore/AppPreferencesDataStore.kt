package dev.scrybe.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.LocalGemmaModel
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTranscriptionModel
import dev.scrybe.core.model.OpenAiTransformModel
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
            val TRANSCRIPTION_PROVIDER = stringPreferencesKey("transcription_provider")
            val AI_FEATURES_PROVIDER = stringPreferencesKey("ai_features_provider")
            val DEFAULT_TRANSFORM_PROFILE_ID = stringPreferencesKey("default_transform_profile_id")
            val PROFILE_SUGGESTION_MODEL = stringPreferencesKey("profile_suggestion_model")
            val TRANSFORM_MODEL = stringPreferencesKey("transform_model")
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
            val SHOW_RECORDING_INFO_IN_LIST = booleanPreferencesKey("show_recording_info_in_list")
            val POST_STOP_DESTINATION = stringPreferencesKey("post_stop_destination")

            // Legacy key — stored the Long as a String. Kept only as a one-time migration
            // fallback so upgrading users don't see a spurious re-prompt of the What's New
            // popup; see [lastSeenWhatsNewVersionCode]/[setLastSeenWhatsNewVersionCode].
            val LAST_SEEN_WHATS_NEW_VERSION_CODE_LEGACY = stringPreferencesKey("last_seen_whats_new_version_code")
            val WHATS_NEW_LAST_SEEN_VERSION_CODE = longPreferencesKey("whats_new_last_seen_version_code")
            val RECORDING_VIBRATE_ON_START_STOP = booleanPreferencesKey("recording_vibrate_on_start_stop")
            val RECORDING_SOUND_ON_START_STOP = booleanPreferencesKey("recording_sound_on_start_stop")
            val TASKFORGE_ENABLED = booleanPreferencesKey("taskforge_enabled")
            val TASKFORGE_PACKAGE_NAME = stringPreferencesKey("taskforge_package_name")
            val TASKFORGE_ACTION = stringPreferencesKey("taskforge_action")
            val CLOUD_TRANSCRIPTION_MODEL = stringPreferencesKey("cloud_transcription_model")
            val SPOKEN_LANGUAGES = stringPreferencesKey("spoken_languages")
            val LOCAL_GEMMA_MODEL = stringPreferencesKey("local_gemma_model")
            val LOCAL_WHISPER_MODEL = stringPreferencesKey("local_whisper_model")
            val DELETED_DEFAULT_PROFILE_IDS = stringPreferencesKey("deleted_default_profile_ids")
            val ENABLE_SPEAKER_IDENTIFICATION = booleanPreferencesKey("enable_speaker_identification")
            val ENABLE_INSIGHT_ANALYSIS = booleanPreferencesKey("enable_insight_analysis")
            val DEBUG_DIARIZATION = booleanPreferencesKey("debug_diarization")
            val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
            val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
            val LOCATION_RECORDING_ENABLED = booleanPreferencesKey("location_recording_enabled")
            val OBSIDIAN_VAULT_URI = stringPreferencesKey("obsidian_vault_uri")
        }

        val defaultProvider: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.TRANSCRIPTION_PROVIDER] ?: prefs[Keys.DEFAULT_PROVIDER] ?: "OPENAI"
            }

        val transcriptionProvider: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.TRANSCRIPTION_PROVIDER] ?: prefs[Keys.DEFAULT_PROVIDER] ?: "OPENAI"
            }

        // Deliberately does NOT fall back to the legacy DEFAULT_PROVIDER key: that pref predates
        // the transcription/AI-features split and only ever described transcription. Inheriting
        // it here silently routed diarization and insights to the on-device implementations for
        // anyone whose pre-split install had default_provider=LOCAL — with no UI showing it and
        // the local services returning empty results when no on-device model is installed.
        // AI features go local only when the user explicitly picks Local for them.
        val aiFeaturesProvider: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.AI_FEATURES_PROVIDER] ?: "OPENAI"
            }

        val autoTranscribe: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.AUTO_TRANSCRIBE] ?: false
            }

        val defaultTransformProfileId: Flow<String?> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.DEFAULT_TRANSFORM_PROFILE_ID]
            }

        val profileSuggestionModel: Flow<String> =
            context.dataStore.data.map { prefs ->
                OpenAiProfileSuggestionModel
                    .fromApiName(
                        prefs[Keys.PROFILE_SUGGESTION_MODEL],
                    ).apiName
            }

        val transformModel: Flow<String> =
            context.dataStore.data.map { prefs ->
                OpenAiTransformModel
                    .fromApiName(
                        prefs[Keys.TRANSFORM_MODEL],
                    ).apiName
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
                prefs[Keys.SHOW_RENAME_AFTER_RECORDING] ?: false
            }

        val confirmRecordSwipeActions: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.CONFIRM_RECORD_SWIPE_ACTIONS] ?: true
            }

        val showRecordingInfoInList: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.SHOW_RECORDING_INFO_IN_LIST] ?: true
            }

        val postStopDestination: Flow<PostStopDestination> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.POST_STOP_DESTINATION]
                    ?.let { value -> runCatching { PostStopDestination.valueOf(value) }.getOrNull() }
                    ?: PostStopDestination.HOME
            }

        val lastSeenWhatsNewVersionCode: Flow<Long> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.WHATS_NEW_LAST_SEEN_VERSION_CODE]
                    ?: prefs[Keys.LAST_SEEN_WHATS_NEW_VERSION_CODE_LEGACY]?.toLongOrNull()
                    ?: 0L
            }

        val recordingVibrateOnStartStop: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.RECORDING_VIBRATE_ON_START_STOP] ?: true
            }

        val recordingSoundOnStartStop: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.RECORDING_SOUND_ON_START_STOP] ?: false
            }

        val taskForgeEnabled: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.TASKFORGE_ENABLED] ?: false
            }

        val taskForgePackageName: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.TASKFORGE_PACKAGE_NAME] ?: ""
            }

        val taskForgeAction: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.TASKFORGE_ACTION] ?: "android.intent.action.SEND"
            }

        // Comma-separated list of languages the user speaks (free text, e.g. "English, Korean").
        // Fed into transcription prompts so multilingual recordings keep each utterance in its
        // spoken language; blank means no language hint.
        val spokenLanguages: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.SPOKEN_LANGUAGES] ?: ""
            }

        val cloudTranscriptionModel: Flow<OpenAiTranscriptionModel> =
            context.dataStore.data.map { prefs ->
                OpenAiTranscriptionModel.fromApiName(prefs[Keys.CLOUD_TRANSCRIPTION_MODEL] ?: "")
            }

        val localGemmaModel: Flow<LocalGemmaModel> =
            context.dataStore.data.map { prefs ->
                LocalGemmaModel.fromName(prefs[Keys.LOCAL_GEMMA_MODEL] ?: "")
            }

        val localWhisperModel: Flow<LocalWhisperModel> =
            context.dataStore.data.map { prefs ->
                LocalWhisperModel.fromName(prefs[Keys.LOCAL_WHISPER_MODEL] ?: "")
            }

        val enableSpeakerIdentification: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.ENABLE_SPEAKER_IDENTIFICATION] ?: false
            }

        val enableInsightAnalysis: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.ENABLE_INSIGHT_ANALYSIS] ?: false
            }

        val debugDiarization: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.DEBUG_DIARIZATION] ?: false
            }

        val deletedDefaultProfileIds: Flow<Set<String>> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.DELETED_DEFAULT_PROFILE_IDS]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
            }

        val hasSeenOnboarding: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.HAS_SEEN_ONBOARDING] ?: false
            }

        val openAiApiKey: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.OPENAI_API_KEY] ?: ""
            }

        val locationRecordingEnabled: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.LOCATION_RECORDING_ENABLED] ?: false
            }

        val obsidianVaultUri: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.OBSIDIAN_VAULT_URI] ?: ""
            }

        suspend fun setDefaultProvider(provider: String) {
            context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_PROVIDER] = provider }
        }

        suspend fun setTranscriptionProvider(provider: String) {
            context.dataStore.edit { prefs -> prefs[Keys.TRANSCRIPTION_PROVIDER] = provider }
        }

        suspend fun setAiFeaturesProvider(provider: String) {
            context.dataStore.edit { prefs -> prefs[Keys.AI_FEATURES_PROVIDER] = provider }
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

        suspend fun setProfileSuggestionModel(modelName: String) {
            context.dataStore.edit { prefs ->
                prefs[Keys.PROFILE_SUGGESTION_MODEL] =
                    OpenAiProfileSuggestionModel.fromApiName(modelName).apiName
            }
        }

        suspend fun setTransformModel(modelName: String) {
            context.dataStore.edit { prefs ->
                prefs[Keys.TRANSFORM_MODEL] =
                    OpenAiTransformModel.fromApiName(modelName).apiName
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

        suspend fun setShowRecordingInfoInList(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.SHOW_RECORDING_INFO_IN_LIST] = enabled }
        }

        suspend fun setPostStopDestination(destination: PostStopDestination) {
            context.dataStore.edit { prefs -> prefs[Keys.POST_STOP_DESTINATION] = destination.name }
        }

        suspend fun setLastSeenWhatsNewVersionCode(versionCode: Long) {
            context.dataStore.edit { prefs ->
                prefs[Keys.WHATS_NEW_LAST_SEEN_VERSION_CODE] = versionCode
                prefs.remove(Keys.LAST_SEEN_WHATS_NEW_VERSION_CODE_LEGACY)
            }
        }

        suspend fun setRecordingVibrateOnStartStop(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.RECORDING_VIBRATE_ON_START_STOP] = enabled }
        }

        suspend fun setRecordingSoundOnStartStop(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.RECORDING_SOUND_ON_START_STOP] = enabled }
        }

        suspend fun setTaskForgeEnabled(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.TASKFORGE_ENABLED] = enabled }
        }

        suspend fun setTaskForgePackageName(packageName: String) {
            context.dataStore.edit { prefs -> prefs[Keys.TASKFORGE_PACKAGE_NAME] = packageName }
        }

        suspend fun setTaskForgeAction(action: String) {
            context.dataStore.edit { prefs -> prefs[Keys.TASKFORGE_ACTION] = action }
        }

        suspend fun setCloudTranscriptionModel(model: OpenAiTranscriptionModel) {
            context.dataStore.edit { prefs -> prefs[Keys.CLOUD_TRANSCRIPTION_MODEL] = model.apiName }
        }

        suspend fun setSpokenLanguages(languages: String) {
            context.dataStore.edit { prefs -> prefs[Keys.SPOKEN_LANGUAGES] = languages }
        }

        suspend fun setLocalGemmaModel(model: LocalGemmaModel) {
            context.dataStore.edit { prefs -> prefs[Keys.LOCAL_GEMMA_MODEL] = model.name }
        }

        suspend fun setLocalWhisperModel(model: LocalWhisperModel) {
            context.dataStore.edit { prefs -> prefs[Keys.LOCAL_WHISPER_MODEL] = model.name }
        }

        suspend fun setEnableSpeakerIdentification(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.ENABLE_SPEAKER_IDENTIFICATION] = enabled }
        }

        suspend fun setEnableInsightAnalysis(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.ENABLE_INSIGHT_ANALYSIS] = enabled }
        }

        suspend fun setDebugDiarization(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.DEBUG_DIARIZATION] = enabled }
        }

        suspend fun setOnboardingSeen() {
            context.dataStore.edit { prefs -> prefs[Keys.HAS_SEEN_ONBOARDING] = true }
        }

        suspend fun setOpenAiApiKey(key: String) {
            context.dataStore.edit { prefs -> prefs[Keys.OPENAI_API_KEY] = key }
        }

        suspend fun setLocationRecordingEnabled(enabled: Boolean) {
            context.dataStore.edit { prefs -> prefs[Keys.LOCATION_RECORDING_ENABLED] = enabled }
        }

        suspend fun setObsidianVaultUri(uri: String) {
            context.dataStore.edit { prefs -> prefs[Keys.OBSIDIAN_VAULT_URI] = uri }
        }

        suspend fun addDeletedDefaultProfileId(id: String) {
            context.dataStore.edit { prefs ->
                val current =
                    prefs[Keys.DELETED_DEFAULT_PROFILE_IDS]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?.toMutableSet()
                        ?: mutableSetOf()
                current.add(id)
                prefs[Keys.DELETED_DEFAULT_PROFILE_IDS] = current.joinToString(",")
            }
        }
    }
