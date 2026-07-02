package com.twobits.pricedrop.data.pro

import com.twobits.core.pro.AppPlan
import com.twobits.core.pro.BackgroundPollPolicy
import com.twobits.core.pro.ExecutionMode
import com.twobits.core.pro.ManagedFeaturePolicy
import com.twobits.core.pro.UsageCounterPolicy
import com.twobits.pricedrop.data.provider.ProviderMode

/**
 * Client-side mirror of PriceDrop's managed-Pro limits. Kept in lockstep with `worker.js`
 * (USAGE_LIMITS / PRICEDROP_POLL / PRICEDROP_BUDGET); the worker remains the source of truth.
 * PriceDrop has no on-device model, so there is deliberately no LOCAL execution mode here.
 */
object PriceDropPlan {
    const val AI_ASK_MONTHLY_LIMIT = 100
    const val PRODUCT_LOOKUP_MONTHLY_LIMIT = 200

    val plan =
        AppPlan(
            appId = "pricedrop",
            proEntitlementId = "pricedrop_pro",
            // PRICEDROP_BUDGET tracking ($1.50) + lookup ($1.50).
            monthlySpendCapUsd = 3.00,
            features =
                listOf(
                    ManagedFeaturePolicy(
                        opKey = "pricedrop.chat",
                        label = "AI Ask",
                        usageCounter = UsageCounterPolicy(AI_ASK_MONTHLY_LIMIT, "questions"),
                        defaultManagedModel = "gpt-5-mini",
                    ),
                    ManagedFeaturePolicy(
                        opKey = "pricedrop.extract-product",
                        label = "Barcode & product lookups",
                        usageCounter = UsageCounterPolicy(PRODUCT_LOOKUP_MONTHLY_LIMIT, "lookups"),
                        defaultManagedModel = "gpt-5-mini",
                    ),
                    ManagedFeaturePolicy(
                        opKey = "pricedrop.tracking",
                        label = "Automatic price checks",
                        // Background price + coupon checks; the $1.50 tracking budget is the hard cap.
                        backgroundPoll = BackgroundPollPolicy(minIntervalMinutes = 60, maxPerDay = 240),
                    ),
                ),
        )
}

/**
 * Maps a per-provider [ProviderMode] to the shared [ExecutionMode]. Total by construction —
 * PriceDrop has no local capability, so this can never produce [ExecutionMode.LOCAL].
 */
fun ProviderMode.toExecutionMode(): ExecutionMode =
    when (this) {
        ProviderMode.OFF -> ExecutionMode.OFF
        ProviderMode.BYOK -> ExecutionMode.BYOK
        ProviderMode.PRO -> ExecutionMode.PRO
    }

/** Inverse of [toExecutionMode]; [ExecutionMode.LOCAL] falls back to OFF (never valid in PriceDrop). */
fun ExecutionMode.toProviderMode(): ProviderMode =
    when (this) {
        ExecutionMode.BYOK -> ProviderMode.BYOK
        ExecutionMode.PRO -> ProviderMode.PRO
        ExecutionMode.OFF, ExecutionMode.LOCAL -> ProviderMode.OFF
    }
