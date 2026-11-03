package com.twobits.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.twobits.design.TwoBitsSpacing

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    // Optional recovery action (e.g. "Retry"), rendered as a text button after the message.
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = TwoBitsSpacing.card,
                    end = if (actionLabel != null) TwoBitsSpacing.half else TwoBitsSpacing.card,
                    top = if (actionLabel != null) TwoBitsSpacing.half else TwoBitsSpacing.card,
                    bottom = if (actionLabel != null) TwoBitsSpacing.half else TwoBitsSpacing.card,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(TwoBitsSpacing.screenH))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            actionLabel?.let {
                TextButton(onClick = onAction) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
