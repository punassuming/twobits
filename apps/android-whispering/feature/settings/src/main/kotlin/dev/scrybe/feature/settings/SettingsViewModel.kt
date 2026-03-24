package dev.scrybe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.ApiKeyProvider
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
    val apiKey: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: AppPreferencesDataStore,
    private val apiKeyProvider: ApiKeyProvider,
) : ViewModel() {

    private val apiKey = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesDataStore.defaultProvider,
        preferencesDataStore.autoTranscribe,
        preferencesDataStore.defaultTransformProfileId,
        apiKey,
    ) { provider, autoTranscribe, profileId, currentApiKey ->
        SettingsUiState(
            defaultProvider = provider,
            autoTranscribe = autoTranscribe,
            defaultTransformProfileId = profileId,
            apiKey = currentApiKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        viewModelScope.launch {
            apiKey.value = apiKeyProvider.getApiKey(ProviderType.OPENAI).orEmpty()
        }
    }

    fun setAutoTranscribe(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setAutoTranscribe(enabled) }
    }

    fun setDefaultProvider(provider: String) {
        viewModelScope.launch { preferencesDataStore.setDefaultProvider(provider) }
    }

    fun updateApiKey(value: String) {
        apiKey.value = value
    }

    fun saveApiKey() {
        viewModelScope.launch {
            val trimmed = apiKey.value.trim()
            if (trimmed.isEmpty()) {
                apiKeyProvider.clearApiKey(ProviderType.OPENAI)
            } else {
                apiKeyProvider.setApiKey(ProviderType.OPENAI, trimmed)
            }
            apiKey.value = trimmed
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            apiKeyProvider.clearApiKey(ProviderType.OPENAI)
            apiKey.value = ""
        }
    }
}
