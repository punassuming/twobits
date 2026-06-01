package dev.scrybe.core.billing

sealed interface SubscriptionTier {
    /** User supplies their own OpenAI API key. */
    data object Free : SubscriptionTier

    /** Managed API keys — no user key required. */
    data object Pro : SubscriptionTier
}
