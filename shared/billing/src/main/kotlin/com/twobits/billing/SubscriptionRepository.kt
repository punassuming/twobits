package com.twobits.billing

import kotlinx.coroutines.flow.StateFlow

class SubscriptionRepository(private val billingManager: BillingManager) {
    val subscriptionTier: StateFlow<SubscriptionTier> = billingManager.subscriptionTier
    suspend fun refresh() = billingManager.refreshStatus()
    fun getAppUserId(): String = billingManager.getAppUserId()
}
