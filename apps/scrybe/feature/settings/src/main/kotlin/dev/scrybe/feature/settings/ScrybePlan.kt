package dev.scrybe.feature.settings

import com.twobits.core.pro.AppPlan
import com.twobits.core.pro.ExecutionMode
import com.twobits.core.pro.ManagedFeaturePolicy
import com.twobits.core.pro.UsageCounterPolicy

/**
 * Client-side mirror of Scrybe's managed-Pro limits. Kept in lockstep with `worker.js`
 * (USAGE_LIMITS / MONTHLY_BUDGET_USD); the worker remains the source of truth.
 */
object ScrybePlan {
    const val TRANSCRIBE_MONTHLY_LIMIT = 300
    const val TRANSFORM_MONTHLY_LIMIT = 300

    val plan =
        AppPlan(
            appId = "scrybe",
            proEntitlementId = "scrybe_pro",
            // Shares the managed OpenAI budget (MONTHLY_BUDGET_USD).
            monthlySpendCapUsd = 2.00,
            features =
                listOf(
                    ManagedFeaturePolicy(
                        opKey = "scrybe.transcribe",
                        label = "Cloud transcriptions",
                        usageCounter = UsageCounterPolicy(TRANSCRIBE_MONTHLY_LIMIT, "transcriptions"),
                        defaultManagedModel = "gpt-4o-mini-transcribe",
                    ),
                    ManagedFeaturePolicy(
                        opKey = "scrybe.transform",
                        label = "AI transforms",
                        usageCounter = UsageCounterPolicy(TRANSFORM_MONTHLY_LIMIT, "runs"),
                        defaultManagedModel = "gpt-5-mini",
                    ),
                ),
        )
}

/**
 * Scrybe persists AI source internally as "LOCAL"/"OPENAI" plus a Pro/BYOK distinction derived from
 * entitlement. These adapters translate the UI segment ("local"/"byok"/"pro") to/from the shared
 * [ExecutionMode] without changing any persisted format. Scrybe ships a real on-device engine, so
 * LOCAL is valid here.
 */
fun executionModeFromSegment(segment: String?): ExecutionMode =
    when (segment) {
        "local" -> ExecutionMode.LOCAL
        "pro" -> ExecutionMode.PRO
        "byok" -> ExecutionMode.BYOK
        else -> ExecutionMode.OFF
    }

fun ExecutionMode.toSegment(): String =
    when (this) {
        ExecutionMode.LOCAL -> "local"
        ExecutionMode.PRO -> "pro"
        ExecutionMode.BYOK -> "byok"
        ExecutionMode.OFF -> "off"
    }
