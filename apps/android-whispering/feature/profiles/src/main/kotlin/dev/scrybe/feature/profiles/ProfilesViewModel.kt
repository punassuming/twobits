package dev.scrybe.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.TransformProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ProfilesUiState {
    data object Loading : ProfilesUiState
    data class Success(val profiles: List<TransformProfile>) : ProfilesUiState
    data class Error(val message: String) : ProfilesUiState
}

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val transformProfileDao: TransformProfileDao,
    private val preferencesDataStore: AppPreferencesDataStore,
) : ViewModel() {

    val uiState: StateFlow<ProfilesUiState> = transformProfileDao.getAllProfiles()
        .map { entities ->
            val profiles = entities.map(::toModel)
            ProfilesUiState.Success(profiles) as ProfilesUiState
        }
        .catch { emit(ProfilesUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfilesUiState.Loading,
        )

    fun saveProfile(
        existingId: String?,
        name: String,
        description: String,
        systemPrompt: String,
        setAsDefault: Boolean,
    ) {
        viewModelScope.launch {
            val id = existingId ?: UUID.randomUUID().toString()
            if (setAsDefault) {
                transformProfileDao.clearDefaultProfile()
            }
            transformProfileDao.insertProfile(
                TransformProfileEntity(
                    id = id,
                    name = name.trim(),
                    description = description.trim(),
                    systemPrompt = systemPrompt.trim(),
                    providerType = ProviderType.OPENAI.name,
                    isDefault = setAsDefault,
                )
            )
            if (setAsDefault) {
                preferencesDataStore.setDefaultTransformProfileId(id)
            } else if (existingId != null && transformProfileDao.getProfileById(existingId)?.isDefault == true) {
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
        }
    }

    fun setDefaultProfile(profileId: String) {
        viewModelScope.launch {
            transformProfileDao.setDefaultProfile(profileId)
            preferencesDataStore.setDefaultTransformProfileId(profileId)
        }
    }

    private fun toModel(entity: TransformProfileEntity): TransformProfile = TransformProfile(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        systemPrompt = entity.systemPrompt,
        providerType = ProviderType.valueOf(entity.providerType),
        isDefault = entity.isDefault,
    )
}
