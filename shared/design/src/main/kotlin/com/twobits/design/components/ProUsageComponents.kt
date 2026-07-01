package com.twobits.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One row of managed-Pro usage or allowance.
 *  - [used] non-null + [limit] non-null → "X of N" meter (live usage against a cap).
 *  - [used] null + [limit] non-null → "Up to N" allowance row (no live count available yet).
 *  - [limit] null → a plain count with no cap (uncapped feature).
 */
data class ProUsageMetric(
    val label: String,
    val used: Int?,
    val limit: Int?,
    val unitLabel: String,
    val icon: ImageVector,
)

/**
 * Managed-Pro usage/allowance card. Pure UI: callers map their `shared/pro` policy (and any live
 * counters) into [metrics]. Carries the framing that Pro is metered, not unlimited.
 */
@Composable
fun ProUsageCard(
    metrics: List<ProUsageMetric>,
    modifier: Modifier = Modifier,
    title: String = "Included each month",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            metrics.forEachIndexed { index, metric ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProUsageRow(metric)
            }
        }
    }
}

@Composable
private fun ProUsageRow(metric: ProUsageMetric) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                metric.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(metric.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            val suffix = if (metric.unitLabel.isBlank()) "" else " ${metric.unitLabel}"
            val valueText =
                when {
                    metric.used != null && metric.limit != null -> "${metric.used} / ${metric.limit}$suffix"
                    metric.limit != null -> "Up to ${metric.limit}$suffix"
                    metric.used != null -> "${metric.used}$suffix"
                    else -> ""
                }
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AI_PRO_COLOR,
            )
        }
        // Live meter only when we have an actual count against a cap.
        if (metric.used != null && metric.limit != null && metric.limit > 0) {
            val fraction = (metric.used.toFloat() / metric.limit.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
                color = AI_PRO_COLOR,
                trackColor = AI_PRO_COLOR.copy(alpha = 0.15f),
            )
        }
    }
}

/**
 * Surfaces the managed monthly spend cap with the explicit "Pro is metered, not unlimited" framing.
 * [capLabel] is e.g. "$2.00 / month included"; [note] explains what happens at the cap.
 */
@Composable
fun ProSpendCapCard(
    capLabel: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AI_PRO_COLOR.copy(alpha = 0.12f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Speed,
            contentDescription = null,
            tint = AI_PRO_COLOR,
            modifier = Modifier.size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                capLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Verbatim BYOK note, identical across all three apps so the guarantee is stated consistently. */
const val BYOK_DIRECT_NOTE: String =
    "BYOK uses your provider key directly from this device. Requests do not route through " +
        "TwoBits managed infrastructure and do not use your Pro allowance."

/** Fixed-copy card rendering [BYOK_DIRECT_NOTE]. */
@Composable
fun ByokDirectNoteCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AI_BYOK_COLOR.copy(alpha = 0.12f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Key,
            contentDescription = null,
            tint = AI_BYOK_COLOR,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = BYOK_DIRECT_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
