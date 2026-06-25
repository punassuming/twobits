package dev.scrybe.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.securestore.SharedCredentialId
import com.twobits.securestore.ipc.SharedCredentialClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.ApiKeyProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val dataStore: AppPreferencesDataStore,
        private val apiKeyProvider: ApiKeyProvider,
        private val credentialClient: SharedCredentialClient,
    ) : ViewModel() {
        val hasSeenOnboarding: StateFlow<Boolean?> =
            dataStore.hasSeenOnboarding
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun completeOnboarding() {
            viewModelScope.launch { dataStore.setOnboardingSeen() }
        }

        fun saveApiKey(key: String) {
            viewModelScope.launch {
                apiKeyProvider.setApiKey(ProviderType.OPENAI, key)
                credentialClient.mirror(SharedCredentialId.OPENAI, key)
            }
        }
    }
