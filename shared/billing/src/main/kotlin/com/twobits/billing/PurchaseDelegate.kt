package com.twobits.billing

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared purchase/restore orchestration for the apps' settings screens.
 *
 * Wraps [BillingManager] with loading + error state so each ViewModel no longer
 * re-implements the same launch → resolve package → purchase → map failure dance.
 * Construct it with the owning ViewModel's `viewModelScope`.
 */
class PurchaseDelegate(
    private val billingManager: BillingManager,
    private val scope: CoroutineScope,
) {
    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    /** Start a Pro purchase. [plan] is `"annual"` or `"monthly"` (defaults to monthly). */
    fun startPurchase(
        activity: Activity,
        plan: String = "monthly",
    ) {
        scope.launch {
            _isPurchasing.value = true
            _purchaseError.value = null
            val pkg =
                if (plan == "annual") {
                    billingManager.getAnnualPackage() ?: billingManager.getMonthlyPackage()
                } else {
                    billingManager.getMonthlyPackage()
                }
            if (pkg == null) {
                _purchaseError.value = "Subscription not available — try again shortly."
                _isPurchasing.value = false
                return@launch
            }
            billingManager
                .purchase(activity, pkg)
                .onFailure { e ->
                    if (e !is PurchaseCancelledException) {
                        _purchaseError.value = e.message ?: "Purchase failed."
                    }
                }
            _isPurchasing.value = false
        }
    }

    fun restore() {
        scope.launch {
            _isPurchasing.value = true
            _purchaseError.value = null
            billingManager
                .restorePurchases()
                .onFailure { _purchaseError.value = it.message ?: "Restore failed." }
            _isPurchasing.value = false
        }
    }

    fun dismissError() {
        _purchaseError.value = null
    }
}
