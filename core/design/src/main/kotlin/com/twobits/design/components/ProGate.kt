package com.twobits.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProGate(
    onUpgrade: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    monthlyPrice: String = "$1.99/month",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Unlock AI features",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Add your own OpenAI key for free, or go Pro for managed access — no key required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Go Pro — $monthlyPrice")
            }
            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use your own API key")
            }
        }
    }
}
