package com.twobits.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The single tier card for Free / Pro / BYOK plan screens across all three apps.
 *
 * Two layouts, selected by [compact]:
 *  - `compact = false` (default): a full-width card with optional CTA button — use for a primary
 *    upgrade card.
 *  - `compact = true`: a tight column meant to sit in a 3-across comparison `Row` with
 *    `Modifier.weight(1f)` — no CTA button; shows [badge]/[priceNote] and an accent border when
 *    [isHighlighted].
 *
 * [accentColor] tints the badge/highlight (defaults to the theme primary). The CTA renders only
 * when [ctaLabel] and [onCta] are both provided and [compact] is false.
 */
@Composable
fun ProTierCard(
    title: String,
    price: String,
    features: List<String>,
    modifier: Modifier = Modifier,
    priceNote: String? = null,
    badge: String? = null,
    accentColor: Color? = null,
    isHighlighted: Boolean = false,
    compact: Boolean = false,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
    ctaEnabled: Boolean = true,
    isLoading: Boolean = false,
) {
    if (compact) {
        CompactTierColumn(
            title = title,
            price = price,
            features = features,
            modifier = modifier,
            priceNote = priceNote,
            badge = badge,
            accentColor = accentColor ?: MaterialTheme.colorScheme.primary,
            isHighlighted = isHighlighted,
        )
        return
    }

    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isHighlighted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (badge != null) {
                Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.15f)) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isHighlighted) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    price,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                if (priceNote != null) {
                    Text(
                        priceNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                features.forEach { feature ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (ctaLabel != null && onCta != null) {
                Button(
                    onClick = onCta,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ctaEnabled,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "Processing…" else ctaLabel)
                }
            }
        }
    }
}

@Composable
private fun CompactTierColumn(
    title: String,
    price: String,
    features: List<String>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    priceNote: String? = null,
    badge: String? = null,
    isHighlighted: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color =
            if (isHighlighted) {
                accentColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border = if (isHighlighted) BorderStroke(1.5.dp, accentColor.copy(alpha = 0.5f)) else null,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (badge != null) {
                Surface(shape = RoundedCornerShape(4.dp), color = accentColor.copy(alpha = 0.15f)) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (priceNote != null) {
                Text(
                    priceNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            features.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = accentColor,
                    )
                    Text(item, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
