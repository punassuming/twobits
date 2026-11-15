package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twobits.design.TwoBitsSpacing

/**
 * The standard "working on it" state: a centered spinner with an optional [label] and an optional
 * Cancel action. Pass [onCancel] for anything that can take more than a few seconds (network
 * lookups, model loads) so the user is never stuck behind a spinner with no way out.
 */
@Composable
fun AppLoadingState(
    modifier: Modifier = Modifier.fillMaxSize(),
    label: String? = null,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = "Cancel",
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TwoBitsSpacing.row),
            modifier =
                Modifier
                    .padding(horizontal = 32.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            CircularProgressIndicator()
            label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            onCancel?.let {
                TextButton(onClick = it) { Text(cancelLabel) }
            }
        }
    }
}
