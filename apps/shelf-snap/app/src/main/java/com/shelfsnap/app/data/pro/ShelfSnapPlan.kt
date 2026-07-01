package com.shelfsnap.app.data.pro

import com.twobits.core.pro.AppPlan
import com.twobits.core.pro.ExecutionMode
import com.twobits.core.pro.ManagedFeaturePolicy
import com.twobits.core.pro.UsageCounterPolicy

/**
 * Client-side mirror of Shelf Snap's managed-Pro limits. Kept in lockstep with `worker.js`
 * (USAGE_LIMITS / MONTHLY_BUDGET_USD); the worker remains the source of truth.
 */
object ShelfSnapPlan {
    const val VISION_MONTHLY_LIMIT = 200
    const val RESEARCH_MONTHLY_LIMIT = 200
    const val LISTING_MONTHLY_LIMIT = 100

    val plan =
        AppPlan(
            appId = "shelfsnap",
            proEntitlementId = "shelfsnap_pro",
            // Shares the managed OpenAI budget (MONTHLY_BUDGET_USD).
            monthlySpendCapUsd = 2.00,
            features =
                listOf(
                    ManagedFeaturePolicy(
                        opKey = "shelfsnap.vision",
                        label = "Photo analyses",
                        usageCounter = UsageCounterPolicy(VISION_MONTHLY_LIMIT, "scans"),
                        defaultManagedModel = "gpt-5-mini",
                    ),
                    ManagedFeaturePolicy(
                        opKey = "shelfsnap.price-research",
                        label = "Market lookups",
                        usageCounter = UsageCounterPolicy(RESEARCH_MONTHLY_LIMIT, "lookups"),
                        defaultManagedModel = "gpt-5-mini",
                    ),
                    ManagedFeaturePolicy(
                        opKey = "shelfsnap.listing",
                        label = "Listing generations",
                        usageCounter = UsageCounterPolicy(LISTING_MONTHLY_LIMIT, "listings"),
                        defaultManagedModel = "gpt-5.4-mini",
                    ),
                ),
        )
}

/**
 * Shelf Snap persists AI-source selection as "pro"/"byok"/"local" strings per feature. These
 * adapters translate to/from the shared [ExecutionMode] without changing the on-disk format. Shelf
 * Snap ships a real (UI-level) local option, so LOCAL is a valid mode here.
 */
fun executionModeFromSourceKey(key: String?): ExecutionMode =
    when (key) {
        "pro" -> ExecutionMode.PRO
        "byok" -> ExecutionMode.BYOK
        "local" -> ExecutionMode.LOCAL
        else -> ExecutionMode.OFF
    }

fun ExecutionMode.toSourceKey(): String =
    when (this) {
        ExecutionMode.PRO -> "pro"
        ExecutionMode.BYOK -> "byok"
        ExecutionMode.LOCAL -> "local"
        ExecutionMode.OFF -> "off"
    }
