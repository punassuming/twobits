package com.twobits.pricedrop.ui.drops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.repository.DropsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DropsViewModel @Inject constructor(
    private val dropsRepo: DropsRepository,
) : ViewModel() {

    val drops: StateFlow<List<Drop>> = dropsRepo.observeActiveDrops()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismiss(id: Long) { viewModelScope.launch { dropsRepo.dismiss(id) } }
}
