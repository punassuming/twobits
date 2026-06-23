package com.shelfsnap.app.ui.itemdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shelfsnap.app.data.model.ListingStatus
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.PlatformListing
import com.twobits.design.components.AppSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingSummaryScreen(
    itemId: Long,
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item
    val listings =
        item?.listings?.filter {
            it.status == ListingStatus.DRAFT || it.status == ListingStatus.ACTIVE
        } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = item?.brand?.ifBlank { item.category } ?: "Listing copy",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (listings.isNotEmpty()) {
                            Text(
                                text = "${listings.size} platform${if (listings.size != 1) "s" else ""} · ready to list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.refineAllListings() },
                        enabled = !uiState.isRefiningAll && listings.any { it.status == ListingStatus.DRAFT },
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refine all")
                    }
                },
            )
        },
    ) { padding ->
        if (listings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = "No listings yet. Go to the List tab to cross-list this item.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val expandedState = remember { mutableStateMapOf<String, Boolean>() }
        listings.forEach { listing ->
            if (listing.platformKey !in expandedState) {
                expandedState[listing.platformKey] = listing.status == ListingStatus.DRAFT
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(listings, key = { it.platformKey }) { listing ->
                val platform = Platform.fromKey(listing.platformKey) ?: return@items
                val expanded = expandedState[listing.platformKey] ?: true
                PlatformListingCard(
                    listing = listing,
                    platform = platform,
                    expanded = expanded,
                    isRefining = listing.platformKey in uiState.refiningPlatforms,
                    onToggle = { expandedState[listing.platformKey] = !expanded },
                    onRefine = { viewModel.refineListing(listing.platformKey) },
                    onMarkActive = { viewModel.markListingActive(listing.platformKey) },
                    onUnlist = { viewModel.unlistPlatform(listing.platformKey) },
                    onAddUrl = { viewModel.setListingUrl(listing.platformKey, it) },
                )
            }
        }
    }
}

@Composable
private fun PlatformListingCard(
    listing: PlatformListing,
    platform: Platform,
    expanded: Boolean,
    isRefining: Boolean,
    onToggle: () -> Unit,
    onRefine: () -> Unit,
    onMarkActive: () -> Unit,
    onUnlist: () -> Unit,
    onAddUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header row — always visible
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status = listing.status)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                ListingStatusChip(status = listing.status)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    // Tips + Refine row
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "• " + platform.listingTips.replace(" · ", "\n• "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onRefine,
                            enabled = !isRefining,
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refine", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (isRefining) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    // TITLE field
                    AppSectionLabel(text = "Title")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = listing.title ?: "–",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val titleLen = listing.title?.length ?: 0
                        Text(
                            text = "$titleLen/${platform.titleCharLimit}",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (titleLen > platform.titleCharLimit) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(listing.title ?: "")) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy title", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // DESCRIPTION field
                    AppSectionLabel(text = "Description")
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = listing.description ?: "–",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(listing.description ?: "")) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy description", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // CONDITION field
                    AppSectionLabel(text = "Condition")
                    Text(
                        text = listing.condition ?: "–",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.height(8.dp))

                    // PRICE field
                    AppSectionLabel(text = "Price")
                    Text(
                        text = "$${listing.price.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(Modifier.height(8.dp))

                    // SHIPPING field
                    AppSectionLabel(text = "Shipping")
                    Text(
                        text = listing.shipping ?: "–",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Copy all button
                    OutlinedButton(
                        onClick = {
                            val allFields =
                                buildString {
                                    appendLine("TITLE: ${listing.title ?: ""}")
                                    appendLine("DESCRIPTION: ${listing.description ?: ""}")
                                    appendLine("CONDITION: ${listing.condition ?: ""}")
                                    appendLine("PRICE: $${listing.price.toInt()}")
                                    append("SHIPPING: ${listing.shipping ?: ""}")
                                }
                            clipboardManager.setText(AnnotatedString(allFields))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy all fields")
                    }

                    Spacer(Modifier.height(4.dp))

                    // Footer row — URL + Mark listed / Unlist
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!listing.listingUrl.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = listing.listingUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { uriHandler.openUri(listing.listingUrl) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open listing", modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text(
                                text = "Track listing URL",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onAddUrl(platform.sellUrl) }) {
                                Text("+ Add URL", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (listing.status == ListingStatus.DRAFT) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onMarkActive) {
                                Text("Mark listed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (listing.status == ListingStatus.ACTIVE) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onUnlist) {
                                Text("Unlist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(
    status: ListingStatus,
    modifier: Modifier = Modifier,
) {
    val color =
        when (status) {
            ListingStatus.DRAFT -> MaterialTheme.colorScheme.tertiary
            ListingStatus.ACTIVE -> MaterialTheme.colorScheme.primary
            ListingStatus.SOLD -> MaterialTheme.colorScheme.outline
            ListingStatus.EXPIRED -> MaterialTheme.colorScheme.error
            ListingStatus.UNLISTED -> MaterialTheme.colorScheme.outlineVariant
        }
    Box(
        modifier =
            modifier
                .size(8.dp)
                .background(color = color, shape = RoundedCornerShape(50)),
    )
}

@Composable
private fun ListingStatusChip(
    status: ListingStatus,
    modifier: Modifier = Modifier,
) {
    val label =
        when (status) {
            ListingStatus.DRAFT -> "Draft"
            ListingStatus.ACTIVE -> "Active"
            ListingStatus.SOLD -> "Sold"
            ListingStatus.EXPIRED -> "Expired"
            ListingStatus.UNLISTED -> "Unlisted"
        }
    val containerColor =
        when (status) {
            ListingStatus.DRAFT -> MaterialTheme.colorScheme.tertiaryContainer
            ListingStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
            ListingStatus.SOLD -> MaterialTheme.colorScheme.surfaceVariant
            ListingStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
            ListingStatus.UNLISTED -> MaterialTheme.colorScheme.surfaceVariant
        }
    val labelColor =
        when (status) {
            ListingStatus.DRAFT -> MaterialTheme.colorScheme.onTertiaryContainer
            ListingStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
            ListingStatus.SOLD -> MaterialTheme.colorScheme.onSurfaceVariant
            ListingStatus.EXPIRED -> MaterialTheme.colorScheme.onErrorContainer
            ListingStatus.UNLISTED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = containerColor,
                labelColor = labelColor,
            ),
    )
}
