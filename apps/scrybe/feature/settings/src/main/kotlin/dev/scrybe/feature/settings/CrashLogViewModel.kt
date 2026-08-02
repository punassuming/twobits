package dev.scrybe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.transcription.CrashLogEntry
import dev.scrybe.core.transcription.CrashLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CrashLogUiState(
    val entries: List<CrashLogEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CrashLogViewModel
    @Inject
    constructor(
        private val store: CrashLogStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CrashLogUiState())
        val uiState: StateFlow<CrashLogUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val entries = withContext(Dispatchers.IO) { store.readAll() }.sortedByDescending { it.timestampMs }
                _uiState.value = CrashLogUiState(entries = entries, isLoading = false)
            }
        }

        fun clear() {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { store.clear() }
                refresh()
            }
        }
    }
