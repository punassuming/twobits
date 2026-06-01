package com.shelfsnap.app.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.ApiKeyValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Inventory filter modes mirroring the v2 design's filter chips. */
enum class InventoryFilter { ALL, LISTED, UNLISTED, DRAFT }

data class InventoryUiState(
    /** Items after search + filter are applied (what the list renders). */
    val items: List<Item> = emptyList(),
    val searchQuery: String = "",
    val filter: InventoryFilter = InventoryFilter.ALL,
    val isLoading: Boolean = true,
    val hasApiKey: Boolean = true,
    // Counts over the full (search-matched) set, for the filter chip labels.
    val totalCount: Int = 0,
    val listedCount: Int = 0,
    val draftCount: Int = 0
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(InventoryFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<InventoryUiState> = combine(
        _searchQuery
            .debounce(200)
            .flatMapLatest { query -> repository.search(query) },
        _searchQuery,
        _filter,
        repository.observeApiKey()
    ) { items, query, filter, apiKey ->
        InventoryUiState(
            items = items.filter { it.matches(filter) },
            searchQuery = query,
            filter = filter,
            isLoading = false,
            hasApiKey = ApiKeyValidator.isValid(apiKey),
            totalCount = items.size,
            listedCount = items.count { it.listings.isNotEmpty() },
            draftCount = items.count { it.isDraft }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState()
    )

    private fun Item.matches(filter: InventoryFilter): Boolean = when (filter) {
        InventoryFilter.ALL -> true
        InventoryFilter.LISTED -> listings.isNotEmpty()
        InventoryFilter.UNLISTED -> listings.isEmpty() && !isDraft
        InventoryFilter.DRAFT -> isDraft
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: InventoryFilter) {
        _filter.value = filter
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
