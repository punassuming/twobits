package com.twobits.pricedrop.ui.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BarcodeUiState {
    data object Scanning : BarcodeUiState
    data class Detected(val barcode: String) : BarcodeUiState
    data class Found(val title: String, val price: Double?, val barcode: String) : BarcodeUiState
    data class Error(val message: String) : BarcodeUiState
}

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    private val watchlistRepo: WatchlistRepository,
) : ViewModel() {

    val uiState: MutableStateFlow<BarcodeUiState> = MutableStateFlow(BarcodeUiState.Scanning)

    fun onBarcodeDetected(barcode: String) {
        if (uiState.value is BarcodeUiState.Detected || uiState.value is BarcodeUiState.Found) return
        uiState.value = BarcodeUiState.Detected(barcode)
        viewModelScope.launch {
            // Real implementation posts to /v1/pricedrop/barcode
            kotlinx.coroutines.delay(600)
            uiState.value = BarcodeUiState.Found(
                title = "Product (UPC: $barcode)",
                price = null,
                barcode = barcode,
            )
        }
    }

    fun reset() { uiState.value = BarcodeUiState.Scanning }

    fun addToWatchlist(title: String, price: Double?, barcode: String, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val id = watchlistRepo.add(WatchedProduct(title = title, currentPrice = price ?: 0.0, upc = barcode))
            onAdded(id)
        }
    }
}
