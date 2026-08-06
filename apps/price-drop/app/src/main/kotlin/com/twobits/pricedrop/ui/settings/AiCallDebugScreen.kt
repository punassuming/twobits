package com.twobits.pricedrop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.pricedrop.data.local.AiCallDebugEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global, chronological log of every AI inference call — local and cloud, across Ask and product
 * search. A "-start" entry with no matching completed entry right after it means the app crashed
 * mid-call — see [com.twobits.pricedrop.data.local.AiCallDebugEntry]'s doc. Never shows full
 * prompt/response text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCallDebugScreen(
    onBack: () -> Unit,
    viewModel: AiCallDebugViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI call log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear log")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.entries.isEmpty() -> {
                    Text(
                        "No AI calls recorded yet. Ask a question or search for a product, then " +
                            "come back here.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.entries) { entry ->
                            AiCallDebugEntryCard(entry)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear AI call log?") },
            text = { Text("This deletes all recorded entries. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AiCallDebugEntryCard(entry: AiCallDebugEntry) {
    val isStartMarker = entry.op.endsWith("-start")
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                when {
                    isStartMarker -> "${entry.op.removeSuffix("-start")} — STARTED"
                    entry.success -> "${entry.op} — OK"
                    else -> "${entry.op} — FAILED"
                } + (entry.httpStatus?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color =
                    when {
                        isStartMarker -> MaterialTheme.colorScheme.tertiary
                        entry.success -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.error
                    },
            )
            Text(
                "${TIME_FORMAT.format(Date(entry.timestampMs))} · ${entry.endpoint}${entry.model?.let { " · $it" } ?: ""}" +
                    (entry.durationMs?.let { " · ${formatDuration(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                entry.requestSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.responseSnippet?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

/** e.g. "340ms" or "12.4s" — short enough for the entry card's one-line detail row. */
private fun formatDuration(durationMs: Long): String = if (durationMs < 1000) "${durationMs}ms" else "%.1fs".format(durationMs / 1000.0)
