package dev.scrybe.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.OpenAiProfileSuggestionModel
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.transforms.OpenAiProfileSuggestionService
import dev.scrybe.core.transforms.ProfileSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ProfilesUiState {
    data object Loading : ProfilesUiState

    data class Success(
        val profiles: List<TransformProfile>,
    ) : ProfilesUiState

    data class Error(
        val message: String,
    ) : ProfilesUiState
}

sealed interface ProfileSuggestionUiState {
    data object Idle : ProfileSuggestionUiState

    data object Loading : ProfileSuggestionUiState

    data class Success(
        val suggestion: ProfileSuggestion,
    ) : ProfileSuggestionUiState

    data class Error(
        val message: String,
    ) : ProfileSuggestionUiState
}

data class ProfileEditorDraft(
    val existingId: String? = null,
    val name: String = "",
    val description: String = "",
    val steps: List<String> = listOf(""),
    val isDefault: Boolean = false,
    val providerType: ProviderType = ProviderType.OPENAI,
    val modelName: String? = null,
    val iconName: String = "MIC",
    val colorName: String = "BLUE",
)

internal fun TransformProfile.toDraft(): ProfileEditorDraft =
    ProfileEditorDraft(
        existingId = id,
        name = name,
        description = description,
        steps = steps.ifEmpty { listOf(systemPrompt) },
        isDefault = isDefault,
        providerType = providerType,
        modelName = modelName,
        iconName = iconName,
        colorName = colorName,
    )

@HiltViewModel
class ProfilesViewModel
    @Inject
    constructor(
        private val transformProfileDao: TransformProfileDao,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val profileSuggestionService: OpenAiProfileSuggestionService,
    ) : ViewModel() {
        val uiState: StateFlow<ProfilesUiState> =
            transformProfileDao
                .getAllProfiles()
                .map { entities ->
                    val profiles = entities.map(::toModel)
                    ProfilesUiState.Success(profiles) as ProfilesUiState
                }.catch { emit(ProfilesUiState.Error(it.message ?: "Unknown error")) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ProfilesUiState.Loading,
                )

        private val _suggestionState = MutableStateFlow<ProfileSuggestionUiState>(ProfileSuggestionUiState.Idle)
        val suggestionState: StateFlow<ProfileSuggestionUiState> = _suggestionState.asStateFlow()

        val profileSuggestionModel: StateFlow<String> =
            preferencesDataStore.profileSuggestionModel
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = OpenAiProfileSuggestionModel.default.apiName,
                )

        private val _editorDraft = MutableStateFlow<ProfileEditorDraft?>(null)
        val editorDraft: StateFlow<ProfileEditorDraft?> = _editorDraft.asStateFlow()

        private val _aiCreatorOpen = MutableStateFlow(false)
        val aiCreatorOpen: StateFlow<Boolean> = _aiCreatorOpen.asStateFlow()

        fun openNewEditor() {
            _editorDraft.value = ProfileEditorDraft()
        }

        fun openEditor(profile: TransformProfile) {
            _editorDraft.value = profile.toDraft()
        }

        fun updateEditorDraft(draft: ProfileEditorDraft) {
            _editorDraft.value = draft
        }

        fun closeEditor() {
            _editorDraft.value = null
        }

        fun openAiCreator() {
            _aiCreatorOpen.value = true
        }

        fun closeAiCreator() {
            _aiCreatorOpen.value = false
        }

        fun saveProfile(draft: ProfileEditorDraft) {
            viewModelScope.launch {
                val id = draft.existingId ?: UUID.randomUUID().toString()
                val normalizedSteps = draft.steps.map { it.trim() }.filter { it.isNotBlank() }
                if (normalizedSteps.isEmpty()) return@launch
                if (draft.isDefault) transformProfileDao.clearDefaultProfile()
                transformProfileDao.insertProfile(
                    TransformProfileEntity(
                        id = id,
                        name = draft.name.trim(),
                        description = draft.description.trim(),
                        systemPrompt = normalizedSteps.first(),
                        steps = TransformStepsCodec.encode(normalizedSteps),
                        providerType = draft.providerType.name,
                        isDefault = draft.isDefault,
                        modelName = draft.modelName?.takeIf { it.isNotBlank() },
                        iconName = draft.iconName,
                        colorName = draft.colorName,
                    ),
                )
                if (draft.isDefault) {
                    preferencesDataStore.setDefaultTransformProfileId(id)
                } else if (draft.existingId != null && transformProfileDao.getProfileById(draft.existingId)?.isDefault == true) {
                    preferencesDataStore.setDefaultTransformProfileId(null)
                }
            }
        }

        fun deleteProfile(profileId: String) {
            viewModelScope.launch {
                val wasDefault = transformProfileDao.getProfileById(profileId)?.isDefault == true
                transformProfileDao.deleteProfile(profileId)
                if (wasDefault) {
                    preferencesDataStore.setDefaultTransformProfileId(null)
                }
                if (profileId.startsWith("default-")) {
                    preferencesDataStore.addDeletedDefaultProfileId(profileId)
                }
            }
        }

        fun setDefaultProfile(profileId: String) {
            viewModelScope.launch {
                transformProfileDao.setDefaultProfile(profileId)
                preferencesDataStore.setDefaultTransformProfileId(profileId)
            }
        }

        fun suggestProfile(
            userRequest: String,
            currentName: String,
            currentDescription: String,
            currentSteps: List<String>,
        ) {
            viewModelScope.launch {
                _suggestionState.value = ProfileSuggestionUiState.Loading
                profileSuggestionService
                    .suggestProfile(
                        userRequest = userRequest,
                        existingName = currentName,
                        existingDescription = currentDescription,
                        existingSteps = currentSteps,
                        modelName = profileSuggestionModel.value,
                    ).fold(
                        onSuccess = {
                            _suggestionState.value = ProfileSuggestionUiState.Success(it)
                        },
                        onFailure = {
                            _suggestionState.value =
                                ProfileSuggestionUiState.Error(
                                    it.message ?: "Failed to suggest a profile",
                                )
                        },
                    )
            }
        }

        fun clearSuggestionState() {
            _suggestionState.value = ProfileSuggestionUiState.Idle
        }

        private fun toModel(entity: TransformProfileEntity): TransformProfile =
            TransformProfile(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                systemPrompt = entity.systemPrompt,
                steps = TransformStepsCodec.decode(entity.steps, fallback = entity.systemPrompt),
                providerType = ProviderType.valueOf(entity.providerType),
                isDefault = entity.isDefault,
                modelName = entity.modelName,
                iconName = entity.iconName,
                colorName = entity.colorName,
            )
    }
