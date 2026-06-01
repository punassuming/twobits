package com.shelfsnap.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.ui.components.ItemThumb
import com.shelfsnap.app.ui.components.brandColor
import com.shelfsnap.app.ui.components.icon
import com.shelfsnap.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onSummaryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_inventory)) },
                actions = {
                    IconButton(onClick = onSummaryClick) {
                        Icon(Icons.Default.Summarize, contentDescription = stringResource(R.string.donation_summary))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItem,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_item)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_items)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter chips (All / Listed / Unlisted / Drafts)
            if (!uiState.isLoading) {
                InventoryFilterRow(
                    selected = uiState.filter,
                    totalCount = uiState.totalCount,
                    listedCount = uiState.listedCount,
                    draftCount = uiState.draftCount,
                    onFilterChange = viewModel::onFilterChange
                )
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (uiState.searchQuery.isNotBlank())
                                stringResource(R.string.no_items_match_search)
                            else
                                stringResource(R.string.no_items),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Nudge users to configure the API key before they hit an
                        // analysis error on their first capture.
                        if (!uiState.hasApiKey) {
                            FilledTonalButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.setup_api_key_nudge))
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        InventoryItemCard(
                            item = item,
                            onClick = { onItemClick(item.id) },
                            onDelete = { viewModel.deleteItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: Item,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val estimateColor = LocalEstimateLabel.current

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo-or-category-icon thumbnail with photo-count badge
            ItemThumb(item = item, size = 64.dp, showCount = true)

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.category.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isDraft) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) { Text(stringResource(R.string.draft_label)) }
                    }
                    ListingStatusPill(item = item)
                }
                // Brand · Model subtitle
                if (item.brand.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (item.model.isNotBlank()) "${item.brand} · ${item.model}" else item.brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Condition as colored chip
                    ConditionBadge(condition = item.condition)
                    // Story 4 – Valuation suggestion, labeled as estimate
                    Text(
                        text = "$" + "%.2f".format(item.estimatedValue) + " *",
                        style = MaterialTheme.typography.labelMedium,
                        color = estimateColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    PlatformDots(item = item)
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Horizontal, scrollable row of inventory filter chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryFilterRow(
    selected: InventoryFilter,
    totalCount: Int,
    listedCount: Int,
    draftCount: Int,
    onFilterChange: (InventoryFilter) -> Unit
) {
    val chips = listOf(
        InventoryFilter.ALL to stringResource(R.string.filter_all, totalCount),
        InventoryFilter.LISTED to stringResource(R.string.filter_listed, listedCount),
        InventoryFilter.UNLISTED to stringResource(R.string.filter_unlisted),
        InventoryFilter.DRAFT to stringResource(R.string.filter_drafts, draftCount)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onFilterChange(mode) },
                label = { Text(label) }
            )
        }
    }
}

/** A small Listed/Sold pill summarizing an item's listing state. */
@Composable
private fun ListingStatusPill(item: Item) {
    when {
        item.hasSold -> StatusPill(
            label = stringResource(R.string.status_sold),
            icon = Icons.Default.CheckCircle,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )

        item.hasActiveListing -> StatusPill(
            label = stringResource(R.string.status_listed),
            icon = Icons.Default.Sell,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content
            )
        }
    }
}

/** Row of small colored dots, one per platform the item is listed on. */
@Composable
private fun PlatformDots(item: Item) {
    val platforms = item.listings.mapNotNull { Platform.fromKey(it.platformKey) }.distinct()
    if (platforms.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        platforms.forEach { platform ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(platform.brandColor().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    platform.icon(),
                    contentDescription = platform.displayName,
                    tint = platform.brandColor(),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/** The accent color for a condition, shared by the badge and the condition selector. */
fun conditionColor(condition: Condition): androidx.compose.ui.graphics.Color = when (condition) {
    Condition.EXCELLENT -> ConditionExcellent
    Condition.GOOD -> ConditionGood
    Condition.FAIR -> ConditionFair
    Condition.POOR -> ConditionPoor
}

/** Displays the item condition as a small colored chip. */
@Composable
internal fun ConditionBadge(condition: Condition) {
    val color = conditionColor(condition)
    val label = condition.name.lowercase().replaceFirstChar { it.uppercase() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.height(24.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
