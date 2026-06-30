package com.twobits.core.pro

/**
 * Client-side mirror of the managed-Pro limits enforced by the TwoBits worker. These types exist
 * for display and pre-flight UX only (e.g. "9 of 100 used", greying out a feature before the
 * worker would 429). The worker (`twobits-worker/worker.js`) remains the single source of truth
 * for hard enforcement — these numbers must be kept in lockstep with it, but the app never trusts
 * them for security.
 *
 * Deliberately free of any billing/RevenueCat dependency: an [AppPlan] is keyed by the same
 * `proEntitlementId` string that `shared/billing` uses, and the app layer joins the two.
 */
data class AppPlan(
    /** App identity sent as `X-TwoBits-App` (e.g. "scrybe", "shelfsnap", "pricedrop"). */
    val appId: String,
    /** RevenueCat entitlement id; joins to `BillingConfig.proEntitlementId`. */
    val proEntitlementId: String,
    /** Human-readable monthly managed-spend cap, for display (mirrors the worker budget). */
    val monthlySpendCapUsd: Double,
    val features: List<ManagedFeaturePolicy>,
) {
    fun feature(opKey: String): ManagedFeaturePolicy? = features.firstOrNull { it.opKey == opKey }
}

/**
 * Per-feature managed policy. [opKey] matches the worker's `<app>.<op>` operation key so client
 * and worker limits can be diffed.
 */
data class ManagedFeaturePolicy(
    val opKey: String,
    val label: String,
    /** Monthly call/usage allowance; null means uncounted (only the dollar cap applies). */
    val usageCounter: UsageCounterPolicy? = null,
    /** Model the worker assigns by default for this op (for display). */
    val defaultManagedModel: String? = null,
    /** Models the worker accepts for this op; empty means "worker dictates, client cannot pick". */
    val allowedModels: List<String> = emptyList(),
    /** Background-poll throttle for background features (e.g. PriceDrop tracking). */
    val backgroundPoll: BackgroundPollPolicy? = null,
    /** Per-minute rate limit, if any. */
    val rateLimit: RateLimitPolicy? = null,
)

data class UsageCounterPolicy(
    val monthlyLimit: Int,
    val unitLabel: String,
)

data class BackgroundPollPolicy(
    val minIntervalMinutes: Int,
    val maxPerDay: Int,
)

data class RateLimitPolicy(
    val maxPerMinute: Int,
)

/**
 * Canonical, in-memory vocabulary for how an AI feature executes. Persisted formats differ per app
 * and are NOT migrated — each app adapts at its store boundary. [LOCAL] is only valid for apps that
 * ship a real on-device implementation; PriceDrop's adapter never emits it.
 */
enum class ExecutionMode { LOCAL, BYOK, PRO, OFF }
