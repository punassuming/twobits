package dev.scrybe.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.TransformProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ProfilesUiState {
    data object Loading : ProfilesUiState
    data class Success(val profiles: List<TransformProfile>) : ProfilesUiState
    data class Error(val message: String) : ProfilesUiState
}

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    transformProfileDao: TransformProfileDao,
) : ViewModel() {

    val uiState: StateFlow<ProfilesUiState> = transformProfileDao.getAllProfiles()
        .map { entities ->
            val profiles = entities.map { entity ->
                TransformProfile(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    systemPrompt = entity.systemPrompt,
                    providerType = ProviderType.valueOf(entity.providerType),
                    isDefault = entity.isDefault,
                )
            }
            ProfilesUiState.Success(profiles) as ProfilesUiState
        }
        .catch { emit(ProfilesUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfilesUiState.Loading,
        )
}
