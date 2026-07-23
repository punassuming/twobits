package com.twobits.pricedrop.ui.drops

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.pricedrop.data.model.Drop
import java.text.NumberFormat
import java.util.Locale

private data class DropSection(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val drops: List<Drop>,
)

@Composable
private fun buildSections(drops: List<Drop>): List<DropSection> {
    val readyToBuy = drops.filter { it.type == "below_target" || it.type == "back_in_stock" }
    val coupons = drops.filter { it.type == "coupon" }
    val bigDrops = drops.filter { it.type == "big_drop" }
    val historicalLows = drops.filter { it.type == "historical_low" }
    return buildList {
        if (readyToBuy.isNotEmpty()) {
            add(DropSection("Ready to buy", Icons.Filled.ShoppingCart, Color(0xFF4CAF50), readyToBuy))
        }
        if (coupons.isNotEmpty()) {
            add(DropSection("New coupons", Icons.Filled.LocalOffer, Color(0xFFFFC107), coupons))
        }
        if (bigDrops.isNotEmpty()) {
            add(DropSection("Big drops", Icons.Filled.TrendingDown, Color(0xFFF44336), bigDrops))
        }
        if (historicalLows.isNotEmpty()) {
            add(DropSection("Historical lows", Icons.Filled.History, Color(0xFF2196F3), historicalLows))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProduct: (Long) -> Unit,
    viewModel: DropsViewModel = hiltViewModel(),
) {
    val drops by viewModel.drops.collectAsState()
    val sections = buildSections(drops)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Drops") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (drops.isNotEmpty()) {
                        TextButton(onClick = { viewModel.dismissAll() }) {
                            Text("Mark all done")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (drops.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No active drops",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "We'll alert you when prices fall",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sections.forEach { section ->
                    item(key = "header_${section.title}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(section.icon, contentDescription = null, tint = section.tint, modifier = Modifier.size(16.dp))
                            Text(
                                section.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = section.tint,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "(${section.drops.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(section.drops, key = { it.id }) { drop ->
                        DropCard(
                            drop = drop,
                            sectionTint = section.tint,
                            onClick = { onNavigateToProduct(drop.productId) },
                            onDismiss = { viewModel.dismiss(drop.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropCard(
    drop: Drop,
    sectionTint: Color,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    val label =
        when (drop.type) {
            "below_target" -> "Below target"
            "coupon" -> "Coupon available"
            "big_drop" -> "Big drop"
            "historical_low" -> "Historical low"
            "back_in_stock" -> "Back in stock"
            else -> "Price drop"
        }
    val icon =
        when (drop.type) {
            "below_target", "back_in_stock" -> Icons.Filled.CheckCircle
            "coupon" -> Icons.Filled.LocalOffer
            "historical_low" -> Icons.Filled.History
            else -> Icons.Filled.TrendingDown
        }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = sectionTint, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = sectionTint)
            }
            if (drop.newPrice != null && drop.oldPrice != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fmt.format(drop.newPrice),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = sectionTint,
                    )
                    Text(
                        "was ${fmt.format(drop.oldPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (drop.couponCode.isNotBlank()) {
                Text(
                    "Code: ${drop.couponCode} — ${drop.couponDiscount}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
