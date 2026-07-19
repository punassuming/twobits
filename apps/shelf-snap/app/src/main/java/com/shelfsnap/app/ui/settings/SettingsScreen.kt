package com.shelfsnap.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.BuildConfig
import com.shelfsnap.app.R
import com.twobits.billing.SubscriptionTier
import com.twobits.design.components.AppLabeledSectionCard
import com.twobits.design.components.SettingsAppInfoSection
import com.twobits.design.components.SettingsProStatusCard

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onWhatsNew: () -> Unit = {},
    onAiConfig: () -> Unit = {},
    onNavigateToPro: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsProStatusCard(
                appName = "Shelf Snap",
                isPro = uiState.subscriptionTier is SubscriptionTier.Pro,
                upgradeLabel = "Upgrade to Pro — \$1.99 / month",
                upgradeDescription = "Skip the API key — Pro includes managed OpenAI access so vision analysis and price research work out of the box.",
                activeDescription = "You have Pro. Managed API keys are active — no personal key required.",
                isPurchasing = uiState.isPurchasing,
                purchaseError = uiState.purchaseError,
                onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                onRestore = viewModel::restorePurchases,
                onDismissError = viewModel::dismissPurchaseError,
                onDetails = onNavigateToPro,
            )

            AiConfigCard(onClick = onAiConfig)

            // ── Capture & storage ──────────────────────────────────────────
            AppLabeledSectionCard(
                icon = Icons.Default.PhotoCamera,
                title = stringResource(R.string.capture_storage_section),
            ) {
                SettingToggle(
                    title = stringResource(R.string.auto_analyze_title),
                    subtitle = stringResource(R.string.auto_analyze_subtitle),
                    checked = uiState.autoAnalyze,
                    onCheckedChange = viewModel::onAutoAnalyzeChange,
                )
                StorageBreakdownCard(storage = uiState.storage)
            }

            // ── About ──────────────────────────────────────────────────────
            SettingsAppInfoSection(
                versionLabel = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                subtitle = stringResource(R.string.about_privacy),
                onWhatsNew = onWhatsNew,
            )
        }
    }
}

@Composable
private fun AiConfigCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.27f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI configuration",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Vision model · pricing · web search · local models · API key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StorageBreakdownCard(storage: StorageInfo) {
    val total = storage.totalBytes.coerceAtLeast(1L)
    val photoFraction = (storage.photosBytes.toFloat() / total).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.storage_usage),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = photoFraction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.storage_photos) + " · " + formatBytes(storage.photosBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.storage_database) + " · " + formatBytes(storage.dbBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.storage_total, formatBytes(storage.totalBytes)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Formats a byte count as a compact human-readable size (B/KB/MB/GB). */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
