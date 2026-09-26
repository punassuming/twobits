package com.twobits.debuglogui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.debuglog.Breadcrumb
import com.twobits.debuglog.BreadcrumbStore
import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogEntryType
import com.twobits.debuglog.DebugLogStore
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
    val breadcrumbs: List<Breadcrumb> = emptyList(),
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
        private val breadcrumbStore: BreadcrumbStore,
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
                _uiState.value = _uiState.value.copy(entries = entries, breadcrumbs = breadcrumbStore.current(), isLoading = false)
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
