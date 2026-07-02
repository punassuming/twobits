package dev.scrybe.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import com.twobits.design.components.ByokDirectNoteCard
import com.twobits.design.components.ProSpendCapCard
import com.twobits.design.components.ProTierCard
import com.twobits.design.components.ProUsageCard
import com.twobits.design.components.ProUsageMetric
import dev.scrybe.core.common.ScrybeLayoutDefaults

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

private data class WhyItem(
    val icon: ImageVector,
    val text: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    val hasPro = uiState.subscriptionTier is SubscriptionTier.Pro
    var selectedPlan by rememberSaveable { mutableStateOf("annual") }

    Scaffold(
        topBar = {
            ProTopBar(
                hasPro = hasPro,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = ScrybeLayoutDefaults.screenHorizontalPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                        .widthIn(max = ScrybeLayoutDefaults.contentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
            ) {
                SharedInfraNote()
                TierComparisonRow(hasPro = hasPro)
                ProSpendCapCard(
                    capLabel = "Managed spend cap: \$%.2f / month".format(ScrybePlan.plan.monthlySpendCapUsd),
                    note =
                        "Pro runs on TwoBits managed providers with a monthly usage cap. When the " +
                            "cap is reached, managed transcription and AI transforms pause until the " +
                            "next cycle — Pro is metered, not unlimited.",
                )
                if (hasPro) {
                    ProActivePlanCard(onRestore = viewModel::restorePurchases)
                    ScrybeUsageCard()
                } else {
                    PlanPickerSection(
                        selectedPlan = selectedPlan,
                        onSelectPlan = { selectedPlan = it },
                        onUpgrade = { activity?.let { viewModel.startProPurchase(it, selectedPlan) } },
                        onRestore = viewModel::restorePurchases,
                    )
                }
                WhyProSection()
                ByokNoteCard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProTopBar(
    hasPro: Boolean,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("Scrybe Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (hasPro) "Active · renews Jul 15, 2026" else "Choose how you want to use AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (hasPro) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

@Composable
private fun SharedInfraNote() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).padding(top = 1.dp),
            )
            Text(
                text =
                    "Powered by the TwoBits shared API — Cloudflare Workers + managed OpenAI. " +
                        "Separate licence from Shelf Snap Pro and PriceDrop Pro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierComparisonRow(hasPro: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "PLANS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
        TierCardsRow(hasPro = hasPro)
    }
}

@Composable
private fun TierCardsRow(hasPro: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "Try it",
            price = "—",
            priceNote = "No account needed",
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            isHighlighted = !hasPro,
            features =
                listOf(
                    "Record and transcribe right away",
                    "Try AI summaries and profiles",
                    "Local storage on your device",
                ),
        )
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "Pro",
            price = "\$1.99",
            priceNote = "/mo · billed annually",
            badge = "Zero setup",
            accentColor = MaterialTheme.colorScheme.primary,
            isHighlighted = hasPro,
            features =
                listOf(
                    "Works immediately — no key config",
                    "Managed transcription + AI (metered)",
                    "Up to ${ScrybePlan.TRANSCRIBE_MONTHLY_LIMIT} transcriptions/mo",
                    "Automatic provider updates",
                    "Pro support channel",
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
                    "All Scrybe features included",
                    "Connect your own API keys",
                    "Choose your preferred models",
                    "Pay providers at their own rates",
                ),
        )
    }
}

@Composable
private fun PlanPickerSection(
    selectedPlan: String,
    onSelectPlan: (String) -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "BILLING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
        PlanPickerRow(selectedPlan = selectedPlan, onSelectPlan = onSelectPlan)
        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
            Text("Upgrade to Pro")
        }
        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("Restore purchase")
        }
    }
}

@Composable
private fun PlanPickerRow(
    selectedPlan: String,
    onSelectPlan: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlanOption(
            price = "\$1.99/mo",
            note = "\$23.88/yr — save 20%",
            badge = "Best value",
            isSelected = selectedPlan == "annual",
            onSelect = { onSelectPlan("annual") },
            modifier = Modifier.weight(1f),
        )
        PlanOption(
            price = "\$2.49/mo",
            note = "Billed each month",
            badge = null,
            isSelected = selectedPlan == "monthly",
            onSelect = { onSelectPlan("monthly") },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanOption(
    price: String,
    note: String,
    badge: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.33f) else MaterialTheme.colorScheme.outlineVariant
    val priceColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = onSelect,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (badge != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(price, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = priceColor)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProActivePlanCard(onRestore: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pro — Active",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Annual · \$1.99/mo · renews Jul 15, 2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.27f)),
                ) {
                    Text("Manage subscription", color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Text("Restore purchase")
                }
            }
        }
    }
}

@Composable
private fun ScrybeUsageCard() {
    // Monthly managed allowances from ScrybePlan (mirrors worker.js). Live per-feature usage is not
    // yet read back from the worker, so these are shown as allowances, not fabricated counts.
    ProUsageCard(
        metrics =
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
            ),
    )
}

@Composable
private fun WhyProSection() {
    val items =
        listOf(
            WhyItem(
                Icons.Filled.Key,
                "Works the moment you install — no API keys, no provider accounts, no configuration.",
            ),
            WhyItem(
                Icons.Filled.Update,
                "Model and provider updates happen in the background. You never need to reconfigure anything.",
            ),
            WhyItem(
                Icons.Filled.Speed,
                "Priority queue — your recordings are processed first during peak hours.",
            ),
            WhyItem(
                Icons.Filled.SupportAgent,
                "Direct support channel — real responses within one business day.",
            ),
        )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "WHY PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp),
                        )
                        Text(item.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ByokNoteCard() {
    ByokDirectNoteCard()
}
