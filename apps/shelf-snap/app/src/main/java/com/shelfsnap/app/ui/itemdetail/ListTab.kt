package com.shelfsnap.app.ui.itemdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.ListingStatus
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.PlatformListing
import com.shelfsnap.app.ui.components.PlatformBadge
import com.shelfsnap.app.ui.components.brandColor
import com.shelfsnap.app.ui.components.icon

@Composable
fun ListTab(uiState: ItemDetailUiState, viewModel: ItemDetailViewModel) {
    val item = uiState.item ?: return
    val existing = item.listings
    val existingKeys = existing.map { it.platformKey }.toSet()
    val available = Platform.entries.filter { it.key !in existingKeys }
    var selected by remember(item.id, existingKeys) { mutableStateOf(emptySet<Platform>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active listings
        if (existing.isNotEmpty()) {
            Text(
                text = stringResource(R.string.active_listings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            existing.forEach { listing -> ActiveListingRow(listing = listing) }
        }

        // List on platforms
        Text(
            text = stringResource(R.string.list_on_platforms),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.list_on_platforms_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        available.forEach { platform ->
            val price = item.marketResearch.suggestedPrices[platform.key]
            PlatformToggleRow(
                platform = platform,
                suggestedPrice = price,
                selected = platform in selected,
                onToggle = {
                    selected = if (platform in selected) selected - platform else selected + platform
                }
            )
        }

        // AI-generated listing preview (shown once at least one platform is selected)
        if (selected.isNotEmpty()) {
            ListingPreviewCard(item = item, selected = selected)
        }

        // Cross-list action. `selected` auto-resets once item.listings updates (its
        // remember key includes existingKeys), so we don't clear it manually here.
        if (selected.isNotEmpty()) {
            Button(
                onClick = { viewModel.crossList(selected) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isCrossListing,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState.isCrossListing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.creating_listings))
                } else {
                    Icon(Icons.Default.Sell, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.list_on_count, selected.size))
                }
            }
        }

        // Tip
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.TipsAndUpdates,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.cross_list_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveListingRow(listing: PlatformListing) {
    val platform = Platform.fromKey(listing.platformKey) ?: return
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(platform.icon(), contentDescription = null, tint = platform.brandColor())
            Column(Modifier.weight(1f)) {
                Text(platform.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = listing.status.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$" + "%.2f".format(listing.price),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlatformToggleRow(
    platform: Platform,
    suggestedPrice: Double?,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val color = platform.brandColor()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                platform.icon(),
                contentDescription = null,
                tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f)) {
                Text(
                    platform.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.suggested
                    ).replaceFirstChar { it.uppercase() } + ": " +
                        (suggestedPrice?.let { "$" + "%.2f".format(it) } ?: "—"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingPreviewCard(item: Item, selected: Set<Platform>) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.listing_preview),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.listing_preview_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val condLabel = item.condition.name.lowercase().replaceFirstChar { it.uppercase() }
            val title = listOf(item.brand, item.model)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { item.category.ifBlank { "—" } }
            Text(
                text = "$title — $condLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            if (item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selected.forEach { platform ->
                    val price = item.marketResearch.suggestedPrices[platform.key] ?: item.estimatedValue
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlatformBadge(platform = platform)
                        Text(
                            text = "$" + "%.2f".format(price),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun ListingStatus.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
