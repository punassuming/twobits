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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.twobits.pricedrop.data.model.Activity
import com.twobits.pricedrop.data.model.ActivityType
import com.twobits.pricedrop.data.model.Coupon
import com.twobits.pricedrop.data.model.CouponState
import com.twobits.pricedrop.data.model.DiscountType
import com.twobits.pricedrop.data.model.Offer
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.domain.EffectivePrice
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class ChartRange(val label: String, val millis: Long?) {
    WEEK("7D", TimeUnit.DAYS.toMillis(7)),
    MONTH("30D", TimeUnit.DAYS.toMillis(30)),
    QUARTER("90D", TimeUnit.DAYS.toMillis(90)),
    ALL("All", null),
}

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
                    IconButton(onClick = { viewModel.refresh(productId) }, enabled = !uiState.isRefreshing) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(2.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
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
                    shipping = product.shipping,
                    fees = product.fees,
                    targetPrice = product.targetPrice,
                    lowestPrice = product.trackedLow.takeIf { it < Double.MAX_VALUE },
                    fmt = fmt,
                )
            }
            if (uiState.offers.isNotEmpty()) {
                item { SectionHeader("Other sellers") }
                items(uiState.offers, key = { "offer-${it.id}" }) { offer ->
                    OfferRow(offer = offer, fmt = fmt)
                }
            }
            if (uiState.priceHistory.size >= 2) {
                item {
                    PriceHistoryCard(
                        history = uiState.priceHistory,
                        targetPrice = product.targetPrice,
                        trackedLow = product.trackedLow.takeIf { it < Double.MAX_VALUE },
                        trackedAvg = product.trackedAvg.takeIf { it > 0.0 },
                        trackedHigh = product.trackedHigh.takeIf { it > 0.0 },
                        fmt = fmt,
                    )
                }
            }
            if (uiState.coupons.isNotEmpty()) {
                item { SectionHeader("Coupons") }
                items(uiState.coupons, key = { "coupon-${it.id}" }) { coupon ->
                    CouponCard(coupon = coupon)
                }
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
            if (uiState.activity.isNotEmpty()) {
                item { SectionHeader("Activity") }
                items(uiState.activity, key = { "activity-${it.id}" }) { entry ->
                    ActivityRow(entry = entry)
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
private fun PriceOverviewCard(
    currentPrice: Double,
    shipping: Double,
    fees: Double,
    targetPrice: Double?,
    lowestPrice: Double?,
    fmt: NumberFormat,
) {
    val extras = shipping + fees
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current price", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(fmt.format(currentPrice), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (extras > 0.0) {
                val effective = EffectivePrice.compute(base = currentPrice, shipping = shipping, fees = fees)
                Text(
                    "Effective ${fmt.format(effective)} incl. ${fmt.format(extras)} shipping & fees",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
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
private fun OfferRow(
    offer: Offer,
    fmt: NumberFormat,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    offer.seller.ifBlank { offer.retailer },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                val shippingLine =
                    if (offer.shipping > 0.0) {
                        "+ ${fmt.format(offer.shipping)} shipping"
                    } else {
                        "Free shipping"
                    }
                Text(
                    shippingLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    fmt.format(offer.effectivePrice),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val availLabel =
                    when (offer.availability) {
                        "in_stock" -> "In stock"
                        "out_of_stock" -> "Out of stock"
                        else -> null
                    }
                if (availLabel != null) {
                    Text(
                        availLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceHistoryCard(
    history: List<PriceEvent>,
    targetPrice: Double?,
    trackedLow: Double?,
    trackedAvg: Double?,
    trackedHigh: Double?,
    fmt: NumberFormat,
) {
    var range by remember { mutableStateOf(ChartRange.ALL) }
    val now = System.currentTimeMillis()
    val filtered =
        remember(history, range) {
            val cutoff = range.millis
            val subset = if (cutoff == null) history else history.filter { it.recordedAt >= now - cutoff }
            subset.ifEmpty { history }
        }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Price history", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { range = option },
                        label = { Text(option.label) },
                    )
                }
            }
            PriceHistoryChart(history = filtered, targetPrice = targetPrice)
            MetricsRow(
                current = filtered.lastOrNull()?.price,
                low = trackedLow,
                avg = trackedAvg,
                high = trackedHigh,
                fmt = fmt,
            )
        }
    }
}

@Composable
private fun PriceHistoryChart(history: List<PriceEvent>, targetPrice: Double?) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(history, targetPrice) {
        modelProducer.runTransaction {
            lineSeries {
                series(history.map { it.price })
                series(history.map { it.effectivePrice })
                if (targetPrice != null) series(history.map { targetPrice })
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(rememberLineCartesianLayer()),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(160.dp),
    )
}

@Composable
private fun MetricsRow(
    current: Double?,
    low: Double?,
    avg: Double?,
    high: Double?,
    fmt: NumberFormat,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("Current", current?.let { fmt.format(it) })
        Metric("Low", low?.let { fmt.format(it) })
        Metric("Avg", avg?.let { fmt.format(it) })
        Metric("High", high?.let { fmt.format(it) })
    }
}

@Composable
private fun Metric(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CouponCard(coupon: Coupon) {
    val clipboard = LocalClipboardManager.current
    val state = CouponState.fromValue(coupon.state)
    val (stateLabel, stateColor) =
        when (state) {
            CouponState.TESTED_VALID -> "Valid" to MaterialTheme.colorScheme.primary
            CouponState.UNVERIFIED -> "Untested" to MaterialTheme.colorScheme.secondary
            CouponState.RESTRICTED -> "Restricted" to MaterialTheme.colorScheme.error
            CouponState.EXPIRED -> "Expired" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateBadge(label = stateLabel, color = stateColor)
                Text(discountLabel(coupon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (coupon.description.isNotBlank()) {
                Text(coupon.description, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(coupon.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(coupon.code)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

private fun discountLabel(coupon: Coupon): String =
    when (DiscountType.fromValue(coupon.discountType)) {
        DiscountType.PERCENT -> "${coupon.discountValue.toInt()}% off"
        DiscountType.FIXED -> "\$${"%.2f".format(coupon.discountValue)} off"
        DiscountType.UNKNOWN -> "Coupon"
    }

@Composable
private fun StateBadge(label: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActivityRow(entry: Activity) {
    val label =
        when (ActivityType.fromValue(entry.type)) {
            ActivityType.ADDED -> "Added to watchlist"
            ActivityType.CHECKED -> "Price checked"
            ActivityType.DROPPED -> "Price dropped"
            ActivityType.COUPON_FOUND -> "Coupon found"
            ActivityType.ALERT_SENT -> "Alert sent"
            ActivityType.OPENED -> "Opened"
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (entry.detail.isNotBlank()) {
                Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(relativeTime(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
}
