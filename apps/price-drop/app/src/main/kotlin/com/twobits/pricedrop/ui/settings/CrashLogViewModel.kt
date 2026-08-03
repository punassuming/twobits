package com.twobits.pricedrop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.local.CrashLogEntry
import com.twobits.pricedrop.data.local.CrashLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
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
