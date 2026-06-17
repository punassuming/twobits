package dev.scrybe.feature.settings

import android.app.Activity
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
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.billing.SubscriptionTier
import dev.scrybe.core.common.ScrybeLayoutDefaults

private data class UsageItem(
    val label: String,
    val value: Int,
    val icon: ImageVector,
)

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
                if (hasPro) {
                    ProActivePlanCard(onRestore = viewModel::restorePurchases)
                    ProUsageCard()
                } else {
                    PlanPickerSection(
                        selectedPlan = selectedPlan,
                        onSelectPlan = { selectedPlan = it },
                        onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
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
        TierCard(
            label = "Try it",
            price = "—",
            priceNote = "No account needed",
            items =
                listOf(
                    "Record and transcribe right away",
                    "Try AI summaries and profiles",
                    "Local storage on your device",
                ),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            isHighlighted = !hasPro,
            modifier = Modifier.weight(1f),
        )
        TierCard(
            label = "Pro",
            price = "\$1.99",
            priceNote = "/mo · billed annually",
            badge = "Zero setup",
            items =
                listOf(
                    "Works immediately — no key config",
                    "We manage transcription + AI",
                    "Priority processing queue",
                    "Automatic provider updates",
                    "Pro support channel",
                ),
            labelColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            isHighlighted = hasPro,
            modifier = Modifier.weight(1f),
        )
        TierCard(
            label = "BYOK",
            price = "Free forever",
            priceNote = "pay providers directly",
            badge = "Full control",
            items =
                listOf(
                    "All Scrybe features included",
                    "Connect your own API keys",
                    "Choose your preferred models",
                    "Pay providers at their own rates",
                ),
            labelColor = MaterialTheme.colorScheme.secondary,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            isHighlighted = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TierCard(
    label: String,
    price: String,
    priceNote: String,
    items: List<String>,
    labelColor: Color,
    containerColor: Color,
    isHighlighted: Boolean,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (isHighlighted) {
            labelColor.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            if (badge != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = labelColor,
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
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = labelColor)
            Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                priceNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            items.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 3.dp),
                ) {
                    Text("•", style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Text(item, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
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
                    onClick = {},
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
private fun ProUsageCard() {
    val items =
        listOf(
            UsageItem("Transcription minutes", 142, Icons.Filled.Mic),
            UsageItem("AI transforms", 38, Icons.Filled.AutoAwesome),
            UsageItem("Sessions this month", 24, Icons.Filled.Folder),
        )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "This month's usage",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        item.value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
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
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp).padding(top = 1.dp),
            )
            Text(
                text =
                    "BYOK has the same capability as Pro. If you already have an OpenAI account, " +
                        "configure your key in Settings → AI configuration. You'll use the same features and pay OpenAI directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
