package com.shelfsnap.app.ui.itemdetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.ListingStatus
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.PlatformListing
import com.shelfsnap.app.data.model.formatListingText
import com.shelfsnap.app.ui.components.PlatformBadge
import com.shelfsnap.app.ui.components.brandColor
import com.shelfsnap.app.ui.components.icon
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ListTab(
    uiState: ItemDetailUiState,
    viewModel: ItemDetailViewModel,
) {
    val item = uiState.item ?: return
    val existing = item.listings
    val existingKeys = existing.map { it.platformKey }.toSet()
    val available = Platform.entries.filter { it.key !in existingKeys }
    var selected by remember(item.id, existingKeys) { mutableStateOf(emptySet<Platform>()) }

    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun launchPlatform(
        platform: Platform,
        listingText: String,
    ) {
        clipboardManager.setText(AnnotatedString(listingText))
        runCatching { uriHandler.openUri(platform.sellUrl) }
        scope.launch {
            snackbarHostState.showSnackbar("Copied — paste into ${platform.displayName}")
        }
    }

    fun shareListingText(
        platform: Platform,
        listingText: String,
    ) {
        val photoUris =
            item.photoPaths
                .take(3)
                .mapNotNull { path ->
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(path),
                        )
                    }.getOrNull()
                }
        val intent =
            if (photoUris.isNotEmpty()) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(photoUris))
                    putExtra(Intent.EXTRA_TEXT, listingText)
                    putExtra(Intent.EXTRA_SUBJECT, platform.displayName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, listingText)
                    putExtra(Intent.EXTRA_SUBJECT, platform.displayName)
                }
            }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share listing")) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Active listings
            if (existing.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.active_listings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                existing.forEach { listing ->
                    val platform = Platform.fromKey(listing.platformKey) ?: return@forEach
                    ActiveListingRow(
                        listing = listing,
                        onLaunch = { launchPlatform(platform, platform.formatListingText(item)) },
                        onMarkSold = { viewModel.markSold(listing.platformKey) },
                    )
                }
            }

            // List on platforms
            Text(
                text = stringResource(R.string.list_on_platforms),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.list_on_platforms_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            available.forEach { platform ->
                val price = item.marketResearch.suggestedPrices[platform.key]
                var tipsExpanded by remember(platform.key) { mutableStateOf(false) }
                PlatformToggleRow(
                    platform = platform,
                    suggestedPrice = price,
                    selected = platform in selected,
                    onToggle = {
                        selected = if (platform in selected) selected - platform else selected + platform
                    },
                )
                if (platform.listingTips.isNotBlank()) {
                    PlatformTipsRow(
                        tips = platform.listingTips,
                        expanded = tipsExpanded,
                        onToggle = { tipsExpanded = !tipsExpanded },
                    )
                }
            }

            // AI-generated listing preview (shown once at least one platform is selected)
            if (selected.isNotEmpty()) {
                ListingPreviewCard(
                    item = item,
                    selected = selected,
                    onCopyText = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar("Listing text copied") }
                    },
                    onShareText = { platform, text -> shareListingText(platform, text) },
                )
            }

            // Cross-list action. `selected` auto-resets once item.listings updates (its
            // remember key includes existingKeys), so we don't clear it manually here.
            if (selected.isNotEmpty()) {
                Button(
                    onClick = { viewModel.crossList(selected) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isCrossListing,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (uiState.isCrossListing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.cross_list_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ActiveListingRow(
    listing: PlatformListing,
    onLaunch: () -> Unit,
    onMarkSold: () -> Unit,
) {
    val platform = Platform.fromKey(listing.platformKey) ?: return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(platform.icon(), contentDescription = null, tint = platform.brandColor())
                Column(Modifier.weight(1f)) {
                    Text(platform.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = listing.status.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "$" + "%.2f".format(listing.price),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onLaunch, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "Open ${platform.displayName}",
                        tint = platform.brandColor(),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (listing.status == ListingStatus.ACTIVE) {
                TextButton(
                    onClick = onMarkSold,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Mark sold", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PlatformToggleRow(
    platform: Platform,
    suggestedPrice: Double?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val color = platform.brandColor()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = onToggle,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                platform.icon(),
                contentDescription = null,
                tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    platform.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        stringResource(
                            R.string.suggested,
                        ).replaceFirstChar { it.uppercase() } + ": " +
                            (suggestedPrice?.let { "$" + "%.2f".format(it) } ?: "—"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun PlatformTipsRow(
    tips: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
    ) {
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Tips",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Text(
                    text = tips.split(" · ").joinToString("\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingPreviewCard(
    item: Item,
    selected: Set<Platform>,
    onCopyText: (String) -> Unit,
    onShareText: (Platform, String) -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.listing_preview),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.listing_preview_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val condLabel =
                item.condition.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            val title =
                listOf(item.brand, item.model)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { item.category.ifBlank { "—" } }
            Text(
                text = "$title — $condLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            if (item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selected.forEach { platform ->
                    val price = item.marketResearch.suggestedPrices[platform.key] ?: item.estimatedValue
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PlatformBadge(platform = platform)
                        Text(
                            text = "$" + "%.2f".format(price),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Copy and Share buttons for the first selected platform
            val firstPlatform = selected.first()
            val listingText = firstPlatform.formatListingText(item)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onCopyText(listingText) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy listing text", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = { onShareText(firstPlatform, listingText) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Share", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun ListingStatus.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
