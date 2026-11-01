package com.twobits.pricedrop.ui.watch

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.TwoBitsSpacing
import com.twobits.design.components.AppChipRow
import com.twobits.design.components.AppEmptyState
import com.twobits.pricedrop.data.model.WatchedProduct
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val totalCount by viewModel.totalCount.collectAsState()
    val dropCount by viewModel.activeDropCount.collectAsState()
    val filter by viewModel.activeFilter.collectAsState()
    val refreshingId by viewModel.refreshingId.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        when (message) {
            is WatchMessage.Removed -> {
                val result =
                    snackbarHostState.showSnackbar(
                        message = "Removed “${message.product.title.ellipsize(MAX_SNACKBAR_TITLE_CHARS)}”",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(message.product)
            }

            is WatchMessage.RefreshFailed -> {
                val result =
                    snackbarHostState.showSnackbar(
                        message = "Couldn't check prices. Check your connection and try again.",
                        actionLabel = "Retry",
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) viewModel.refreshItem(message.productId, force = true)
            }
        }
        viewModel.messageShown(message)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            if (totalCount > 0) {
                val filters = listOf("All", "Below target", "Coupons", "Needs check", "Paused")
                AppChipRow(verticalPadding = 8.dp) {
                    filters.forEach { chip ->
                        FilterChip(
                            selected = filter == chip,
                            onClick = { viewModel.setFilter(chip) },
                            label = { Text(chip) },
                        )
                    }
                }
            }
            if (watchlist.isEmpty()) {
                if (totalCount == 0) {
                    AppEmptyState(
                        icon = Icons.Filled.Bookmark,
                        title = "Watch your first product",
                        subtitle =
                            "Search by name or URL, scan a barcode, or ask AI — " +
                                "PriceDrop alerts you when the price falls.",
                        primaryActionLabel = "Add your first product",
                        onPrimaryAction = { showAddSheet = true },
                    )
                } else {
                    AppEmptyState(
                        icon = Icons.Filled.Search,
                        title = "No products match this filter",
                        primaryActionLabel = "Show all",
                        onPrimaryAction = { viewModel.setFilter("All") },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(watchlist, key = { it.id }) { product ->
                        WatchCard(
                            product = product,
                            isRefreshing = refreshingId == product.id,
                            onClick = { onNavigateToProduct(product.id) },
                            onRemove = { viewModel.removeItem(product.id) },
                            onPause = { viewModel.pauseItem(product.id) },
                            onResume = { viewModel.resumeItem(product.id) },
                            onRefresh = { viewModel.refreshItem(product.id) },
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
private fun WatchCard(
    product: WatchedProduct,
    isRefreshing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRefresh: () -> Unit,
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    val context = LocalContext.current
    val pctChange =
        if (product.trackedHigh > 0 && product.currentPrice > 0) {
            ((product.currentPrice - product.trackedHigh) / product.trackedHigh * 100).toInt()
        } else {
            0
        }
    val isBelowTarget =
        product.targetPrice != null &&
            product.currentPrice > 0 &&
            product.currentPrice <= product.targetPrice
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        fmt.format(product.currentPrice),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isBelowTarget) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                    if (product.targetPrice != null) {
                        Text(
                            "Target: ${fmt.format(product.targetPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isBelowTarget) {
                        val savings = product.targetPrice!! - product.currentPrice
                        StatusBadge(
                            text = "Below target · save ${fmt.format(savings)}",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    } else if (pctChange < 0) {
                        StatusBadge(
                            text = "$pctChange% vs high",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = Icons.Filled.TrendingDown,
                        )
                    }
                    if (!product.isActive) {
                        Text(
                            "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = {
                                if (isRefreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            },
                        )
                        if (product.isActive) {
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                leadingIcon = { Icon(Icons.Filled.Pause, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onPause()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onResume()
                                },
                            )
                        }
                        if (product.productUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Open retailer") },
                                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.productUrl)))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            LastCheckedRow(product = product, isRefreshing = isRefreshing, onRefresh = onRefresh)
        }
    }
}

/** Non-interactive status label (a chip with a no-op click misleads TalkBack and touch users). */
@Composable
private fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor, contentColor = contentColor) {
        Row(
            modifier = Modifier.padding(horizontal = TwoBitsSpacing.item, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private const val MAX_SNACKBAR_TITLE_CHARS = 40

private fun String.ellipsize(maxChars: Int): String = if (length <= maxChars) this else take(maxChars - 1).trimEnd() + "…"

@Composable
private fun LastCheckedRow(
    product: WatchedProduct,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val lastCheckedLabel =
        when {
            product.lastCheckedAt == 0L -> {
                "Not checked yet — tap to check now"
            }

            else -> {
                val diffMs = now - product.lastCheckedAt
                val relative =
                    when {
                        diffMs < TimeUnit.MINUTES.toMillis(2) -> "just now"
                        diffMs < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}m ago"
                        diffMs < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diffMs)}h ago"
                        else -> "${TimeUnit.MILLISECONDS.toDays(diffMs)}d ago"
                    }
                "Last checked $relative"
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        } else {
            IconButton(onClick = onRefresh, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            lastCheckedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Column(
            Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Add or ask",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
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
