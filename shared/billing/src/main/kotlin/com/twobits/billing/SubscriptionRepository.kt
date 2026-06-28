package com.twobits.billing

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubscriptionRepository(private val billingManager: BillingManager) {
    val subscriptionTier: StateFlow<SubscriptionTier> = billingManager.subscriptionTier

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun refresh() = billingManager.refreshStatus()

    fun getAppUserId(): String = billingManager.getAppUserId()

    /**
     * Ensures the tier reflects RevenueCat at least once this process before a
     * routing/quota decision is made — otherwise a cold-started Pro user is seen as
     * [SubscriptionTier.Free] (the initial default) until they open Settings.
     *
     * Refreshes once, awaited by the first caller; retries on failure (only a
     * successful refresh marks it initialized). Cheap thereafter — subsequent
     * callers return immediately and rely on the StateFlow, which purchase flows
     * keep current.
     */
    suspend fun ensureFresh() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            // Only mark initialized when the refresh actually reached RevenueCat —
            // refreshStatus() swallows network errors, so a failed cold-start fetch
            // must be retried on the next call rather than latching us to Free.
            if (billingManager.refreshStatus()) initialized = true
        }
    }
}
