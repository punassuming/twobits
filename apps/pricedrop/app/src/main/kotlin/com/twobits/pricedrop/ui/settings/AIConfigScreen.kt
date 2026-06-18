package com.twobits.pricedrop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val FEATURE_SECTIONS = listOf(
    Triple(Icons.Filled.Search, "Search", "SerpAPI Google Shopping"),
    Triple(Icons.Filled.LocalOffer, "Coupons", "CouponLayer"),
    Triple(Icons.Filled.Notifications, "Drops", "Rainforest + Keepa"),
    Triple(Icons.Filled.AutoAwesome, "Ask AI", "OpenAI via TwoBits proxy"),
    Triple(Icons.Filled.ShoppingCart, "Price check", "Rainforest API"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Configuration") },
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
                SectionCard2("API endpoint") {
                    ListItem(
                        headlineContent = { Text("Base URL") },
                        supportingContent = { Text(uiState.apiBaseUrl, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
            item {
                SectionCard2("Features") {
                    Column {
                        FEATURE_SECTIONS.forEach { (icon, name, provider) ->
                            FeatureRow(icon = icon, name = name, provider = provider)
                        }
                    }
                }
            }
            item { CallBudgetSection() }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, name: String, provider: String) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(name) },
        supportingContent = { Text(provider, style = MaterialTheme.typography.bodySmall) },
    )
}

@Composable
private fun CallBudgetSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monthly call budget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("$2.74 of $3.00 used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { 2.74f / 3.00f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BudgetStat("Search", "42")
                BudgetStat("Price", "18")
                BudgetStat("Coupons", "31")
                BudgetStat("Barcode", "7")
            }
        }
    }
}

@Composable
private fun BudgetStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard2(title: String, content: @Composable () -> Unit) {
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
