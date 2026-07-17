package dev.scrybe.feature.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.billing.BillingManager
import com.twobits.billing.PurchaseDelegate
import com.twobits.billing.SubscriptionRepository
import com.twobits.billing.SubscriptionTier
import com.twobits.common.ReleaseNotes
import com.twobits.common.ReleaseNotesParser
import com.twobits.core.localmodels.LocalModelState
import com.twobits.securestore.SharedCredentialId
import com.twobits.securestore.ipc.SharedCredentialClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.database.PersonDao
import dev.scrybe.core.database.PersonEntity
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.localai.LocalModelManager
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.LocalGemmaModel
import dev.scrybe.core.model.LocalWhisperModel
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.OpenAiTranscriptionModel
import dev.scrybe.core.model.OpenAiTransformModel
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.ThemeMode
import dev.scrybe.core.transcription.ApiKeyProvider
import dev.scrybe.core.transcription.OpenAiApiKeyValidator
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import dev.scrybe.core.transforms.OpenAiProfileSuggestionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val transcriptionProvider: String = "OPENAI",
    val aiFeaturesProvider: String = "OPENAI",
    val autoTranscribe: Boolean = false,
    val defaultTransformProfileId: String? = null,
    val defaultTransformProfileName: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val showRenameAfterRecording: Boolean = false,
    val confirmRecordSwipeActions: Boolean = true,
    val recordingVibrateOnStartStop: Boolean = true,
    val recordingSoundOnStartStop: Boolean = false,
    val showRecordingInfoInList: Boolean = true,
    val postStopDestination: PostStopDestination = PostStopDestination.HOME,
    val audioFormat: AudioFormat = AudioFormat.AAC,
    val sampleRateHz: Int = 48_000,
    val encodingBitRate: Int = 128_000,
    val channelCount: Int = 1,
    val apiKey: String = "",
    val subscriptionTier: SubscriptionTier = SubscriptionTier.Free,
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val latestReleaseTitle: String? = null,
    val releaseNotes: List<String> = emptyList(),
    val releaseHistory: List<ReleaseNotes> = emptyList(),
    val savedFiles: List<SavedFileEntry> = emptyList(),
    val usageStats: UsageStats = UsageStats(),
    val apiKeyValidationStatus: ApiKeyValidationStatus = ApiKeyValidationStatus.Unknown,
    val apiKeyValidationMessage: String? = null,
    val transcriptionModel: String = OpenAiTranscriptionModel.default.apiName,
    val profileSuggestionModel: String = OpenAiProfileSuggestionModel.default.apiName,
    val profileSuggestionModelTestState: ProfileSuggestionModelTestUiState = ProfileSuggestionModelTestUiState.Idle,
    val transformModel: String = OpenAiTransformModel.default.apiName,
    val taskForgeEnabled: Boolean = false,
    val taskForgePackageName: String = "",
    val taskForgeAction: String = "android.intent.action.SEND",
    val enableSpeakerIdentification: Boolean = false,
    val enableInsightAnalysis: Boolean = false,
    val debugDiarization: Boolean = false,
    val locationRecordingEnabled: Boolean = false,
    val obsidianVaultUri: String = "",
)

data class SavedFileEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val category: String,
    val lastModified: Long = 0L,
)

data class UsageStats(
    val recordCount: Int = 0,
    val activeRecordCount: Int = 0,
    val archivedRecordCount: Int = 0,
    val transcriptionCount: Int = 0,
    val transformCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val averageDurationMs: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val totalEstimatedCostUsd: Double = 0.0,
    val exportFileCount: Int = 0,
    val savedCopyCount: Int = 0,
)

enum class ApiKeyValidationStatus {
    Unknown,
    Validating,
    Valid,
    Invalid,
}

sealed interface ProfileSuggestionModelTestUiState {
    data object Idle : ProfileSuggestionModelTestUiState

    data object Loading : ProfileSuggestionModelTestUiState

    data class Success(
        val resolvedModelName: String,
    ) : ProfileSuggestionModelTestUiState

    data class Error(
        val message: String,
    ) : ProfileSuggestionModelTestUiState
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val personDao: PersonDao,
        private val recordingSessionDao: RecordingSessionDao,
        transcriptDao: TranscriptDao,
        transformRunDao: TransformRunDao,
        transformProfileDao: TransformProfileDao,
        private val apiKeyProvider: ApiKeyProvider,
        private val apiKeyValidator: OpenAiApiKeyValidator,
        private val profileSuggestionService: OpenAiProfileSuggestionService,
        private val localModelManager: LocalModelManager,
        private val subscriptionRepository: SubscriptionRepository,
        private val billingManager: BillingManager,
        private val credentialClient: SharedCredentialClient,
        private val coordinator: SessionTranscriptionCoordinator,
    ) : ViewModel() {
        val persons: StateFlow<List<PersonEntity>> =
            personDao
                .getAllPersons()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        val spokenLanguages: StateFlow<String> =
            preferencesDataStore.spokenLanguages
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

        fun setSpokenLanguages(languages: String) {
            viewModelScope.launch { preferencesDataStore.setSpokenLanguages(languages) }
        }

        val whisperStates: StateFlow<Map<LocalWhisperModel, LocalModelState>> = localModelManager.whisperStates
        val selectedWhisperModel: StateFlow<LocalWhisperModel> = localModelManager.selectedWhisperModel
        val gemmaStates: StateFlow<Map<LocalGemmaModel, LocalModelState>> = localModelManager.gemmaStates

        val selectedGemmaModel: StateFlow<LocalGemmaModel> =
            preferencesDataStore.localGemmaModel
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = LocalGemmaModel.default,
                )

        private val apiKey = MutableStateFlow("")
        private val appMetadata = MutableStateFlow(AppMetadata())
        private val savedFiles = MutableStateFlow<List<SavedFileEntry>>(emptyList())
        private val apiKeyValidationStatus = MutableStateFlow(ApiKeyValidationStatus.Unknown)
        private val apiKeyValidationMessage = MutableStateFlow<String?>(null)
        private val purchaseDelegate = PurchaseDelegate(billingManager, viewModelScope)
        private val profileSuggestionModelTestState =
            MutableStateFlow<ProfileSuggestionModelTestUiState>(ProfileSuggestionModelTestUiState.Idle)
        private val localMetadata =
            combine(apiKey, appMetadata) { currentApiKey, metadata ->
                LocalMetadata(
                    apiKey = currentApiKey,
                    appMetadata = metadata,
                )
            }
        private val apiKeyValidation =
            combine(
                apiKeyValidationStatus,
                apiKeyValidationMessage,
            ) { status, message ->
                ApiKeyValidation(
                    status = status,
                    message = message,
                )
            }
        private val providersData =
            combine(
                preferencesDataStore.transcriptionProvider,
                preferencesDataStore.aiFeaturesProvider,
            ) { transcription, aiFeatures ->
                ProvidersData(transcriptionProvider = transcription, aiFeaturesProvider = aiFeatures)
            }
        private val aiFeatureToggles =
            combine(
                preferencesDataStore.enableSpeakerIdentification,
                preferencesDataStore.enableInsightAnalysis,
                preferencesDataStore.debugDiarization,
            ) { speakerIdEnabled, insightEnabled, debugDiarization ->
                Triple(speakerIdEnabled, insightEnabled, debugDiarization)
            }
        private val profileSettings =
            combine(
                providersData,
                preferencesDataStore.autoTranscribe,
                preferencesDataStore.defaultTransformProfileId,
                preferencesDataStore.profileSuggestionModel,
                combine(
                    transformProfileDao.getAllProfiles(),
                    aiFeatureToggles,
                    preferencesDataStore.cloudTranscriptionModel,
                ) { profiles, toggles, transcriptionModel ->
                    Triple(profiles, toggles, transcriptionModel)
                },
            ) { providers, autoTranscribe, profileId, profileSuggestionModel, (profiles, toggles, transcriptionModel) ->
                val (speakerIdEnabled, insightEnabled, debugDiarization) = toggles
                ProfileSettings(
                    transcriptionProvider = providers.transcriptionProvider,
                    aiFeaturesProvider = providers.aiFeaturesProvider,
                    autoTranscribe = autoTranscribe,
                    enableSpeakerIdentification = speakerIdEnabled,
                    enableInsightAnalysis = insightEnabled,
                    debugDiarization = debugDiarization,
                    defaultTransformProfileId = profileId,
                    defaultTransformProfileName = profiles.firstOrNull { it.id == profileId }?.name,
                    profileSuggestionModel = profileSuggestionModel,
                    transcriptionModel = transcriptionModel.apiName,
                )
            }
        private val displayPreferences =
            combine(
                preferencesDataStore.themeMode,
                preferencesDataStore.keepScreenOn,
                preferencesDataStore.showRenameAfterRecording,
                preferencesDataStore.confirmRecordSwipeActions,
                preferencesDataStore.postStopDestination,
            ) { themeMode, keepScreenOn, showRenameAfterRecording, confirmRecordSwipeActions, postStopDestination ->
                DisplayPreferences(
                    themeMode = themeMode,
                    keepScreenOn = keepScreenOn,
                    showRenameAfterRecording = showRenameAfterRecording,
                    confirmRecordSwipeActions = confirmRecordSwipeActions,
                    postStopDestination = postStopDestination,
                )
            }
        private val audioPreferences =
            combine(
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
        private val recordingFeedbackPreferences =
            combine(
                preferencesDataStore.recordingVibrateOnStartStop,
                preferencesDataStore.recordingSoundOnStartStop,
            ) { vibrate, sound ->
                RecordingFeedbackPreferences(vibrate = vibrate, sound = sound)
            }
        private val recordingPreferences =
            combine(
                displayPreferences,
                audioPreferences,
                preferencesDataStore.showRecordingInfoInList,
                recordingFeedbackPreferences,
            ) { displayPreferences, audioPreferences, showRecordingInfoInList, feedback ->
                RecordingPreferences(
                    themeMode = displayPreferences.themeMode,
                    keepScreenOn = displayPreferences.keepScreenOn,
                    showRenameAfterRecording = displayPreferences.showRenameAfterRecording,
                    confirmRecordSwipeActions = displayPreferences.confirmRecordSwipeActions,
                    postStopDestination = displayPreferences.postStopDestination,
                    audioFormat = audioPreferences.audioFormat,
                    sampleRateHz = audioPreferences.sampleRateHz,
                    encodingBitRate = audioPreferences.encodingBitRate,
                    channelCount = audioPreferences.channelCount,
                    showRecordingInfoInList = showRecordingInfoInList,
                    recordingVibrateOnStartStop = feedback.vibrate,
                    recordingSoundOnStartStop = feedback.sound,
                )
            }
        private val usageData =
            combine(
                recordingSessionDao.getAllSessions(),
                transcriptDao.getAllTranscripts(),
                transformRunDao.getAllRuns(),
                savedFiles,
            ) { sessions, transcripts, runs, savedFiles ->
                val archivedCount = sessions.count { it.isArchived }
                val totalDurationMs = sessions.sumOf { it.durationMs }
                UsageData(
                    savedFiles = savedFiles,
                    usageStats =
                        UsageStats(
                            recordCount = sessions.size,
                            activeRecordCount = sessions.size - archivedCount,
                            archivedRecordCount = archivedCount,
                            transcriptionCount = transcripts.count { it.type == "RAW" || it.type == "EDITED" },
                            transformCount = runs.size,
                            totalDurationMs = totalDurationMs,
                            averageDurationMs = if (sessions.isEmpty()) 0L else totalDurationMs / sessions.size,
                            totalStorageBytes = sessions.sumOf { it.fileSizeBytes },
                            totalEstimatedCostUsd = sessions.sumOf { it.estimatedTranscriptionCostUsd ?: 0.0 },
                            exportFileCount = savedFiles.count { it.category == "Exports" },
                            savedCopyCount = savedFiles.count { it.category == "Saved Copies" },
                        ),
                )
            }
        private val taskForgeSettings =
            combine(
                preferencesDataStore.taskForgeEnabled,
                preferencesDataStore.taskForgePackageName,
                preferencesDataStore.taskForgeAction,
            ) { enabled, packageName, action ->
                TaskForgeSettings(enabled = enabled, packageName = packageName, action = action)
            }
        private val integrationsPreferences =
            combine(
                preferencesDataStore.locationRecordingEnabled,
                preferencesDataStore.obsidianVaultUri,
            ) { locationEnabled, obsidianUri ->
                IntegrationsPreferences(
                    locationRecordingEnabled = locationEnabled,
                    obsidianVaultUri = obsidianUri,
                )
            }
        private val coreSettingsData =
            combine(
                profileSettings,
                recordingPreferences,
                localMetadata,
                usageData,
                preferencesDataStore.transformModel,
            ) { profileSettings, recordingPreferences, metadata, usageData, transformModel ->
                SettingsData(
                    transcriptionProvider = profileSettings.transcriptionProvider,
                    aiFeaturesProvider = profileSettings.aiFeaturesProvider,
                    autoTranscribe = profileSettings.autoTranscribe,
                    enableSpeakerIdentification = profileSettings.enableSpeakerIdentification,
                    enableInsightAnalysis = profileSettings.enableInsightAnalysis,
                    debugDiarization = profileSettings.debugDiarization,
                    defaultTransformProfileId = profileSettings.defaultTransformProfileId,
                    defaultTransformProfileName = profileSettings.defaultTransformProfileName,
                    transcriptionModel = profileSettings.transcriptionModel,
                    profileSuggestionModel = profileSettings.profileSuggestionModel,
                    transformModel = transformModel,
                    themeMode = recordingPreferences.themeMode,
                    keepScreenOn = recordingPreferences.keepScreenOn,
                    showRenameAfterRecording = recordingPreferences.showRenameAfterRecording,
                    confirmRecordSwipeActions = recordingPreferences.confirmRecordSwipeActions,
                    postStopDestination = recordingPreferences.postStopDestination,
                    showRecordingInfoInList = recordingPreferences.showRecordingInfoInList,
                    audioFormat = recordingPreferences.audioFormat,
                    sampleRateHz = recordingPreferences.sampleRateHz,
                    encodingBitRate = recordingPreferences.encodingBitRate,
                    channelCount = recordingPreferences.channelCount,
                    recordingVibrateOnStartStop = recordingPreferences.recordingVibrateOnStartStop,
                    recordingSoundOnStartStop = recordingPreferences.recordingSoundOnStartStop,
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
        private val settingsData =
            combine(
                coreSettingsData,
                taskForgeSettings,
                integrationsPreferences,
            ) { core, taskForge, integrations ->
                core.copy(
                    taskForgeEnabled = taskForge.enabled,
                    taskForgePackageName = taskForge.packageName,
                    taskForgeAction = taskForge.action,
                    locationRecordingEnabled = integrations.locationRecordingEnabled,
                    obsidianVaultUri = integrations.obsidianVaultUri,
                )
            }

        private val billingState =
            combine(
                subscriptionRepository.subscriptionTier,
                purchaseDelegate.isPurchasing,
                purchaseDelegate.purchaseError,
            ) { tier, purchasing, error ->
                BillingState(tier = tier, isPurchasing = purchasing, purchaseError = error)
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                combine(settingsData, apiKeyValidation, profileSuggestionModelTestState) { s, v, m ->
                    Triple(s, v, m)
                },
                billingState,
            ) { (settingsData, validation, modelTestState), billing ->
                SettingsUiState(
                    transcriptionProvider = settingsData.transcriptionProvider,
                    aiFeaturesProvider = settingsData.aiFeaturesProvider,
                    autoTranscribe = settingsData.autoTranscribe,
                    defaultTransformProfileId = settingsData.defaultTransformProfileId,
                    defaultTransformProfileName = settingsData.defaultTransformProfileName,
                    themeMode = settingsData.themeMode,
                    keepScreenOn = settingsData.keepScreenOn,
                    showRenameAfterRecording = settingsData.showRenameAfterRecording,
                    confirmRecordSwipeActions = settingsData.confirmRecordSwipeActions,
                    showRecordingInfoInList = settingsData.showRecordingInfoInList,
                    postStopDestination = settingsData.postStopDestination,
                    audioFormat = settingsData.audioFormat,
                    sampleRateHz = settingsData.sampleRateHz,
                    encodingBitRate = settingsData.encodingBitRate,
                    channelCount = settingsData.channelCount,
                    recordingVibrateOnStartStop = settingsData.recordingVibrateOnStartStop,
                    recordingSoundOnStartStop = settingsData.recordingSoundOnStartStop,
                    apiKey = settingsData.apiKey,
                    subscriptionTier = billing.tier,
                    isPurchasing = billing.isPurchasing,
                    purchaseError = billing.purchaseError,
                    versionName = settingsData.versionName,
                    versionCode = settingsData.versionCode,
                    latestReleaseTitle = settingsData.latestReleaseTitle,
                    releaseNotes = settingsData.releaseNotes,
                    releaseHistory = settingsData.releaseHistory,
                    savedFiles = settingsData.savedFiles,
                    usageStats = settingsData.usageStats,
                    apiKeyValidationStatus = validation.status,
                    apiKeyValidationMessage = validation.message,
                    transcriptionModel = settingsData.transcriptionModel,
                    profileSuggestionModel = settingsData.profileSuggestionModel,
                    profileSuggestionModelTestState = modelTestState,
                    transformModel = settingsData.transformModel,
                    taskForgeEnabled = settingsData.taskForgeEnabled,
                    taskForgePackageName = settingsData.taskForgePackageName,
                    taskForgeAction = settingsData.taskForgeAction,
                    enableSpeakerIdentification = settingsData.enableSpeakerIdentification,
                    enableInsightAnalysis = settingsData.enableInsightAnalysis,
                    debugDiarization = settingsData.debugDiarization,
                    locationRecordingEnabled = settingsData.locationRecordingEnabled,
                    obsidianVaultUri = settingsData.obsidianVaultUri,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

        init {
            viewModelScope.launch {
                var loaded = apiKeyProvider.getApiKey(ProviderType.OPENAI).orEmpty()
                if (loaded.isBlank()) {
                    val sibling = credentialClient.readThrough(SharedCredentialId.OPENAI)
                    if (!sibling.isNullOrBlank()) {
                        apiKeyProvider.setApiKey(ProviderType.OPENAI, sibling)
                        loaded = sibling
                    }
                }
                apiKey.value = loaded
                appMetadata.value = loadAppMetadata()
                refreshSavedFiles()
                apiKeyValidationStatus.value =
                    if (apiKey.value.isBlank()) ApiKeyValidationStatus.Unknown else ApiKeyValidationStatus.Valid
                subscriptionRepository.refresh()
            }
        }

        fun setAutoTranscribe(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setAutoTranscribe(enabled) }
        }

        fun setEnableSpeakerIdentification(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setEnableSpeakerIdentification(enabled) }
        }

        fun setEnableInsightAnalysis(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setEnableInsightAnalysis(enabled) }
        }

        fun setDebugDiarization(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setDebugDiarization(enabled) }
        }

        fun setTranscriptionProvider(provider: String) {
            viewModelScope.launch { preferencesDataStore.setTranscriptionProvider(provider) }
        }

        fun setAiFeaturesProvider(provider: String) {
            viewModelScope.launch { preferencesDataStore.setAiFeaturesProvider(provider) }
        }

        fun downloadWhisperModel(model: LocalWhisperModel) {
            viewModelScope.launch { localModelManager.downloadWhisper(model) }
        }

        fun importGemmaModel(
            uri: Uri,
            model: LocalGemmaModel,
        ) {
            viewModelScope.launch { localModelManager.importGemmaFromUri(uri, model) }
        }

        fun deleteWhisperModel(model: LocalWhisperModel) {
            localModelManager.deleteWhisper(model)
        }

        fun deleteGemmaModel(model: LocalGemmaModel) {
            localModelManager.deleteGemma(model)
        }

        fun selectWhisperModel(model: LocalWhisperModel) {
            localModelManager.selectWhisperModel(model)
        }

        fun selectGemmaModel(model: LocalGemmaModel) {
            viewModelScope.launch { preferencesDataStore.setLocalGemmaModel(model) }
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

        fun setConfirmRecordSwipeActions(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setConfirmRecordSwipeActions(enabled) }
        }

        fun setShowRecordingInfoInList(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setShowRecordingInfoInList(enabled) }
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
            profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Idle
        }

        fun saveApiKey() {
            viewModelScope.launch {
                val trimmed = apiKey.value.trim()
                if (trimmed.isEmpty()) {
                    apiKeyProvider.clearApiKey(ProviderType.OPENAI)
                    apiKeyValidationStatus.value = ApiKeyValidationStatus.Unknown
                    apiKeyValidationMessage.value = "API key cleared"
                    profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Idle
                } else {
                    apiKeyValidationStatus.value = ApiKeyValidationStatus.Validating
                    apiKeyValidationMessage.value = "Checking OpenAI connection..."
                    profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Idle
                    apiKeyValidator
                        .validate(trimmed)
                        .onSuccess {
                            apiKeyProvider.setApiKey(ProviderType.OPENAI, trimmed)
                            credentialClient.mirror(SharedCredentialId.OPENAI, trimmed)
                            apiKeyValidationStatus.value = ApiKeyValidationStatus.Valid
                            apiKeyValidationMessage.value = "Connected to OpenAI"
                        }.onFailure {
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
                profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Idle
            }
        }

        fun setTranscriptionModel(model: OpenAiTranscriptionModel) {
            viewModelScope.launch { preferencesDataStore.setCloudTranscriptionModel(model) }
        }

        fun setProfileSuggestionModel(modelName: String) {
            viewModelScope.launch {
                preferencesDataStore.setProfileSuggestionModel(modelName)
                profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Idle
            }
        }

        fun setTransformModel(modelName: String) {
            viewModelScope.launch { preferencesDataStore.setTransformModel(modelName) }
        }

        fun setRecordingVibrateOnStartStop(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setRecordingVibrateOnStartStop(enabled) }
        }

        fun setRecordingSoundOnStartStop(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setRecordingSoundOnStartStop(enabled) }
        }

        fun setTaskForgeEnabled(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setTaskForgeEnabled(enabled) }
        }

        fun setTaskForgePackageName(packageName: String) {
            viewModelScope.launch { preferencesDataStore.setTaskForgePackageName(packageName) }
        }

        fun setTaskForgeAction(action: String) {
            viewModelScope.launch { preferencesDataStore.setTaskForgeAction(action) }
        }

        fun setLocationRecordingEnabled(enabled: Boolean) {
            viewModelScope.launch { preferencesDataStore.setLocationRecordingEnabled(enabled) }
        }

        fun setObsidianVaultUri(uri: String) {
            viewModelScope.launch { preferencesDataStore.setObsidianVaultUri(uri) }
        }

        fun testApiConnection() {
            viewModelScope.launch {
                val trimmed = apiKey.value.trim()
                if (trimmed.isEmpty()) {
                    apiKeyValidationStatus.value = ApiKeyValidationStatus.Unknown
                    apiKeyValidationMessage.value = "Enter an API key first"
                    return@launch
                }
                apiKeyValidationStatus.value = ApiKeyValidationStatus.Validating
                apiKeyValidationMessage.value = "Testing connection…"
                apiKeyValidator
                    .validate(trimmed)
                    .onSuccess {
                        apiKeyValidationStatus.value = ApiKeyValidationStatus.Valid
                        apiKeyValidationMessage.value = "Connected to OpenAI"
                    }.onFailure {
                        apiKeyValidationStatus.value = ApiKeyValidationStatus.Invalid
                        apiKeyValidationMessage.value = it.message ?: "Connection test failed"
                    }
            }
        }

        fun testProfileSuggestionModel() {
            viewModelScope.launch {
                profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Loading
                profileSuggestionService
                    .testModel(uiState.value.profileSuggestionModel)
                    .fold(
                        onSuccess = { resolvedModel ->
                            profileSuggestionModelTestState.value =
                                ProfileSuggestionModelTestUiState.Success(resolvedModel)
                        },
                        onFailure = {
                            profileSuggestionModelTestState.value =
                                ProfileSuggestionModelTestUiState.Error(
                                    it.message ?: "Failed to test the selected model",
                                )
                        },
                    )
            }
        }

        fun clearProfileSuggestionModelTestState() {
            profileSuggestionModelTestState.value = ProfileSuggestionModelTestUiState.Idle
        }

        fun startProPurchase(
            activity: Activity,
            plan: String = "monthly",
        ) = purchaseDelegate.startPurchase(activity, plan)

        fun restorePurchases() = purchaseDelegate.restore()

        fun dismissPurchaseError() = purchaseDelegate.dismissError()

        fun refreshSavedFiles() {
            viewModelScope.launch {
                savedFiles.value = scanSavedFiles()
            }
        }

        fun renamePerson(
            id: String,
            name: String,
        ) {
            viewModelScope.launch { personDao.renamePerson(id, name) }
        }

        fun deletePerson(id: String) {
            viewModelScope.launch {
                personDao.clearPersonFromSegments(id)
                personDao.deletePerson(id)
            }
        }

        fun mergePersons(
            sourceId: String,
            targetId: String,
        ) {
            viewModelScope.launch {
                personDao.reassignPersonAcrossSessions(sourceId, targetId)
                personDao.deletePerson(sourceId)
            }
        }

        suspend fun sessionCountForPerson(id: String): Int = personDao.sessionCountForPerson(id)

        suspend fun segmentCountForPerson(id: String): Int = personDao.segmentCountForPerson(id)

        suspend fun talkRatioForPerson(id: String): Float = personDao.talkRatioForPerson(id) ?: 0f

        fun reIdentifyAll() {
            viewModelScope.launch {
                val sessions = recordingSessionDao.getAllSessionsOnce()
                sessions
                    .filter { File(it.audioFilePath).exists() }
                    .forEach { session ->
                        runCatching { coordinator.fetchSpeakerInfo(session.id) }
                    }
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
            val changelogText =
                runCatching {
                    context.assets
                        .open("CHANGELOG.md")
                        .bufferedReader()
                        .use { it.readText() }
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
            val transcriptionProvider: String = "OPENAI",
            val aiFeaturesProvider: String = "OPENAI",
            val autoTranscribe: Boolean = false,
            val enableSpeakerIdentification: Boolean = false,
            val enableInsightAnalysis: Boolean = false,
            val debugDiarization: Boolean = false,
            val defaultTransformProfileId: String? = null,
            val defaultTransformProfileName: String? = null,
            val themeMode: ThemeMode = ThemeMode.SYSTEM,
            val keepScreenOn: Boolean = true,
            val showRenameAfterRecording: Boolean = false,
            val confirmRecordSwipeActions: Boolean = true,
            val showRecordingInfoInList: Boolean = true,
            val postStopDestination: PostStopDestination = PostStopDestination.HOME,
            val audioFormat: AudioFormat = AudioFormat.AAC,
            val sampleRateHz: Int = 48_000,
            val encodingBitRate: Int = 128_000,
            val channelCount: Int = 1,
            val recordingVibrateOnStartStop: Boolean = true,
            val recordingSoundOnStartStop: Boolean = false,
            val apiKey: String = "",
            val transcriptionModel: String = OpenAiTranscriptionModel.default.apiName,
            val profileSuggestionModel: String = OpenAiProfileSuggestionModel.default.apiName,
            val transformModel: String = OpenAiTransformModel.default.apiName,
            val taskForgeEnabled: Boolean = false,
            val taskForgePackageName: String = "",
            val taskForgeAction: String = "android.intent.action.SEND",
            val locationRecordingEnabled: Boolean = false,
            val obsidianVaultUri: String = "",
            val versionName: String = "",
            val versionCode: Long = 0L,
            val latestReleaseTitle: String? = null,
            val releaseNotes: List<String> = emptyList(),
            val releaseHistory: List<ReleaseNotes> = emptyList(),
            val savedFiles: List<SavedFileEntry> = emptyList(),
            val usageStats: UsageStats = UsageStats(),
        )

        private data class ProfileSettings(
            val transcriptionProvider: String = "OPENAI",
            val aiFeaturesProvider: String = "OPENAI",
            val autoTranscribe: Boolean = false,
            val enableSpeakerIdentification: Boolean = false,
            val enableInsightAnalysis: Boolean = false,
            val debugDiarization: Boolean = false,
            val defaultTransformProfileId: String? = null,
            val defaultTransformProfileName: String? = null,
            val profileSuggestionModel: String = OpenAiProfileSuggestionModel.default.apiName,
            val transcriptionModel: String = OpenAiTranscriptionModel.default.apiName,
        )

        private data class RecordingPreferences(
            val themeMode: ThemeMode = ThemeMode.SYSTEM,
            val keepScreenOn: Boolean = true,
            val showRenameAfterRecording: Boolean = false,
            val confirmRecordSwipeActions: Boolean = true,
            val showRecordingInfoInList: Boolean = true,
            val postStopDestination: PostStopDestination = PostStopDestination.HOME,
            val audioFormat: AudioFormat = AudioFormat.AAC,
            val sampleRateHz: Int = 48_000,
            val encodingBitRate: Int = 128_000,
            val channelCount: Int = 1,
            val recordingVibrateOnStartStop: Boolean = true,
            val recordingSoundOnStartStop: Boolean = false,
        )

        private data class RecordingFeedbackPreferences(
            val vibrate: Boolean = true,
            val sound: Boolean = false,
        )

        private data class DisplayPreferences(
            val themeMode: ThemeMode = ThemeMode.SYSTEM,
            val keepScreenOn: Boolean = true,
            val showRenameAfterRecording: Boolean = false,
            val confirmRecordSwipeActions: Boolean = true,
            val postStopDestination: PostStopDestination = PostStopDestination.HOME,
        )

        private data class AudioPreferences(
            val audioFormat: AudioFormat = AudioFormat.AAC,
            val sampleRateHz: Int = 48_000,
            val encodingBitRate: Int = 128_000,
            val channelCount: Int = 1,
        )

        private data class TaskForgeSettings(
            val enabled: Boolean = false,
            val packageName: String = "",
            val action: String = "android.intent.action.SEND",
        )

        private data class IntegrationsPreferences(
            val locationRecordingEnabled: Boolean = false,
            val obsidianVaultUri: String = "",
        )

        private data class ProvidersData(
            val transcriptionProvider: String = "OPENAI",
            val aiFeaturesProvider: String = "OPENAI",
        )

        private data class UsageData(
            val savedFiles: List<SavedFileEntry> = emptyList(),
            val usageStats: UsageStats = UsageStats(),
        )

        private data class BillingState(
            val tier: SubscriptionTier = SubscriptionTier.Free,
            val isPurchasing: Boolean = false,
            val purchaseError: String? = null,
        )

        private fun scanSavedFiles(): List<SavedFileEntry> {
            val directories =
                listOfNotNull(
                    context.filesDir
                        .resolve("recordings")
                        .takeIf { it.exists() }
                        ?.let { "Recordings" to it },
                    context
                        .getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?.resolve("exports")
                        ?.takeIf { it.exists() }
                        ?.let { "Exports" to it },
                    context
                        .getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?.resolve("saved-recordings")
                        ?.takeIf { it.exists() }
                        ?.let { "Saved Copies" to it },
                )

            return directories.flatMap { (category, dir) ->
                dir
                    .listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { file ->
                        SavedFileEntry(
                            name = file.name,
                            path = file.absolutePath,
                            sizeBytes = file.length(),
                            category = category,
                            lastModified = file.lastModified(),
                        )
                    }.orEmpty()
            }
        }
    }
