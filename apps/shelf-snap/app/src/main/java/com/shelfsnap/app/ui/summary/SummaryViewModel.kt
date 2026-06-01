package com.shelfsnap.app.ui.summary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SummaryUiState(
    val items: List<Item> = emptyList(),
    val totalValue: Double = 0.0,
    val isLoading: Boolean = true,
    val exportedPath: String? = null,
    val exportError: String? = null
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val csvExporter: CsvExporter
) : ViewModel() {

    private val _extra = MutableStateFlow(
        Pair<String?, String?>(null, null) // exportedPath, exportError
    )

    val uiState: StateFlow<SummaryUiState> = combine(
        repository.observeAll(),
        _extra
    ) { items, extra ->
        SummaryUiState(
            items = items,
            totalValue = items.sumOf { it.estimatedValue },
            isLoading = false,
            exportedPath = extra.first,
            exportError = extra.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SummaryUiState()
    )

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val items = uiState.value.items
            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val result = csvExporter.export(items, outputDir)
            result.fold(
                onSuccess = { file ->
                    _extra.update { Pair(file.absolutePath, null) }
                },
                onFailure = { e ->
                    _extra.update { Pair(null, e.message ?: "Unknown error") }
                }
            )
        }
    }

    fun clearExportStatus() {
        _extra.update { Pair(null, null) }
    }
}
