package com.twobits.pricedrop.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.twobits.pricedrop.data.model.PriceEvent
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(productId) { viewModel.load(productId) }

    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    var showTargetEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.product?.title ?: "Product", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTargetEditor = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit target")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val product = uiState.product ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PriceOverviewCard(
                    currentPrice = product.currentPrice,
                    targetPrice = product.targetPrice,
                    lowestPrice = product.trackedLow.takeIf { it < Double.MAX_VALUE },
                    fmt = fmt,
                )
            }
            if (uiState.priceHistory.size >= 2) {
                item { PriceHistoryChart(history = uiState.priceHistory) }
            }
            if (uiState.drops.isNotEmpty()) {
                item {
                    SectionHeader("Active drops")
                    uiState.drops.forEach { drop ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(drop.type.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                if (drop.newPrice != null) Text(fmt.format(drop.newPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (drop.couponCode.isNotBlank()) Text("Code: ${drop.couponCode}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.dismissAllDrops(productId) }) { Text("Dismiss all") }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showTargetEditor) {
        var input by remember { mutableStateOf(uiState.product?.targetPrice?.toString() ?: "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTargetEditor = false },
            title = { Text("Set target price") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Target price") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    viewModel.updateTargetPrice(productId, input.toDoubleOrNull())
                    showTargetEditor = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTargetEditor = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PriceOverviewCard(currentPrice: Double, targetPrice: Double?, lowestPrice: Double?, fmt: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current price", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(fmt.format(currentPrice), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (targetPrice != null) {
                    PriceLabel("Target", fmt.format(targetPrice))
                }
                if (lowestPrice != null) {
                    PriceLabel("All-time low", fmt.format(lowestPrice))
                }
            }
        }
    }
}

@Composable
private fun PriceLabel(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PriceHistoryChart(history: List<PriceEvent>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(history) {
        modelProducer.runTransaction {
            lineSeries { series(history.map { it.price }) }
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Text("Price history", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
}
