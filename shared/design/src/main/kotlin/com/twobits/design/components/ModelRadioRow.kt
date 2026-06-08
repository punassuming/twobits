package com.twobits.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * A single radio-button row for model selection — name, subtitle, optional cost label.
 * Wrap multiple [ModelRadioRow]s in a [ModelRadioList] to get the unified card treatment.
 */
@Composable
fun ModelRadioRow(
    name: String,
    subtitle: String,
    costLabel: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (costLabel != null) {
            Text(
                text = costLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/**
 * Wraps a list of [ModelRadioRow]s in a rounded card with dividers between rows.
 */
@Composable
fun <T> ModelRadioList(
    models: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    name: (T) -> String,
    subtitle: (T) -> String,
    costLabel: ((T) -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            models.forEachIndexed { index, model ->
                if (index > 0) HorizontalDivider(thickness = 0.5.dp)
                val isFirst = index == 0
                val isLast = index == models.lastIndex
                val shape = when {
                    isFirst && isLast -> RoundedCornerShape(14.dp)
                    isFirst -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                    isLast -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                ModelRadioRow(
                    name = name(model),
                    subtitle = subtitle(model),
                    costLabel = costLabel?.invoke(model),
                    selected = model == selected,
                    onClick = { onSelect(model) },
                    modifier = Modifier.background(
                        if (model == selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = shape,
                    ),
                )
            }
        }
    }
}
