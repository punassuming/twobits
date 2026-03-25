package dev.scrybe.feature.settings

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.common.ReleaseNotes
import dev.scrybe.core.common.ReleaseNotesParser
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.ThemeMode
import dev.scrybe.core.transcription.ApiKeyProvider
import dev.scrybe.core.transcription.OpenAiApiKeyValidator
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val defaultProvider: String = "OPENAI",
    val autoTranscribe: Boolean = false,
    val defaultTransformProfileId: String? = null,
    val defaultTransformProfileName: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val showRenameAfterRecording: Boolean = true,
    val postStopDestination: PostStopDestination = PostStopDestination.HOME,
    val audioFormat: AudioFormat = AudioFormat.AAC,
    val sampleRateHz: Int = 48_000,
    val encodingBitRate: Int = 128_000,
    val channelCount: Int = 1,
    val apiKey: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val latestReleaseTitle: String? = null,
    val releaseNotes: List<String> = emptyList(),
    val releaseHistory: List<ReleaseNotes> = emptyList(),
    val savedFiles: List<SavedFileEntry> = emptyList(),
    val usageStats: UsageStats = UsageStats(),
    val apiKeyValidationStatus: ApiKeyValidationStatus = ApiKeyValidationStatus.Unknown,
    val apiKeyValidationMessage: String? = null,
)

data class SavedFileEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val category: String,
)

data class UsageStats(
    val recordCount: Int = 0,
    val transcriptionCount: Int = 0,
    val transformCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val totalEstimatedCostUsd: Double = 0.0,
)

enum class ApiKeyValidationStatus {
    Unknown,
    Validating,
    Valid,
    Invalid,
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataStore: AppPreferencesDataStore,
    recordingSessionDao: RecordingSessionDao,
    transcriptDao: TranscriptDao,
    transformRunDao: TransformRunDao,
    transformProfileDao: TransformProfileDao,
    private val apiKeyProvider: ApiKeyProvider,
    private val apiKeyValidator: OpenAiApiKeyValidator,
) : ViewModel() {

    private val apiKey = MutableStateFlow("")
    private val appMetadata = MutableStateFlow(AppMetadata())
    private val savedFiles = MutableStateFlow<List<SavedFileEntry>>(emptyList())
    private val apiKeyValidationStatus = MutableStateFlow(ApiKeyValidationStatus.Unknown)
    private val apiKeyValidationMessage = MutableStateFlow<String?>(null)
    private val localMetadata = combine(apiKey, appMetadata) { currentApiKey, metadata ->
        LocalMetadata(
            apiKey = currentApiKey,
            appMetadata = metadata,
        )
    }
    private val apiKeyValidation = combine(
        apiKeyValidationStatus,
        apiKeyValidationMessage,
    ) { status, message ->
        ApiKeyValidation(
            status = status,
            message = message,
        )
    }
    private val profileSettings = combine(
        preferencesDataStore.defaultProvider,
        preferencesDataStore.autoTranscribe,
        preferencesDataStore.defaultTransformProfileId,
        transformProfileDao.getAllProfiles(),
    ) { provider, autoTranscribe, profileId, profiles ->
        ProfileSettings(
            defaultProvider = provider,
            autoTranscribe = autoTranscribe,
            defaultTransformProfileId = profileId,
            defaultTransformProfileName = profiles.firstOrNull { it.id == profileId }?.name,
        )
    }
    private val displayPreferences = combine(
        preferencesDataStore.themeMode,
        preferencesDataStore.keepScreenOn,
        preferencesDataStore.showRenameAfterRecording,
        preferencesDataStore.postStopDestination,
    ) { themeMode, keepScreenOn, showRenameAfterRecording, postStopDestination ->
        DisplayPreferences(
            themeMode = themeMode,
            keepScreenOn = keepScreenOn,
            showRenameAfterRecording = showRenameAfterRecording,
            postStopDestination = postStopDestination,
        )
    }
    private val audioPreferences = combine(
        preferencesDataStore.audioFormat,
        preferencesDataStore.sampleRateHz,
        preferencesDataStore.encodingBitRate,
        preferencesDataStore.channelCount,
    ) { audioFormat, sampleRateHz, encodingBitRate, channelCount ->
        AudioPreferences(
            audioFormat = audioFormat,
            sampleRateHz = sampleRateHz,
            encodingBitRate = encodingBitRate,
            channelCount = channelCount,
        )
    }
    private val recordingPreferences = combine(
        displayPreferences,
        audioPreferences,
    ) { displayPreferences, audioPreferences ->
        RecordingPreferences(
            themeMode = displayPreferences.themeMode,
            keepScreenOn = displayPreferences.keepScreenOn,
            showRenameAfterRecording = displayPreferences.showRenameAfterRecording,
            postStopDestination = displayPreferences.postStopDestination,
            audioFormat = audioPreferences.audioFormat,
            sampleRateHz = audioPreferences.sampleRateHz,
            encodingBitRate = audioPreferences.encodingBitRate,
            channelCount = audioPreferences.channelCount,
        )
    }
    private val usageData = combine(
        recordingSessionDao.getAllSessions(),
        transcriptDao.getAllTranscripts(),
        transformRunDao.getAllRuns(),
        savedFiles,
    ) { sessions, transcripts, runs, savedFiles ->
        UsageData(
            savedFiles = savedFiles,
            usageStats = UsageStats(
                recordCount = sessions.size,
                transcriptionCount = transcripts.count { it.type == "RAW" || it.type == "EDITED" },
                transformCount = runs.size,
                totalDurationMs = sessions.sumOf { it.durationMs },
                totalStorageBytes = sessions.sumOf { it.fileSizeBytes },
                totalEstimatedCostUsd = sessions.sumOf { it.estimatedTranscriptionCostUsd ?: 0.0 },
            ),
        )
    }
    private val settingsData = combine(
        profileSettings,
        recordingPreferences,
        localMetadata,
        usageData,
    ) { profileSettings, recordingPreferences, metadata, usageData ->
        SettingsData(
            defaultProvider = profileSettings.defaultProvider,
            autoTranscribe = profileSettings.autoTranscribe,
            defaultTransformProfileId = profileSettings.defaultTransformProfileId,
            defaultTransformProfileName = profileSettings.defaultTransformProfileName,
            themeMode = recordingPreferences.themeMode,
            keepScreenOn = recordingPreferences.keepScreenOn,
            showRenameAfterRecording = recordingPreferences.showRenameAfterRecording,
            postStopDestination = recordingPreferences.postStopDestination,
            audioFormat = recordingPreferences.audioFormat,
            sampleRateHz = recordingPreferences.sampleRateHz,
            encodingBitRate = recordingPreferences.encodingBitRate,
            channelCount = recordingPreferences.channelCount,
            apiKey = metadata.apiKey,
            versionName = metadata.appMetadata.versionName,
            versionCode = metadata.appMetadata.versionCode,
            latestReleaseTitle = metadata.appMetadata.latestReleaseTitle,
            releaseNotes = metadata.appMetadata.releaseNotes,
            releaseHistory = metadata.appMetadata.releaseHistory,
            savedFiles = usageData.savedFiles,
            usageStats = usageData.usageStats,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsData,
        apiKeyValidation,
    ) { settingsData, validation ->
        SettingsUiState(
            defaultProvider = settingsData.defaultProvider,
            autoTranscribe = settingsData.autoTranscribe,
            defaultTransformProfileId = settingsData.defaultTransformProfileId,
            defaultTransformProfileName = settingsData.defaultTransformProfileName,
            themeMode = settingsData.themeMode,
            keepScreenOn = settingsData.keepScreenOn,
            showRenameAfterRecording = settingsData.showRenameAfterRecording,
            postStopDestination = settingsData.postStopDestination,
            audioFormat = settingsData.audioFormat,
            sampleRateHz = settingsData.sampleRateHz,
            encodingBitRate = settingsData.encodingBitRate,
            channelCount = settingsData.channelCount,
            apiKey = settingsData.apiKey,
            versionName = settingsData.versionName,
            versionCode = settingsData.versionCode,
            latestReleaseTitle = settingsData.latestReleaseTitle,
            releaseNotes = settingsData.releaseNotes,
            releaseHistory = settingsData.releaseHistory,
            savedFiles = settingsData.savedFiles,
            usageStats = settingsData.usageStats,
            apiKeyValidationStatus = validation.status,
            apiKeyValidationMessage = validation.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        viewModelScope.launch {
            apiKey.value = apiKeyProvider.getApiKey(ProviderType.OPENAI).orEmpty()
            appMetadata.value = loadAppMetadata()
            refreshSavedFiles()
            apiKeyValidationStatus.value =
                if (apiKey.value.isBlank()) ApiKeyValidationStatus.Unknown else ApiKeyValidationStatus.Valid
        }
    }

    fun setAutoTranscribe(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setAutoTranscribe(enabled) }
    }

    fun setDefaultProvider(provider: String) {
        if (provider != ProviderType.OPENAI.name) return
        viewModelScope.launch { preferencesDataStore.setDefaultProvider(provider) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { preferencesDataStore.setThemeMode(themeMode) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setKeepScreenOn(enabled) }
    }

    fun setShowRenameAfterRecording(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setShowRenameAfterRecording(enabled) }
    }

    fun setPostStopDestination(destination: PostStopDestination) {
        viewModelScope.launch { preferencesDataStore.setPostStopDestination(destination) }
    }

    fun setAudioFormat(audioFormat: AudioFormat) {
        viewModelScope.launch { preferencesDataStore.setAudioFormat(audioFormat) }
    }

    fun setSampleRateHz(sampleRateHz: Int) {
        viewModelScope.launch { preferencesDataStore.setSampleRateHz(sampleRateHz) }
    }

    fun setEncodingBitRate(bitRate: Int) {
        viewModelScope.launch { preferencesDataStore.setEncodingBitRate(bitRate) }
    }

    fun setChannelCount(channelCount: Int) {
        viewModelScope.launch { preferencesDataStore.setChannelCount(channelCount) }
    }

    fun updateApiKey(value: String) {
        apiKey.value = value
        apiKeyValidationStatus.value = ApiKeyValidationStatus.Unknown
        apiKeyValidationMessage.value = null
    }

    fun saveApiKey() {
        viewModelScope.launch {
            val trimmed = apiKey.value.trim()
            if (trimmed.isEmpty()) {
                apiKeyProvider.clearApiKey(ProviderType.OPENAI)
                apiKeyValidationStatus.value = ApiKeyValidationStatus.Unknown
                apiKeyValidationMessage.value = "API key cleared"
            } else {
                apiKeyValidationStatus.value = ApiKeyValidationStatus.Validating
                apiKeyValidationMessage.value = "Checking OpenAI connection..."
                apiKeyValidator.validate(trimmed)
                    .onSuccess {
                        apiKeyProvider.setApiKey(ProviderType.OPENAI, trimmed)
                        apiKeyValidationStatus.value = ApiKeyValidationStatus.Valid
                        apiKeyValidationMessage.value = "Connected to OpenAI"
                    }
                    .onFailure {
                        apiKeyValidationStatus.value = ApiKeyValidationStatus.Invalid
                        apiKeyValidationMessage.value = it.message ?: "Unable to validate API key"
                        return@launch
                    }
            }
            apiKey.value = trimmed
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            apiKeyProvider.clearApiKey(ProviderType.OPENAI)
            apiKey.value = ""
            apiKeyValidationStatus.value = ApiKeyValidationStatus.Unknown
            apiKeyValidationMessage.value = "API key cleared"
        }
    }

    fun refreshSavedFiles() {
        viewModelScope.launch {
            savedFiles.value = scanSavedFiles()
        }
    }

    fun deleteSavedFile(path: String) {
        viewModelScope.launch {
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            refreshSavedFiles()
        }
    }

    private fun loadAppMetadata(): AppMetadata {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val changelogText = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrElse {
            "Changelog unavailable in this build."
        }
        val releaseHistory = ReleaseNotesParser.parseReleaseHistory(changelogText)
        return AppMetadata(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            latestReleaseTitle = releaseHistory.firstOrNull()?.title,
            releaseNotes = releaseHistory.firstOrNull()?.summaryItems.orEmpty(),
            releaseHistory = releaseHistory,
        )
    }

    private data class AppMetadata(
        val versionName: String = "",
        val versionCode: Long = 0L,
        val latestReleaseTitle: String? = null,
        val releaseNotes: List<String> = emptyList(),
        val releaseHistory: List<ReleaseNotes> = emptyList(),
    )

    private data class LocalMetadata(
        val apiKey: String = "",
        val appMetadata: AppMetadata = AppMetadata(),
    )

    private data class ApiKeyValidation(
        val status: ApiKeyValidationStatus = ApiKeyValidationStatus.Unknown,
        val message: String? = null,
    )

    private data class SettingsData(
        val defaultProvider: String = "OPENAI",
        val autoTranscribe: Boolean = false,
        val defaultTransformProfileId: String? = null,
        val defaultTransformProfileName: String? = null,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val keepScreenOn: Boolean = true,
        val showRenameAfterRecording: Boolean = true,
        val postStopDestination: PostStopDestination = PostStopDestination.HOME,
        val audioFormat: AudioFormat = AudioFormat.AAC,
        val sampleRateHz: Int = 48_000,
        val encodingBitRate: Int = 128_000,
        val channelCount: Int = 1,
        val apiKey: String = "",
        val versionName: String = "",
        val versionCode: Long = 0L,
        val latestReleaseTitle: String? = null,
        val releaseNotes: List<String> = emptyList(),
        val releaseHistory: List<ReleaseNotes> = emptyList(),
        val savedFiles: List<SavedFileEntry> = emptyList(),
        val usageStats: UsageStats = UsageStats(),
    )

    private data class ProfileSettings(
        val defaultProvider: String = "OPENAI",
        val autoTranscribe: Boolean = false,
        val defaultTransformProfileId: String? = null,
        val defaultTransformProfileName: String? = null,
    )

    private data class RecordingPreferences(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val keepScreenOn: Boolean = true,
        val showRenameAfterRecording: Boolean = true,
        val postStopDestination: PostStopDestination = PostStopDestination.HOME,
        val audioFormat: AudioFormat = AudioFormat.AAC,
        val sampleRateHz: Int = 48_000,
        val encodingBitRate: Int = 128_000,
        val channelCount: Int = 1,
    )

    private data class DisplayPreferences(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val keepScreenOn: Boolean = true,
        val showRenameAfterRecording: Boolean = true,
        val postStopDestination: PostStopDestination = PostStopDestination.HOME,
    )

    private data class AudioPreferences(
        val audioFormat: AudioFormat = AudioFormat.AAC,
        val sampleRateHz: Int = 48_000,
        val encodingBitRate: Int = 128_000,
        val channelCount: Int = 1,
    )

    private data class UsageData(
        val savedFiles: List<SavedFileEntry> = emptyList(),
        val usageStats: UsageStats = UsageStats(),
    )

    private fun scanSavedFiles(): List<SavedFileEntry> {
        val directories = listOfNotNull(
            context.filesDir.resolve("recordings").takeIf { it.exists() }?.let { "Recordings" to it },
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?.resolve("exports")
                ?.takeIf { it.exists() }
                ?.let { "Exports" to it },
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?.resolve("saved-recordings")
                ?.takeIf { it.exists() }
                ?.let { "Saved Copies" to it },
        )

        return directories.flatMap { (category, dir) ->
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    SavedFileEntry(
                        name = file.name,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        category = category,
                    )
                }
                .orEmpty()
        }
    }
}
