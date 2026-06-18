package com.twobits.pricedrop.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the first-run gate. [completed] is `null` while the flag is still loading
 * from DataStore so navigation can hold its start destination until it is known.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ViewModel() {
        val completed: StateFlow<Boolean?> =
            dataStore.data
                .map { it[ONBOARDING_COMPLETE] ?: false }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun markComplete() {
            viewModelScope.launch { dataStore.edit { it[ONBOARDING_COMPLETE] = true } }
        }

        private companion object {
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        }
    }
