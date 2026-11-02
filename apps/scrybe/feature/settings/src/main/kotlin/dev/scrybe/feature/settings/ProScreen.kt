package dev.scrybe.feature.settings

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import com.twobits.design.components.ProScreenActions
import com.twobits.design.components.ProScreenContent
import com.twobits.design.components.ProScreenPricing
import com.twobits.design.components.ProScreenScaffold
import com.twobits.design.components.ProScreenState
import com.twobits.design.components.ProUsageMetric
import com.twobits.design.components.ProWhyItem

@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    ProScreenScaffold(
        content = remember { scrybeProContent() },
        state =
            ProScreenState(
                hasPro = uiState.subscriptionTier is SubscriptionTier.Pro,
                isPurchasing = uiState.isPurchasing,
                purchaseError = uiState.purchaseError,
            ),
        actions =
            ProScreenActions(
                onNavigateBack = onNavigateBack,
                onUpgrade = { plan -> activity?.let { viewModel.startProPurchase(it, plan) } },
                onRestore = viewModel::restorePurchases,
                onDismissError = viewModel::dismissPurchaseError,
            ),
    )
}

private fun scrybeProContent(): ProScreenContent {
    val plan = ScrybePlan.plan
    return ProScreenContent(
        appName = "Scrybe",
        pricing =
            ProScreenPricing(
                monthlyLabel = plan.pricing.monthlyLabel,
                annualPerMonthLabel = plan.pricing.annualPerMonthLabel,
                annualNote = plan.pricing.annualNote,
                annualPerMonthPrice = plan.pricing.annualPerMonthPrice,
                monthlySpendCapUsd = plan.monthlySpendCapUsd,
            ),
        infraNote =
            "Powered by the TwoBits shared API — Cloudflare Workers + managed OpenAI. " +
                "Separate licence from Shelf Snap Pro and PriceDrop Pro.",
        spendCapNote =
            "Pro runs on TwoBits managed providers with a monthly usage cap. When the " +
                "cap is reached, managed transcription and AI transforms pause until the " +
                "next cycle — Pro is metered, not unlimited.",
        tryItFeatures =
            listOf(
                "Record and transcribe right away",
                "Try AI summaries and profiles",
                "Local storage on your device",
            ),
        proFeatures =
            listOf(
                "Works immediately — no key config",
                "Managed transcription + AI (metered)",
                "Up to ${ScrybePlan.TRANSCRIBE_MONTHLY_LIMIT} transcriptions/mo",
                "Automatic provider updates",
                "Pro support channel",
            ),
        byokFeatures =
            listOf(
                "All Scrybe features included",
                "Connect your own API keys",
                "Choose your preferred models",
                "Pay providers at their own rates",
            ),
        usageMetrics = scrybeUsageMetrics(),
        whyPro = scrybeWhyPro(),
    )
}

// Monthly managed allowances from ScrybePlan (mirrors worker.js). Live per-feature usage is
// not yet read back from the worker, so these are shown as allowances, not fabricated counts.
private fun scrybeUsageMetrics(): List<ProUsageMetric> =
    listOf(
        ProUsageMetric(
            label = "Cloud transcriptions",
            used = null,
            limit = ScrybePlan.TRANSCRIBE_MONTHLY_LIMIT,
            unitLabel = "transcriptions",
            icon = Icons.Filled.Mic,
        ),
        ProUsageMetric(
            label = "AI transforms",
            used = null,
            limit = ScrybePlan.TRANSFORM_MONTHLY_LIMIT,
            unitLabel = "runs",
            icon = Icons.Filled.AutoAwesome,
        ),
    )

private fun scrybeWhyPro(): List<ProWhyItem> =
    listOf(
        ProWhyItem(
            Icons.Filled.Key,
            "Works the moment you install — no API keys, no provider accounts, no configuration.",
        ),
        ProWhyItem(
            Icons.Filled.Update,
            "Model and provider updates happen in the background. You never need to reconfigure anything.",
        ),
        ProWhyItem(
            Icons.Filled.Speed,
            "Priority queue — your recordings are processed first during peak hours.",
        ),
        ProWhyItem(
            Icons.Filled.SupportAgent,
            "Direct support channel — real responses within one business day.",
        ),
    )
