package com.twobits.billing

sealed interface SubscriptionTier {
    data object Free : SubscriptionTier
    data object Pro : SubscriptionTier
}
