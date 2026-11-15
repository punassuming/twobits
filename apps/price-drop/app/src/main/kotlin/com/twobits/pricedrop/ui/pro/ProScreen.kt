package com.twobits.pricedrop.ui.pro

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.ProScreenActions
import com.twobits.design.components.ProScreenContent
import com.twobits.design.components.ProScreenPricing
import com.twobits.design.components.ProScreenScaffold
import com.twobits.design.components.ProScreenState
import com.twobits.design.components.ProUsageMetric
import com.twobits.design.components.ProWhyItem
import com.twobits.pricedrop.data.pro.PriceDropPlan
import com.twobits.pricedrop.ui.settings.SettingsViewModel

@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    onNavigateToByok: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    ProScreenScaffold(
        content = remember { priceDropProContent() },
        state =
            ProScreenState(
                hasPro = uiState.hasPro,
                isPurchasing = uiState.isPurchasing,
                purchaseError = uiState.purchaseError,
            ),
        actions =
            ProScreenActions(
                onNavigateBack = onNavigateBack,
                onUpgrade = { plan -> activity?.let { viewModel.startProPurchase(it, plan) } },
                onRestore = viewModel::restorePurchases,
                onDismissError = viewModel::dismissPurchaseError,
                onConfigureByok = onNavigateToByok,
            ),
    )
}

private fun priceDropProContent(): ProScreenContent {
    val plan = PriceDropPlan.plan
    return ProScreenContent(
        appName = "PriceDrop",
        pricing =
            ProScreenPricing(
                monthlyLabel = plan.pricing.monthlyLabel,
                annualPerMonthLabel = plan.pricing.annualPerMonthLabel,
                annualNote = plan.pricing.annualNote,
                annualPerMonthPrice = plan.pricing.annualPerMonthPrice,
                monthlySpendCapUsd = plan.monthlySpendCapUsd,
            ),
        infraNote =
            "Powered by the TwoBits shared API — Cloudflare Workers + managed OpenAI, " +
                "Jina AI, and SearchAPI.io. Separate licence from Scrybe Pro and Shelf Snap Pro.",
        spendCapNote =
            "Pro runs on TwoBits managed providers with a monthly usage cap. When the cap " +
                "is reached, managed price checks and AI Ask pause until the next cycle — " +
                "Pro is metered, not unlimited.",
        tryItFeatures = listOf("3 watched products", "Daily price checks", "Basic drop alerts"),
        proFeatures =
            listOf(
                "Track your full watchlist",
                "Automatic price checks (metered)",
                "Coupons + Amazon history",
                "AI Ask — up to ${PriceDropPlan.AI_ASK_MONTHLY_LIMIT}/mo",
                "Priority support",
            ),
        byokFeatures = listOf("All Pro features", "Own API keys", "No spend cap"),
        usageMetrics = priceDropUsageMetrics(),
        whyPro = priceDropWhyPro(),
    )
}

// Monthly managed allowances from PriceDropPlan (mirrors worker.js). We don't yet read live
// per-feature usage back from the worker, so these are shown as allowances, not fake counts.
private fun priceDropUsageMetrics(): List<ProUsageMetric> =
    listOf(
        ProUsageMetric(
            label = "AI Ask",
            used = null,
            limit = PriceDropPlan.AI_ASK_MONTHLY_LIMIT,
            unitLabel = "questions",
            icon = Icons.Default.AutoAwesome,
        ),
        ProUsageMetric(
            label = "Barcode & product lookups",
            used = null,
            limit = PriceDropPlan.PRODUCT_LOOKUP_MONTHLY_LIMIT,
            unitLabel = "lookups",
            icon = Icons.Default.Sell,
        ),
    )

private fun priceDropWhyPro(): List<ProWhyItem> =
    listOf(
        ProWhyItem(Icons.Default.Key, "Works the moment you install — no accounts or API keys required."),
        ProWhyItem(
            Icons.Default.NotificationsActive,
            "Frequent automated checks catch price drops in time to act — free daily checks miss flash sales.",
        ),
        ProWhyItem(
            Icons.Default.TrendingDown,
            "Amazon price history included — see whether the current price is actually a deal.",
        ),
        ProWhyItem(
            Icons.Default.AutoAwesome,
            "AI Ask included — up to ${PriceDropPlan.AI_ASK_MONTHLY_LIMIT} managed questions a month, " +
                "answered without leaving the app.",
        ),
        ProWhyItem(Icons.Default.SupportAgent, "Direct support channel — real responses within one business day."),
    )
