package com.twobits.pricedrop.ui.settings

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AppSectionLabel
import com.twobits.pricedrop.BuildConfig

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
                ProBanner(
                    hasPro = uiState.hasPro,
                    onUpgrade = onNavigateToPro,
                    onDetails = onNavigateToPro,
                )
            }
            item {
                AiConfigEntry(onClick = onNavigateToAiConfig)
            }
            item {
                SectionCard(title = "Tracking", icon = Icons.Filled.Schedule) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Check frequency") },
                            supportingContent = { Text("Every ${uiState.checkFrequencyHours}h") },
                        )
                        Slider(
                            value = uiState.checkFrequencyHours.toFloat(),
                            onValueChange = { viewModel.setCheckFrequency(it.toInt()) },
                            valueRange = 1f..24f,
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
            }
            item {
                SectionCard(title = "Privacy", icon = Icons.Filled.Lock) {
                    Column {
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
            }
            item {
                val uriHandler = LocalUriHandler.current
                SectionCard(title = "About", icon = Icons.Filled.Info) {
                    Column {
                        ListItem(
                            headlineContent = { Text("PriceDrop v${BuildConfig.VERSION_NAME}") },
                            supportingContent = { Text("Local-first. Your data stays on this device.") },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("What's new") },
                            supportingContent = { Text("Recent changes & release notes") },
                            trailingContent = {
                                TextButton(onClick = onNavigateToWhatsNew) { Text("See notes") }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Privacy policy") },
                            supportingContent = { Text("punassuming.github.io/twobits/privacy") },
                            trailingContent = {
                                TextButton(
                                    onClick = {
                                        uriHandler.openUri("https://punassuming.github.io/twobits/privacy.html")
                                    },
                                ) { Text("Open") }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProBanner(
    hasPro: Boolean,
    onUpgrade: () -> Unit,
    onDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (hasPro) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(26.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPro) "PriceDrop Pro — Active" else "PriceDrop Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            if (hasPro) {
                                "Managed connectors · no key setup"
                            } else {
                                "Shopping search · coupons · AI — no keys needed"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasPro) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (!hasPro) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier.weight(2f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ),
                    ) {
                        Text("Upgrade — \$2.99/mo")
                    }
                    OutlinedButton(
                        onClick = onDetails,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Details")
                    }
                }
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDetails) {
                        Text("Details")
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
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

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppSectionLabel(title, icon, Modifier.padding(start = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column { content() }
        }
    }
}
