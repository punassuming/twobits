package com.shelfsnap.app.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.ApiKeyValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Inventory filter modes matching the design's filter chips. */
enum class InventoryFilter { ALL, DRAFT, LISTED, SOLD }

enum class SortOrder(
    val label: String,
) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    VALUE_HIGH("Value: high to low"),
    VALUE_LOW("Value: low to high"),
    ALPHA("A → Z"),
}

data class InventoryUiState(
    /** Items after search + filter + sort are applied (what the list renders). */
    val items: List<Item> = emptyList(),
    val searchQuery: String = "",
    val filter: InventoryFilter = InventoryFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val isLoading: Boolean = true,
    val hasApiKey: Boolean = true,
    // Counts over the full (search-matched) set, for the filter chip labels.
    val totalCount: Int = 0,
    val listedCount: Int = 0,
    val draftCount: Int = 0,
    val soldCount: Int = 0,
)

@HiltViewModel
class InventoryViewModel
    @Inject
    constructor(
        private val repository: ItemRepository,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        private val _filter = MutableStateFlow(InventoryFilter.ALL)
        private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        val uiState: StateFlow<InventoryUiState> =
            combine(
                _searchQuery
                    .debounce(200)
                    .flatMapLatest { query -> repository.search(query) },
                _searchQuery,
                _filter,
                _sortOrder,
                repository.observeApiKey(),
            ) { items, query, filter, sortOrder, apiKey ->
                val filtered = items.filter { it.matches(filter) }
                val sorted =
                    when (sortOrder) {
                        SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                        SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
                        SortOrder.VALUE_HIGH -> filtered.sortedByDescending { it.estimatedValue ?: 0.0 }
                        SortOrder.VALUE_LOW -> filtered.sortedBy { it.estimatedValue ?: 0.0 }
                        SortOrder.ALPHA -> filtered.sortedBy { it.category.lowercase() }
                    }
                InventoryUiState(
                    items = sorted,
                    searchQuery = query,
                    filter = filter,
                    sortOrder = sortOrder,
                    isLoading = false,
                    hasApiKey = ApiKeyValidator.isValid(apiKey),
                    totalCount = items.size,
                    listedCount = items.count { it.hasActiveListing },
                    draftCount = items.count { it.isDraft },
                    soldCount = items.count { it.hasSold },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = InventoryUiState(),
            )

        private fun Item.matches(filter: InventoryFilter): Boolean =
            when (filter) {
                InventoryFilter.ALL -> true
                InventoryFilter.DRAFT -> isDraft
                InventoryFilter.LISTED -> hasActiveListing
                InventoryFilter.SOLD -> hasSold
            }

        fun onSearchQueryChange(query: String) {
            _searchQuery.value = query
        }

        fun onFilterChange(filter: InventoryFilter) {
            _filter.value = filter
        }

        fun onSortChange(sort: SortOrder) {
            _sortOrder.value = sort
        }

        fun deleteItem(id: Long) {
            viewModelScope.launch { repository.delete(id) }
        }
    }
