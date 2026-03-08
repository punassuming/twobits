package dev.scrybe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.datastore.AppPreferencesDataStore
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: AppPreferencesDataStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesDataStore.defaultProvider,
        preferencesDataStore.autoTranscribe,
        preferencesDataStore.defaultTransformProfileId,
    ) { provider, autoTranscribe, profileId ->
        SettingsUiState(
            defaultProvider = provider,
            autoTranscribe = autoTranscribe,
            defaultTransformProfileId = profileId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setAutoTranscribe(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setAutoTranscribe(enabled) }
    }

    fun setDefaultProvider(provider: String) {
        viewModelScope.launch { preferencesDataStore.setDefaultProvider(provider) }
    }
}
