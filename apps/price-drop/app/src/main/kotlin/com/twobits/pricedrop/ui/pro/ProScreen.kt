package com.twobits.pricedrop.ui.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.ByokDirectNoteCard
import com.twobits.design.components.ProSpendCapCard
import com.twobits.design.components.ProTierCard
import com.twobits.design.components.ProUsageCard
import com.twobits.design.components.ProUsageMetric
import com.twobits.pricedrop.data.pro.PriceDropPlan
import com.twobits.pricedrop.ui.settings.SettingsViewModel

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    onNavigateToByok: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    val isPro = uiState.hasPro

    Scaffold(
        topBar = { ProTopBar(onBack = onNavigateBack, isPro = isPro) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProInfraNote()
            TierComparisonRow(isPro = isPro)
            ProSpendCapCard(
                capLabel = "Managed spend cap: \$%.2f / month".format(PriceDropPlan.plan.monthlySpendCapUsd),
                note =
                    "Pro runs on TwoBits managed providers with a monthly usage cap. When the cap " +
                        "is reached, managed price checks and AI Ask pause until the next cycle — " +
                        "Pro is metered, not unlimited.",
            )
            if (isPro) {
                ProActiveCard(
                    onRestore = viewModel::restorePurchases,
                    isPurchasing = uiState.isPurchasing,
                )
                UsageCard()
            } else {
                BillingSection(
                    isPurchasing = uiState.isPurchasing,
                    onUpgrade = { plan -> activity?.let { viewModel.startProPurchase(it, plan) } },
                    onRestore = viewModel::restorePurchases,
                )
            }
            if (uiState.purchaseError != null) {
                Text(
                    uiState.purchaseError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = viewModel::dismissPurchaseError) { Text("Dismiss") }
            }
            WhyProSection()
            ByokNote(onNavigateToByok = onNavigateToByok)
        }
    }
}

@Composable
private fun ProTopBar(
    onBack: () -> Unit,
    isPro: Boolean,
) {
    TopAppBar(
        title = {
            Column {
                Text("PriceDrop Pro", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (isPro) "Active · renews Jul 15, 2026" else "Choose how you want to use AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        actions = {
            if (isPro) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        },
    )
}

@Composable
private fun ProInfraNote() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    "Powered by the TwoBits shared API — Cloudflare Workers + managed OpenAI, " +
                        "Jina AI, and SearchAPI.io. Separate licence from Scrybe Pro and Shelf Snap Pro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierComparisonRow(isPro: Boolean) {
    Text(
        "Plans",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "Try it",
            price = "—",
            priceNote = "No account needed",
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            isHighlighted = !isPro,
            features =
                listOf(
                    "3 watched products",
                    "Daily price checks",
                    "Basic drop alerts",
                ),
        )
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "Pro",
            price = "\$4.99",
            priceNote = "/mo · billed annually",
            badge = "Zero setup",
            accentColor = MaterialTheme.colorScheme.primary,
            isHighlighted = isPro,
            features =
                listOf(
                    "Track your full watchlist",
                    "Automatic price checks (metered)",
                    "Coupons + Amazon history",
                    "AI Ask — up to ${PriceDropPlan.AI_ASK_MONTHLY_LIMIT}/mo",
                    "Priority support",
                ),
        )
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "BYOK",
            price = "Free forever",
            priceNote = "pay providers directly",
            badge = "Full control",
            accentColor = MaterialTheme.colorScheme.secondary,
            features =
                listOf(
                    "All Pro features",
                    "Own API keys",
                    "No spend cap",
                ),
        )
    }
}

@Composable
private fun BillingSection(
    isPurchasing: Boolean,
    onUpgrade: (plan: String) -> Unit,
    onRestore: () -> Unit,
) {
    var selectedPlan by remember { mutableStateOf("annual") }
    Text(
        "Billing",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanOption(
            modifier = Modifier.weight(1f),
            label = "Annual",
            price = "\$4.99/mo",
            note = "\$59.88/yr — save 17%",
            badge = "Best value",
            selected = selectedPlan == "annual",
            onClick = { selectedPlan = "annual" },
        )
        PlanOption(
            modifier = Modifier.weight(1f),
            label = "Monthly",
            price = "\$5.99/mo",
            note = "Billed each month",
            badge = null,
            selected = selectedPlan == "monthly",
            onClick = { selectedPlan = "monthly" },
        )
    }
    Button(
        onClick = { onUpgrade(selectedPlan) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isPurchasing,
        shape = RoundedCornerShape(14.dp),
    ) {
        if (isPurchasing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (isPurchasing) "Processing…" else "Upgrade to Pro")
    }
    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
        Text("Restore purchase")
    }
}

@Composable
private fun PlanOption(
    modifier: Modifier = Modifier,
    label: String,
    price: String,
    note: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        border =
            if (selected) {
                BorderStroke(1.5.dp, color.copy(alpha = 0.5f))
            } else {
                null
            },
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (badge != null) {
                Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProActiveCard(
    isPurchasing: Boolean,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pro — Active",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Annual · \$4.99/mo · renews Jul 15, 2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL)),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Manage subscription", style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    enabled = !isPurchasing,
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Restore purchase", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun UsageCard() {
    // Monthly managed allowances from PriceDropPlan (mirrors worker.js). We don't yet read live
    // per-feature usage back from the worker, so these are shown as allowances, not fake counts.
    ProUsageCard(
        metrics =
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
            ),
    )
}

@Composable
private fun WhyProSection() {
    data class WhyRow(
        val icon: ImageVector,
        val text: String,
    )
    val rows =
        listOf(
            WhyRow(Icons.Default.Key, "Works the moment you install — no accounts or API keys required."),
            WhyRow(
                Icons.Default.NotificationsActive,
                "Frequent automated checks catch price drops in time to act — free daily checks miss flash sales.",
            ),
            WhyRow(
                Icons.Default.TrendingDown,
                "Amazon price history included — see whether the current price is actually a deal.",
            ),
            WhyRow(
                Icons.Default.AutoAwesome,
                "AI Ask included — up to ${PriceDropPlan.AI_ASK_MONTHLY_LIMIT} managed questions a month, " +
                    "answered without leaving the app.",
            ),
            WhyRow(Icons.Default.SupportAgent, "Direct support channel — real responses within one business day."),
        )
    Text(
        "Why Pro",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        row.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(row.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ByokNote(onNavigateToByok: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ByokDirectNoteCard()
        TextButton(
            onClick = onNavigateToByok,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) { Text("Configure keys →") }
    }
}
