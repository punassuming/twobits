package com.shelfsnap.app.ui.settings

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.data.pro.ShelfSnapPlan
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
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    ProScreenScaffold(
        content = remember { shelfSnapProContent() },
        state =
            ProScreenState(
                hasPro = uiState.subscriptionTier is SubscriptionTier.Pro,
                isPurchasing = uiState.isPurchasing,
                purchaseError = uiState.purchaseError,
            ),
        actions =
            ProScreenActions(
                onNavigateBack = onBack,
                onUpgrade = { plan -> activity?.let { viewModel.startProPurchase(it, plan) } },
                onRestore = viewModel::restorePurchases,
                onDismissError = viewModel::dismissPurchaseError,
            ),
    )
}

private fun shelfSnapProContent(): ProScreenContent {
    val plan = ShelfSnapPlan.plan
    return ProScreenContent(
        appName = "Shelf Snap",
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
                "Separate licence from Scrybe Pro and PriceDrop Pro.",
        spendCapNote =
            "Pro runs on TwoBits managed providers with a monthly usage cap. When the cap " +
                "is reached, managed vision and market lookups pause until the next cycle — " +
                "Pro is metered, not unlimited.",
        tryItFeatures =
            listOf(
                "Snap and identify items right away",
                "Try AI-powered descriptions",
                "Build your local inventory",
            ),
        proFeatures =
            listOf(
                "Works immediately — no key config",
                "Managed Vision + search providers",
                "Up to ${ShelfSnapPlan.VISION_MONTHLY_LIMIT} photo analyses/mo",
                "Automatic model updates",
                "Pro support channel",
            ),
        byokFeatures =
            listOf(
                "All Shelf Snap features",
                "Connect your own API keys",
                "Choose preferred models",
                "Pay providers directly",
            ),
        usageMetrics = shelfSnapUsageMetrics(),
        whyPro = shelfSnapWhyPro(),
    )
}

// Monthly managed allowances from ShelfSnapPlan (mirrors worker.js). Live per-feature usage
// is not yet read back from the worker, so these are shown as allowances, not fabricated counts.
private fun shelfSnapUsageMetrics(): List<ProUsageMetric> =
    listOf(
        ProUsageMetric(
            label = "Photo analyses",
            used = null,
            limit = ShelfSnapPlan.VISION_MONTHLY_LIMIT,
            unitLabel = "scans",
            icon = Icons.Default.PhotoCamera,
        ),
        ProUsageMetric(
            label = "Market lookups",
            used = null,
            limit = ShelfSnapPlan.RESEARCH_MONTHLY_LIMIT,
            unitLabel = "lookups",
            icon = Icons.Default.Speed,
        ),
        ProUsageMetric(
            label = "Listing generations",
            used = null,
            limit = ShelfSnapPlan.LISTING_MONTHLY_LIMIT,
            unitLabel = "listings",
            icon = Icons.Default.AutoAwesome,
        ),
    )

private fun shelfSnapWhyPro(): List<ProWhyItem> =
    listOf(
        ProWhyItem(
            Icons.Default.Key,
            "Works the moment you install — snap a photo and the analysis runs, no accounts or keys required.",
        ),
        ProWhyItem(
            Icons.Default.Update,
            "Vision model updates, search provider changes, rate limits — all handled automatically in the background.",
        ),
        ProWhyItem(Icons.Default.Speed, "Priority queue — your scans are processed first during peak hours."),
        ProWhyItem(Icons.Default.SupportAgent, "Direct support channel — real responses within one business day."),
    )
