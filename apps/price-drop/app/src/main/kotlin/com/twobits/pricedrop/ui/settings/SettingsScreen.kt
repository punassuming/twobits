package com.twobits.pricedrop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    onClick = onNavigateToPro,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("PriceDrop Pro", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Unlimited tracking · AI research · coupons") },
                        trailingContent = { TextButton(onClick = onNavigateToPro) { Text("See plans") } },
                    )
                }
            }
            item {
                Card(
                    onClick = onNavigateToAiConfig,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        headlineContent = { Text("AI configuration") },
                        supportingContent = { Text("API provider · search · coupons · budget") },
                    )
                }
            }
            item {
                SectionCard(title = "Tracking preferences") {
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
                            trailingContent = { Switch(checked = uiState.wifiOnly, onCheckedChange = viewModel::setWifiOnly) },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Only while charging") },
                            trailingContent = { Switch(checked = uiState.onlyWhileCharging, onCheckedChange = viewModel::setChargingOnly) },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Quiet hours") },
                            supportingContent = { Text("No notifications 10 PM – 8 AM") },
                            trailingContent = { Switch(checked = uiState.quietHoursEnabled, onCheckedChange = viewModel::setQuietHours) },
                        )
                    }
                }
            }
            item {
                SectionCard(title = "Privacy") {
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
                SectionCard(title = "About") {
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
                                    onClick = { uriHandler.openUri("https://punassuming.github.io/twobits/privacy.html") },
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
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            content()
        }
    }
}
