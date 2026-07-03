package com.shelfsnap.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.displayTitle
import com.shelfsnap.app.ui.components.ItemThumb
import com.shelfsnap.app.ui.components.brandColor
import com.shelfsnap.app.ui.theme.ConditionExcellent
import com.shelfsnap.app.ui.theme.ConditionFair
import com.shelfsnap.app.ui.theme.ConditionGood
import com.shelfsnap.app.ui.theme.ConditionPoor
import com.shelfsnap.app.ui.theme.LocalEstimateLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onSummaryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

    if (showSortSheet) {
        InventorySortSheet(
            currentSort = uiState.sortOrder,
            onSortSelected = {
                viewModel.onSortChange(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    IconButton(onClick = onSummaryClick) {
                        Icon(Icons.Default.Summarize, contentDescription = stringResource(R.string.donation_summary))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.add_item))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.items.isNotEmpty() || uiState.searchQuery.isNotBlank()) {
                // Pill-shaped search field — flat, no outline border
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_items)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                )

                if (!uiState.isLoading) {
                    InventoryFilterRow(
                        selected = uiState.filter,
                        totalCount = uiState.totalCount,
                        listedCount = uiState.listedCount,
                        draftCount = uiState.draftCount,
                        soldCount = uiState.soldCount,
                        onFilterChange = viewModel::onFilterChange,
                    )
                }

                if (!uiState.isLoading && uiState.items.isNotEmpty()) {
                    SummaryBanner(
                        itemCount = uiState.items.size,
                        totalEstimate = uiState.items.sumOf { it.estimatedValue },
                        onSummaryClick = onSummaryClick,
                    )
                }
            }

            when {
                uiState.isLoading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }

                uiState.items.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        if (uiState.searchQuery.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.no_items_match_search),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp),
                            )
                        } else {
                            InventoryWalkthrough(onSettingsClick = onSettingsClick)
                        }
                    }

                else ->
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            InventoryItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun InventoryWalkthrough(onSettingsClick: () -> Unit) {
    val steps =
        listOf(
            Triple(Icons.Default.PhotoCamera, R.string.walkthrough_step1_title, R.string.walkthrough_step1_body),
            Triple(Icons.Default.AutoAwesome, R.string.walkthrough_step2_title, R.string.walkthrough_step2_body),
            Triple(Icons.Default.Sell, R.string.walkthrough_step3_title, R.string.walkthrough_step3_body),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.walkthrough_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        steps.forEach { (icon, titleRes, bodyRes) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        androidx.compose.material3.FilledTonalButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.go_to_settings))
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: Item,
    onClick: () -> Unit,
) {
    val estimateColor = LocalEstimateLabel.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ItemThumb(item = item, size = 64.dp, showCount = true)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Title row: brand+model (or category fallback) left, estimate right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.brand.isNotBlank()) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$" + "%.0f".format(item.estimatedValue),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color =
                                if (item.confidencePercent >= 90) {
                                    estimateColor
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        if (item.confidencePercent > 0) {
                            Text(
                                text = "${item.confidencePercent}% conf.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Badges row: condition | status pill | platform dots (pushed right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConditionBadge(condition = item.condition)
                    ListingStatusPill(item = item)
                    if (item.isDraft) {
                        StatusPill(
                            label = stringResource(R.string.draft_label),
                            icon = Icons.Default.Edit,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PlatformDots(item = item)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryFilterRow(
    selected: InventoryFilter,
    totalCount: Int,
    listedCount: Int,
    draftCount: Int,
    soldCount: Int,
    onFilterChange: (InventoryFilter) -> Unit,
) {
    val chips =
        listOf(
            InventoryFilter.ALL to stringResource(R.string.filter_all, totalCount),
            InventoryFilter.DRAFT to stringResource(R.string.filter_drafts, draftCount),
            InventoryFilter.LISTED to stringResource(R.string.filter_listed, listedCount),
            InventoryFilter.SOLD to stringResource(R.string.filter_sold, soldCount),
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onFilterChange(mode) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySortSheet(
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            SortOrder.entries.forEach { order ->
                ListItem(
                    headlineContent = { Text(order.label) },
                    trailingContent = {
                        RadioButton(
                            selected = currentSort == order,
                            onClick = null,
                        )
                    },
                    modifier = Modifier.clickable { onSortSelected(order) },
                )
            }
        }
    }
}

@Composable
private fun ListingStatusPill(item: Item) {
    when {
        item.hasSold ->
            StatusPill(
                label = stringResource(R.string.status_sold),
                icon = Icons.Default.CheckCircle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        item.hasActiveListing ->
            StatusPill(
                label = stringResource(R.string.status_listed),
                icon = Icons.Default.Sell,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
    }
}

@Composable
private fun StatusPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color,
) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}

@Composable
private fun PlatformDots(item: Item) {
    val platforms = item.listings.mapNotNull { Platform.fromKey(it.platformKey) }.distinct()
    if (platforms.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        platforms.forEach { platform ->
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(platform.brandColor()),
            )
        }
    }
}

@Composable
private fun SummaryBanner(
    itemCount: Int,
    totalEstimate: Double,
    onSummaryClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable(onClick = onSummaryClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text =
                    buildString {
                        append(itemCount)
                        append(" item")
                        if (itemCount != 1) append("s")
                        append("  ·  Est. total: \$")
                        append(totalEstimate.toInt())
                    },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun ConditionBadge(condition: Condition) {
    val color = conditionColor(condition)
    val label = condition.name.lowercase().replaceFirstChar { it.uppercase() }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

fun conditionColor(condition: Condition): Color =
    when (condition) {
        Condition.EXCELLENT -> ConditionExcellent
        Condition.GOOD -> ConditionGood
        Condition.FAIR -> ConditionFair
        Condition.POOR -> ConditionPoor
    }
