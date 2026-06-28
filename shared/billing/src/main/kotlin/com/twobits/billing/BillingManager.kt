package com.twobits.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BillingManager(
    context: Context,
    private val config: BillingConfig,
) {
    private val _tier = MutableStateFlow<SubscriptionTier>(SubscriptionTier.Free)
    val subscriptionTier: StateFlow<SubscriptionTier> = _tier.asStateFlow()

    init {
        Purchases.configure(PurchasesConfiguration.Builder(context, config.revenueCatPublicKey).build())
    }

    /** Returns true if RevenueCat was reached and the tier updated; false on failure. */
    suspend fun refreshStatus(): Boolean =
        runCatching { fetchCustomerInfo() }
            .onSuccess { _tier.value = it.toTier() }
            .isSuccess

    suspend fun getMonthlyPackage(): Package? =
        runCatching {
            val offerings = fetchOfferings()
            offerings.current?.availablePackages?.firstOrNull {
                it.identifier == "\$rc_monthly" || it.product.sku.contains("monthly")
            }
        }.getOrNull()

    suspend fun getAnnualPackage(): Package? =
        runCatching {
            val offerings = fetchOfferings()
            offerings.current?.availablePackages?.firstOrNull {
                it.identifier == "\$rc_annual" || it.product.sku.contains("annual")
            }
        }.getOrNull()

    suspend fun purchase(activity: Activity, pkg: Package): Result<SubscriptionTier> =
        runCatching {
            val customerInfo = purchasePackage(activity, pkg)
            val tier = customerInfo.toTier()
            _tier.value = tier
            tier
        }

    fun getAppUserId(): String = Purchases.sharedInstance.appUserID

    suspend fun restorePurchases(): Result<SubscriptionTier> =
        runCatching {
            val customerInfo = doRestorePurchases()
            val tier = customerInfo.toTier()
            _tier.value = tier
            tier
        }

    private suspend fun fetchCustomerInfo(): CustomerInfo =
        suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.getCustomerInfo(
                object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) = cont.resume(customerInfo)
                    override fun onError(error: PurchasesError) =
                        cont.resumeWithException(Exception(error.message))
                },
            )
        }

    private suspend fun fetchOfferings(): Offerings =
        suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.getOfferings(
                object : ReceiveOfferingsCallback {
                    override fun onReceived(offerings: Offerings) = cont.resume(offerings)
                    override fun onError(error: PurchasesError) =
                        cont.resumeWithException(Exception(error.message))
                },
            )
        }

    private suspend fun purchasePackage(activity: Activity, pkg: Package): CustomerInfo =
        suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, pkg).build(),
                object : PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) =
                        cont.resume(customerInfo)
                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        val ex = if (userCancelled) PurchaseCancelledException()
                        else Exception(error.message)
                        cont.resumeWithException(ex)
                    }
                },
            )
        }

    private suspend fun doRestorePurchases(): CustomerInfo =
        suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.restorePurchases(
                object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) = cont.resume(customerInfo)
                    override fun onError(error: PurchasesError) =
                        cont.resumeWithException(Exception(error.message))
                },
            )
        }

    private fun CustomerInfo.toTier(): SubscriptionTier =
        if (entitlements[config.proEntitlementId]?.isActive == true) SubscriptionTier.Pro
        else SubscriptionTier.Free
}
