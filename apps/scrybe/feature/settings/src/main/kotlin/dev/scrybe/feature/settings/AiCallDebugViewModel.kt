package dev.scrybe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.transcription.AiCallDebugEntry
import dev.scrybe.core.transcription.AiCallDebugStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiCallDebugUiState(
    val entries: List<AiCallDebugEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AiCallDebugViewModel
    @Inject
    constructor(
        private val store: AiCallDebugStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AiCallDebugUiState())
        val uiState: StateFlow<AiCallDebugUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val entries = store.readAll().sortedByDescending { it.timestampMs }
                _uiState.value = AiCallDebugUiState(entries = entries, isLoading = false)
            }
        }

        fun clear() {
            viewModelScope.launch {
                store.clear()
                refresh()
            }
        }
    }
