package com.twobits.pricedrop.ui.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.model.WatchedProduct
import com.twobits.pricedrop.data.repository.AddResult
import com.twobits.pricedrop.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BarcodeUiState {
    data object Scanning : BarcodeUiState

    data class Detected(
        val barcode: String,
    ) : BarcodeUiState

    data class Found(
        val title: String,
        val price: Double?,
        val barcode: String,
        val asin: String = "",
        val url: String = "",
        val imageUrl: String = "",
        val matched: Boolean = true,
    ) : BarcodeUiState

    data class Error(
        val message: String,
    ) : BarcodeUiState
}

@HiltViewModel
class BarcodeScanViewModel
    @Inject
    constructor(
        private val watchlistRepo: WatchlistRepository,
    ) : ViewModel() {
        val uiState: MutableStateFlow<BarcodeUiState> = MutableStateFlow(BarcodeUiState.Scanning)

        fun onBarcodeDetected(barcode: String) {
            if (uiState.value is BarcodeUiState.Detected || uiState.value is BarcodeUiState.Found) return
            uiState.value = BarcodeUiState.Detected(barcode)
            viewModelScope.launch {
                runCatching { watchlistRepo.resolveBarcode(barcode) }
                    .onSuccess { match ->
                        uiState.value =
                            if (match.found) {
                                BarcodeUiState.Found(
                                    title = match.title.ifBlank { "Product (UPC: $barcode)" },
                                    price = match.price,
                                    barcode = barcode,
                                    asin = match.asin,
                                    url = match.url,
                                    imageUrl = match.imageUrl,
                                    matched = true,
                                )
                            } else {
                                // No catalog match — fall back to a manual entry keyed on the UPC.
                                BarcodeUiState.Found(
                                    title = "Product (UPC: $barcode)",
                                    price = null,
                                    barcode = barcode,
                                    matched = false,
                                )
                            }
                    }.onFailure { e ->
                        uiState.value = BarcodeUiState.Error(e.message ?: "Could not look up this barcode.")
                    }
            }
        }

        fun reportCameraError(message: String) {
            uiState.value = BarcodeUiState.Error(message)
        }

        fun reset() {
            uiState.value = BarcodeUiState.Scanning
        }

        fun addToWatchlist(
            title: String,
            price: Double?,
            barcode: String,
            asin: String = "",
            url: String = "",
            imageUrl: String = "",
            onAdded: (Long) -> Unit,
        ) {
            viewModelScope.launch {
                when (
                    val result =
                        watchlistRepo.add(
                            WatchedProduct(
                                title = title,
                                currentPrice = price ?: 0.0,
                                upc = barcode,
                                asin = asin,
                                productUrl = url,
                                imageUrl = imageUrl,
                            ),
                        )
                ) {
                    is AddResult.Added -> onAdded(result.id)
                    AddResult.LimitReached ->
                        uiState.value =
                            BarcodeUiState.Error(
                                "Free plan tracks up to 3 active products. Remove one or upgrade to " +
                                    "PriceDrop Pro for unlimited tracking.",
                            )
                }
            }
        }
    }
