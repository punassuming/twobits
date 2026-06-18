package com.twobits.pricedrop.ui.pro

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val FREE_FEATURES = listOf("5 watched products", "Daily price checks", "Basic drop alerts")
private val PRO_FEATURES = listOf("Unlimited watched products", "Hourly price checks", "Coupons + historical data", "AI Ask", "Barcode scanning", "Priority support")
private val BYOK_FEATURES = listOf("All Pro features", "Use your own API keys", "No monthly spend cap")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(onNavigateBack: () -> Unit) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("annual" to "Annual – $4.99/mo", "monthly" to "Monthly – $5.99/mo").forEach { (value, label) ->
                        FilterChip(
                            selected = selectedPlan == value,
                            onClick = { selectedPlan = value },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item { TierCard(title = "Try it free", price = "Free", features = FREE_FEATURES, isHighlighted = false, ctaLabel = "Current plan", onCta = {}) }
            item { TierCard(title = "Pro", price = if (selectedPlan == "annual") "$4.99/mo" else "$5.99/mo", features = PRO_FEATURES, isHighlighted = true, ctaLabel = "Upgrade to Pro", onCta = {}) }
            item { TierCard(title = "BYOK", price = "Your API costs", features = BYOK_FEATURES, isHighlighted = false, ctaLabel = "Set up BYOK", onCta = {}) }
        }
    }
}

@Composable
private fun TierCard(
    title: String,
    price: String,
    features: List<String>,
    isHighlighted: Boolean,
    ctaLabel: String,
    onCta: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isHighlighted) Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(price, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                features.forEach { feature ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = onCta, modifier = Modifier.fillMaxWidth()) { Text(ctaLabel) }
        }
    }
}
