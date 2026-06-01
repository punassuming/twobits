package dev.scrybe.core.billing

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val billingManager: BillingManager,
) {
    val subscriptionTier: StateFlow<SubscriptionTier> = billingManager.subscriptionTier

    suspend fun refresh() = billingManager.refreshStatus()
}
