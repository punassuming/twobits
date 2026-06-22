package com.twobits.pricedrop.ui.pro

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.ProTierCard
import com.twobits.pricedrop.ui.settings.SettingsViewModel

private val FREE_FEATURES = listOf("5 watched products", "Daily price checks", "Basic drop alerts")
private val PRO_FEATURES =
    listOf(
        "Unlimited watched products",
        "Hourly price checks",
        "Coupons + historical data",
        "AI Ask",
        "Barcode scanning",
        "Priority support",
    )
private val BYOK_FEATURES = listOf("All Pro features", "Use your own API keys", "No monthly spend cap")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    onNavigateToByok: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    var selectedPlan by remember { mutableStateOf("annual") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PriceDrop Pro") },
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("annual" to "Annual – $4.99/mo", "monthly" to "Monthly – $5.99/mo").forEach { (value, label) ->
                        FilterChip(
                            selected = selectedPlan == value,
                            onClick = { selectedPlan = value },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                ProTierCard(
                    title = "Try it free",
                    price = "Free",
                    features = FREE_FEATURES,
                    ctaLabel = "Current plan",
                    ctaEnabled = false,
                    onCta = {},
                )
            }
            item {
                ProTierCard(
                    title = "Pro",
                    price = if (selectedPlan == "annual") "$4.99/mo" else "$5.99/mo",
                    features = PRO_FEATURES,
                    isHighlighted = true,
                    ctaLabel = "Upgrade to Pro",
                    ctaEnabled = !uiState.isPurchasing,
                    isLoading = uiState.isPurchasing,
                    onCta = { activity?.let { viewModel.startProPurchase(it, selectedPlan) } },
                )
            }
            item {
                ProTierCard(
                    title = "BYOK",
                    price = "Your API costs",
                    features = BYOK_FEATURES,
                    ctaLabel = "Set up BYOK",
                    onCta = onNavigateToByok,
                )
            }
            if (uiState.purchaseError != null) {
                item {
                    Text(
                        uiState.purchaseError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::dismissPurchaseError) { Text("Dismiss") }
                }
            }
        }
    }
}
