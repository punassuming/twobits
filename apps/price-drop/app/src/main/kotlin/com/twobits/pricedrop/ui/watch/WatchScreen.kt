package com.twobits.pricedrop.ui.watch

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.pricedrop.data.model.WatchedProduct
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    onNavigateToProduct: (Long) -> Unit,
    onNavigateToDrops: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToBarcode: () -> Unit,
    onNavigateToAsk: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val watchlist by viewModel.watchlist.collectAsState()
    val dropCount by viewModel.activeDropCount.collectAsState()
    val filter by viewModel.activeFilter.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PriceDrop", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onNavigateToDrops) {
                        BadgedBox(badge = { if (dropCount > 0) Badge { Text("$dropCount") } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Drops")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add or ask") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val filters = listOf("All", "Drops", "Coupons", "Big drops")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters) { chip ->
                    FilterChip(
                        selected = filter == chip,
                        onClick = { viewModel.setFilter(chip) },
                        label = { Text(chip) },
                    )
                }
            }
            if (watchlist.isEmpty()) {
                EmptyWatchlist()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(watchlist, key = { it.id }) { product ->
                        WatchCard(
                            product = product,
                            onClick = { onNavigateToProduct(product.id) },
                            onRemove = { viewModel.removeItem(product.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddOrAskSheet(
            onDismiss = { showAddSheet = false },
            onSearch = {
                showAddSheet = false
                onNavigateToSearch()
            },
            onBarcode = {
                showAddSheet = false
                onNavigateToBarcode()
            },
            onAsk = {
                showAddSheet = false
                onNavigateToAsk()
            },
        )
    }
}

@Composable
private fun EmptyWatchlist() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("No watched products", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap 'Add or ask' to start tracking prices", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WatchCard(
    product: WatchedProduct,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    val pctChange =
        if (product.trackedHigh > 0 && product.currentPrice > 0) {
            ((product.currentPrice - product.trackedHigh) / product.trackedHigh * 100).toInt()
        } else {
            0
        }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    fmt.format(product.currentPrice),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (product.targetPrice != null) {
                    Text(
                        "Target: ${fmt.format(product.targetPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pctChange < 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$pctChange% vs high") },
                        icon = { Icon(Icons.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrAskSheet(
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onBarcode: () -> Unit,
    onAsk: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Add or ask", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple(Icons.Filled.Search, "Search by name or URL", onSearch),
                Triple(Icons.Filled.QrCodeScanner, "Scan barcode", onBarcode),
                Triple(Icons.Filled.AutoAwesome, "Ask AI", onAsk),
            ).forEach { (icon, label, action) ->
                ListItem(
                    leadingContent = { Icon(icon, contentDescription = null) },
                    headlineContent = { Text(label) },
                    modifier = Modifier.clickable { action() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
