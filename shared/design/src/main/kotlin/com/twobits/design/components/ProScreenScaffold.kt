package com.twobits.design.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twobits.design.twoBitsContentWidth

/** Plan id passed to `onUpgrade` for the annual subscription. */
const val PRO_PLAN_ANNUAL: String = "annual"

/** Plan id passed to `onUpgrade` for the monthly subscription. */
const val PRO_PLAN_MONTHLY: String = "monthly"

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * Pricing values for the Pro screen. Apps map their `shared/pro` `AppPlan` into this (design does
 * not depend on `:pro`): `pricing.monthlyLabel`, `pricing.annualPerMonthLabel`, `pricing.annualNote`,
 * `pricing.annualPerMonthPrice` and `monthlySpendCapUsd`.
 */
@Immutable
data class ProScreenPricing(
    val monthlyLabel: String,
    val annualPerMonthLabel: String,
    val annualNote: String,
    val annualPerMonthPrice: String,
    val monthlySpendCapUsd: Double,
)

/** One "Why Pro" bullet. */
@Immutable
data class ProWhyItem(
    val icon: ImageVector,
    val text: String,
)

/**
 * App-specific copy and config for [ProScreenScaffold]. Structure, section order, headers and
 * billing semantics are owned by the scaffold; apps only supply what differs.
 *
 * @param appName e.g. "Scrybe" — the title becomes "Scrybe Pro".
 * @param infraNote the "Powered by the TwoBits shared API…" line.
 * @param spendCapNote explains what pauses when the managed spend cap is reached.
 * @param tryItFeatures / [proFeatures] / [byokFeatures] are the tier-card bullet lists.
 * @param usageMetrics the monthly allowances shown while Pro is active.
 * @param whyPro the "Why Pro" bullets.
 */
@Immutable
data class ProScreenContent(
    val appName: String,
    val pricing: ProScreenPricing,
    val infraNote: String,
    val spendCapNote: String,
    val tryItFeatures: List<String>,
    val proFeatures: List<String>,
    val byokFeatures: List<String>,
    val usageMetrics: List<ProUsageMetric>,
    val whyPro: List<ProWhyItem>,
)

/** Live billing state for [ProScreenScaffold]. */
@Immutable
data class ProScreenState(
    val hasPro: Boolean,
    val isPurchasing: Boolean = false,
    val purchaseError: String? = null,
)

/**
 * Callbacks for [ProScreenScaffold].
 *
 * @param onUpgrade receives [PRO_PLAN_ANNUAL] or [PRO_PLAN_MONTHLY].
 * @param onManageSubscription defaults (null) to opening Google Play's subscriptions page.
 * @param onConfigureByok when set, a "Configure keys →" link is shown under the BYOK note.
 */
@Immutable
data class ProScreenActions(
    val onNavigateBack: () -> Unit,
    val onUpgrade: (plan: String) -> Unit,
    val onRestore: () -> Unit,
    val onDismissError: () -> Unit = {},
    val onManageSubscription: (() -> Unit)? = null,
    val onConfigureByok: (() -> Unit)? = null,
)

/**
 * The shared Pro / upgrade screen used by all three apps: top bar, infra note, Try it / Pro / BYOK
 * comparison, managed spend cap, then either the billing picker (not Pro) or the active-plan card
 * and usage allowances (Pro), purchase error, "Why Pro" and the BYOK note.
 */
@Composable
fun ProScreenScaffold(
    content: ProScreenContent,
    state: ProScreenState,
    actions: ProScreenActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TwoBitsTopAppBar(
                title = "${content.appName} Pro",
                subtitle = if (state.hasPro) "Active · manage billing in Google Play" else "Choose how you want to use AI",
                onNavigateBack = actions.onNavigateBack,
                actions = { if (state.hasPro) ProActivePill() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .twoBitsContentWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProScreenBody(content = content, state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun ProScreenBody(
    content: ProScreenContent,
    state: ProScreenState,
    actions: ProScreenActions,
) {
    ProInfraNote(text = content.infraNote)
    ProTierComparison(content = content, hasPro = state.hasPro)
    ProSpendCapCard(
        capLabel = "Managed spend cap: \$%.2f / month".format(content.pricing.monthlySpendCapUsd),
        note = content.spendCapNote,
    )
    if (state.hasPro) {
        ProActiveCard(
            isPurchasing = state.isPurchasing,
            onRestore = actions.onRestore,
            onManageSubscription = actions.onManageSubscription,
        )
        ProUsageCard(metrics = content.usageMetrics)
    } else {
        ProBillingSection(
            pricing = content.pricing,
            isPurchasing = state.isPurchasing,
            onUpgrade = actions.onUpgrade,
            onRestore = actions.onRestore,
        )
    }
    state.purchaseError?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = actions.onDismissError) { Text("Dismiss") }
    }
    ProWhySection(items = content.whyPro)
    ProByokNote(onConfigureByok = actions.onConfigureByok)
}

@Composable
private fun ProActivePill() {
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

@Composable
private fun ProSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProInfraNote(text: String) {
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
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProTierComparison(
    content: ProScreenContent,
    hasPro: Boolean,
) {
    ProSectionHeader("Plans")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "Try it",
            price = "—",
            priceNote = "No account needed",
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            isHighlighted = !hasPro,
            features = content.tryItFeatures,
        )
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "Pro",
            price = content.pricing.annualPerMonthPrice,
            priceNote = "/mo · billed annually",
            badge = "Zero setup",
            accentColor = MaterialTheme.colorScheme.primary,
            isHighlighted = hasPro,
            features = content.proFeatures,
        )
        ProTierCard(
            modifier = Modifier.weight(1f),
            compact = true,
            title = "BYOK",
            price = "Free forever",
            priceNote = "pay providers directly",
            badge = "Full control",
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            features = content.byokFeatures,
        )
    }
}

@Composable
private fun ProBillingSection(
    pricing: ProScreenPricing,
    isPurchasing: Boolean,
    onUpgrade: (plan: String) -> Unit,
    onRestore: () -> Unit,
) {
    var selectedPlan by rememberSaveable { mutableStateOf(PRO_PLAN_ANNUAL) }
    ProSectionHeader("Billing")
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProPlanOption(
            modifier = Modifier.weight(1f),
            label = "Annual",
            price = pricing.annualPerMonthLabel,
            note = pricing.annualNote,
            badge = "Best value",
            selected = selectedPlan == PRO_PLAN_ANNUAL,
            onClick = { selectedPlan = PRO_PLAN_ANNUAL },
        )
        ProPlanOption(
            modifier = Modifier.weight(1f),
            label = "Monthly",
            price = pricing.monthlyLabel,
            note = "Billed each month",
            badge = null,
            selected = selectedPlan == PRO_PLAN_MONTHLY,
            onClick = { selectedPlan = PRO_PLAN_MONTHLY },
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
private fun ProPlanOption(
    label: String,
    price: String,
    note: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.extraSmall
    Surface(
        modifier =
            modifier
                .clip(shape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = shape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        border = if (selected) BorderStroke(1.5.dp, color.copy(alpha = 0.5f)) else null,
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
    onManageSubscription: (() -> Unit)?,
) {
    val context = LocalContext.current
    val openPlaySubscriptions: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL)))
        }
    }
    val manage = onManageSubscription ?: openPlaySubscriptions
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pro — Active",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Billing and renewal are managed in Google Play",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = manage,
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
private fun ProWhySection(items: List<ProWhyItem>) {
    ProSectionHeader("Why Pro")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(item.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProByokNote(onConfigureByok: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ByokDirectNoteCard()
        if (onConfigureByok != null) {
            TextButton(
                onClick = onConfigureByok,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) { Text("Configure keys →") }
        }
    }
}
