package com.twobits.pricedrop.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.DropsRepository
import com.twobits.pricedrop.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchViewModel @Inject constructor(
    private val watchlistRepo: WatchlistRepository,
    private val dropsRepo: DropsRepository,
) : ViewModel() {

    val watchlist: StateFlow<List<WatchedProduct>> = watchlistRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeDropCount: StateFlow<Int> = dropsRepo.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeFilter = MutableStateFlow("All")

    fun setFilter(filter: String) { activeFilter.value = filter }

    fun removeItem(id: Long) { viewModelScope.launch { watchlistRepo.remove(id) } }
}
