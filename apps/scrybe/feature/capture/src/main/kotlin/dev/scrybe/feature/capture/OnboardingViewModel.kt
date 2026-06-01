package dev.scrybe.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.datastore.AppPreferencesDataStore
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
    ) : ViewModel() {
        val hasSeenOnboarding: StateFlow<Boolean?> =
            dataStore.hasSeenOnboarding
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun completeOnboarding() {
            viewModelScope.launch { dataStore.setOnboardingSeen() }
        }

        fun saveApiKey(key: String) {
            viewModelScope.launch { dataStore.setOpenAiApiKey(key) }
        }
    }
