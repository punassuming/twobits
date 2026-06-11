package com.shelfsnap.app.ui.itemdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shelfsnap.app.R
import com.shelfsnap.app.data.model.MarketComp
import com.shelfsnap.app.data.model.MarketResearch
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.shelfsnap.app.ui.components.PlatformBadge
import com.shelfsnap.app.ui.components.brandColor
import java.text.DateFormat
import java.util.Date

@Composable
fun MarketTab(
    uiState: ItemDetailUiState,
    viewModel: ItemDetailViewModel,
) {
    val research = uiState.item?.marketResearch ?: MarketResearch()
    val hasData = research.comps.isNotEmpty() || research.suggestedPrices.isNotEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Research action / refresh
        Button(
            onClick = viewModel::researchPrice,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isResearching,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (uiState.isResearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.researching_price))
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.research_price))
            }
        }

        if (hasData) {
            SearchStatusBanner(research)
        }

        if (!hasData) {
            Text(
                text = stringResource(R.string.no_market_research),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val platformCount =
            research.comps
                .map { it.platformKey }
                .distinct()
                .size

        // Price summary
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.price_analysis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        stringResource(
                            R.string.price_analysis_subtitle,
                            research.comps.size,
                            platformCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        value = "$" + "%.0f".format(research.averageSoldPrice),
                        label = stringResource(R.string.avg_sold_price),
                        emphasize = true,
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        value = "$" + "%.0f".format(research.lowPrice) + "–$" + "%.0f".format(research.highPrice),
                        label = stringResource(R.string.price_range),
                    )
                }
                if (research.highPrice > research.lowPrice) {
                    PriceRangeBar(
                        low = research.lowPrice,
                        high = research.highPrice,
                        marker = research.averageSoldPrice,
                    )
                }
                if (research.confidencePercent > 0) {
                    Text(
                        text = stringResource(R.string.confidence_short, research.confidencePercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        // Per-platform suggested prices
        if (research.suggestedPrices.isNotEmpty()) {
            SectionTitle(
                title = stringResource(R.string.suggested_price_by_platform),
                subtitle = stringResource(R.string.suggested_price_subtitle),
            )
            research.suggestedPrices.entries
                .mapNotNull { (key, price) -> Platform.fromKey(key)?.let { it to price } }
                .forEach { (platform, price) ->
                    PlatformPriceRow(
                        platform = platform,
                        price = price,
                        onApply = { viewModel.applySuggestedPrice(price) },
                    )
                }
        }

        // Comparable listings, filterable by platform
        if (research.comps.isNotEmpty()) {
            SectionTitle(title = stringResource(R.string.comparable_listings))
            var compFilter by remember(research) { mutableStateOf<String?>(null) }
            val platformKeys = research.comps.map { it.platformKey }.distinct()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = compFilter == null,
                    onClick = { compFilter = null },
                    label = { Text(stringResource(R.string.filter_all, research.comps.size)) },
                )
                platformKeys.forEach { key ->
                    val platform = Platform.fromKey(key)
                    val count = research.comps.count { it.platformKey == key }
                    FilterChip(
                        selected = compFilter == key,
                        onClick = { compFilter = key },
                        label = { Text("${platform?.displayName ?: key} ($count)") },
                    )
                }
            }
            research.comps
                .filter { compFilter == null || it.platformKey == compFilter }
                .forEach { comp -> CompListingRow(comp = comp) }
        }

        // Tappable citations
        val linkCitations = research.citations.filter { it.url.isNotBlank() }
        if (linkCitations.isNotEmpty()) {
            val uriHandler = LocalUriHandler.current
            SectionTitle(title = stringResource(R.string.sources_section_title))
            linkCitations.forEach { citation ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { uriHandler.openUri(citation.url) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = citation.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            val sourceNames =
                research.comps
                    .mapNotNull { Platform.fromKey(it.platformKey)?.displayName }
                    .distinct()
                    .ifEmpty { research.citations.map { it.label } }
                    .joinToString(", ")
            if (sourceNames.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.sources_label, sourceNames),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val date =
                            if (research.retrievedAt > 0) {
                                DateFormat.getDateInstance().format(Date(research.retrievedAt))
                            } else {
                                "—"
                            }
                        Text(
                            text = stringResource(R.string.sources_retrieved, date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One-line status above the price card answering "where did this estimate come from?":
 * live web results, a failed search, an empty search, or AI knowledge only.
 */
@Composable
private fun SearchStatusBanner(research: MarketResearch) {
    val provider = SearchProvider.fromKey(research.searchProviderKey)
    val (icon, text, container, content) =
        when {
            research.searchResultCount > 0 ->
                SearchStatusStyle(
                    icon = Icons.Default.Search,
                    text = "Based on ${research.searchResultCount} web results via ${provider.displayName}",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            research.searchError != null ->
                SearchStatusStyle(
                    icon = Icons.Default.Info,
                    text = "Web search failed — AI estimate only. ${research.searchError}",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            provider == SearchProvider.NONE ->
                SearchStatusStyle(
                    icon = Icons.Default.Info,
                    text =
                        "Prices estimated from AI training data. Enable Brave Search or Jina AI " +
                            "in Settings → AI for live market prices.",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            else ->
                SearchStatusStyle(
                    icon = Icons.Default.Info,
                    text = "${provider.displayName} returned no results — AI estimate only",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
        }
    Surface(shape = RoundedCornerShape(12.dp), color = container) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

private data class SearchStatusStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String,
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
)

/**
 * Position of the [value] marker on a low→high range, clamped to a visible margin so the
 * dot never sits flush against either end. Pure function, unit-tested.
 */
fun priceMarkerFraction(
    low: Double,
    high: Double,
    value: Double,
): Float {
    if (high <= low) return 0.5f
    val raw = ((value - low) / (high - low)).toFloat()
    return raw.coerceIn(0.03f, 0.97f)
}

/** Gradient price range with a marker at the average/suggested price. */
@Composable
private fun PriceRangeBar(
    low: Double,
    high: Double,
    marker: Double,
) {
    val fraction = priceMarkerFraction(low, high, marker)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                ),
                            ),
                        ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(fraction))
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                )
                Spacer(Modifier.weight(1f - fraction))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$" + "%.0f".format(low),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    stringResource(R.string.suggested).replaceFirstChar { it.uppercase() } +
                        ": $" + "%.0f".format(marker),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "$" + "%.0f".format(high),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    emphasize: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlatformPriceRow(
    platform: Platform,
    price: Double,
    onApply: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlatformBadge(platform = platform)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$" + "%.2f".format(price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = platform.brandColor(),
                )
                Text(
                    text = stringResource(R.string.suggested),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onApply) { Text(stringResource(R.string.apply_suggested_price)) }
        }
    }
}

@Composable
private fun CompListingRow(comp: MarketComp) {
    val platform = Platform.fromKey(comp.platformKey)
    val uriHandler = LocalUriHandler.current
    val hasLink = comp.sourceUrl.isNotBlank()
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasLink) {
                            Modifier.clickable { uriHandler.openUri(comp.sourceUrl) }
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = comp.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                Text(
                    text = "$" + "%.2f".format(comp.price),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (comp.sold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (hasLink) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (platform != null) PlatformBadge(platform = platform)
                val statusColor = if (comp.sold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.14f)) {
                    Text(
                        text = if (comp.sold) stringResource(R.string.status_sold) else stringResource(R.string.filter_unlisted),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (comp.date.isNotBlank()) {
                    Text(
                        text = comp.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
