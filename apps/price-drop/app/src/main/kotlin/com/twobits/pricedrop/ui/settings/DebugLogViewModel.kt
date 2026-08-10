package com.twobits.pricedrop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.local.DebugLogEntry
import com.twobits.pricedrop.data.local.DebugLogEntryType
import com.twobits.pricedrop.data.local.DebugLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** null [filter] shows every entry type — the default, chronological "everything" view. */
data class DebugLogUiState(
    val entries: List<DebugLogEntry> = emptyList(),
    val filter: DebugLogEntryType? = null,
    val isLoading: Boolean = true,
) {
    val visibleEntries: List<DebugLogEntry>
        get() = if (filter == null) entries else entries.filter { it.type == filter }
}

@HiltViewModel
class DebugLogViewModel
    @Inject
    constructor(
        private val store: DebugLogStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DebugLogUiState())
        val uiState: StateFlow<DebugLogUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val entries = withContext(Dispatchers.IO) { store.readAll() }.sortedByDescending { it.timestampMs }
                _uiState.value = _uiState.value.copy(entries = entries, isLoading = false)
            }
        }

        fun setFilter(filter: DebugLogEntryType?) {
            _uiState.value = _uiState.value.copy(filter = filter)
        }

        fun clear() {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { store.clear() }
                refresh()
            }
        }
    }
