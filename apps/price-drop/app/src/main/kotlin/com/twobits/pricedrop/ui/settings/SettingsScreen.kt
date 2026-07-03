package com.twobits.pricedrop.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AppLabeledSectionCard
import com.twobits.design.components.SettingsAppInfoSection
import com.twobits.design.components.SettingsProStatusCard
import com.twobits.pricedrop.BuildConfig
import com.twobits.pricedrop.data.settings.SettingsPrefs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPro: () -> Unit,
    onNavigateToAiConfig: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(viewModel) {
        viewModel.exportEvent.collect { json ->
            val intent =
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "PriceDrop Watchlist Export")
                        putExtra(Intent.EXTRA_TEXT, json)
                    },
                    "Export watchlist",
                )
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsProStatusCard(
                    appName = "PriceDrop",
                    isPro = uiState.hasPro,
                    upgradeLabel = "Upgrade — \$2.99/mo",
                    upgradeDescription = "Shopping search · coupons · AI — no keys needed.",
                    activeDescription = "Managed connectors are active — no key setup needed.",
                    isPurchasing = uiState.isPurchasing,
                    purchaseError = uiState.purchaseError,
                    onUpgrade = { activity?.let { viewModel.startProPurchase(it) } },
                    onRestore = viewModel::restorePurchases,
                    onDismissError = viewModel::dismissPurchaseError,
                    onDetails = onNavigateToPro,
                )
            }
            item {
                AiConfigEntry(onClick = onNavigateToAiConfig)
            }
            item {
                AppLabeledSectionCard(
                    title = "Tracking",
                    icon = Icons.Filled.Schedule,
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text("Check frequency") },
                        supportingContent = { Text("Every ${uiState.checkFrequencyHours}h") },
                    )
                    Slider(
                        value = uiState.checkFrequencyHours.toFloat(),
                        onValueChange = { viewModel.setCheckFrequency((it / 4f).roundToInt() * 4) },
                        valueRange = SettingsPrefs.MIN_CHECK_FREQ_HOURS.toFloat()..SettingsPrefs.MAX_CHECK_FREQ_HOURS.toFloat(),
                        steps = 22,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Wi-Fi only") },
                        supportingContent = { Text("Only check prices on Wi-Fi") },
                        trailingContent = {
                            Switch(checked = uiState.wifiOnly, onCheckedChange = viewModel::setWifiOnly)
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Only while charging") },
                        trailingContent = {
                            Switch(
                                checked = uiState.onlyWhileCharging,
                                onCheckedChange = viewModel::setChargingOnly,
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Quiet hours") },
                        supportingContent = { Text("No notifications 10 PM – 8 AM") },
                        trailingContent = {
                            Switch(
                                checked = uiState.quietHoursEnabled,
                                onCheckedChange = viewModel::setQuietHours,
                            )
                        },
                    )
                }
            }
            item {
                AppLabeledSectionCard(
                    title = "Privacy",
                    icon = Icons.Filled.Lock,
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text("Local-first storage") },
                        supportingContent = { Text("All data stays on this device. No account required.") },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Clear search history") },
                        trailingContent = {
                            TextButton(onClick = viewModel::clearSearchHistory) { Text("Clear") }
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Export data") },
                        supportingContent = { Text("Share watchlist as JSON") },
                        trailingContent = {
                            TextButton(onClick = viewModel::exportData) { Text("Export") }
                        },
                    )
                }
            }
            item {
                SettingsAppInfoSection(
                    versionLabel = "PriceDrop v${BuildConfig.VERSION_NAME}",
                    subtitle = "Local-first. Your data stays on this device.",
                    onWhatsNew = onNavigateToWhatsNew,
                )
            }
        }
    }
}

@Composable
private fun AiConfigEntry(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI configuration",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Providers · models · API keys · call budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
