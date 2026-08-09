package dev.scrybe.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified, chronological timeline merging every on-device diagnostic signal — crashes, AI calls
 * (local and cloud), and supporting service calls — that used to live on two separate screens
 * (Crash log, AI call log). Seeing them together, in order, is the point: a service call that
 * timed out right before a crash is otherwise invisible unless the two screens are
 * cross-referenced by eye. An "-start" AI-call entry with no matching completed entry right
 * after it means the app crashed mid-call — see [DebugLogEntry]'s doc. Never shows raw
 * audio/photo bytes or full prompt/response/page text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    onBack: () -> Unit,
    viewModel: DebugLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.visibleEntries.isNotEmpty()) {
                        IconButton(onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        uiState.visibleEntries.joinToString("\n\n---\n\n") { it.asShareText() },
                                    )
                                }
                            context.startActivity(Intent.createChooser(intent, "Share debug log"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear log")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DebugLogFilterRow(selected = uiState.filter, onSelect = viewModel::setFilter)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.visibleEntries.isEmpty() -> {
                        Text(
                            "Nothing recorded yet. Use the app, and crashes, AI calls, and " +
                                "service calls will show up here as they happen.",
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
                            items(uiState.visibleEntries) { entry ->
                                DebugLogEntryCard(entry)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear debug log?") },
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
private fun DebugLogFilterRow(
    selected: DebugLogEntryType?,
    onSelect: (DebugLogEntryType?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        item {
            FilterChip(
                selected = selected == DebugLogEntryType.CRASH,
                onClick = { onSelect(DebugLogEntryType.CRASH) },
                label = { Text("Crashes") },
            )
        }
        item {
            FilterChip(
                selected = selected == DebugLogEntryType.AI_CALL,
                onClick = { onSelect(DebugLogEntryType.AI_CALL) },
                label = { Text("AI calls") },
            )
        }
        item {
            FilterChip(
                selected = selected == DebugLogEntryType.SERVICE_CALL,
                onClick = { onSelect(DebugLogEntryType.SERVICE_CALL) },
                label = { Text("Services") },
            )
        }
    }
}

@Composable
private fun DebugLogEntryCard(entry: DebugLogEntry) {
    when (entry.type) {
        DebugLogEntryType.CRASH -> CrashEntryCard(entry)
        DebugLogEntryType.AI_CALL, DebugLogEntryType.SERVICE_CALL -> CallEntryCard(entry)
    }
}

@Composable
private fun CrashEntryCard(entry: DebugLogEntry) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                entry.exceptionType?.substringAfterLast('.') ?: "Crash",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "${TIME_FORMAT.format(Date(entry.timestampMs))} · thread ${entry.threadName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.stackTrace?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CallEntryCard(entry: DebugLogEntry) {
    val isStartMarker = entry.op?.endsWith("-start") == true
    val isService = entry.type == DebugLogEntryType.SERVICE_CALL
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                when {
                    isStartMarker -> "${entry.op?.removeSuffix("-start")} — STARTED"
                    entry.success == true -> "${entry.op} — OK"
                    else -> "${entry.op} — FAILED"
                } + (entry.httpStatus?.let { " ($it)" } ?: "") + (if (isService) " · service" else ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color =
                    when {
                        isStartMarker -> MaterialTheme.colorScheme.tertiary
                        entry.success == true -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.error
                    },
            )
            Text(
                "${TIME_FORMAT.format(Date(entry.timestampMs))} · ${entry.endpoint}${entry.model?.let { " · $it" } ?: ""}" +
                    (entry.durationMs?.let { " · ${formatDuration(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.requestSummary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.responseSnippet?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.stackTrace?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DebugLogEntry.asShareText(): String =
    when (type) {
        DebugLogEntryType.CRASH ->
            "${TIME_FORMAT.format(Date(timestampMs))} · thread $threadName\n$exceptionType: $message\n$stackTrace"
        DebugLogEntryType.AI_CALL, DebugLogEntryType.SERVICE_CALL ->
            "${TIME_FORMAT.format(Date(timestampMs))} · $op · $endpoint" +
                (model?.let { " · $it" } ?: "") +
                (durationMs?.let { " · ${formatDuration(it)}" } ?: "") +
                "\n$requestSummary" +
                (responseSnippet?.let { "\n$it" } ?: "")
    }

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

/** e.g. "340ms" or "12.4s" — short enough for the entry card's one-line detail row. */
private fun formatDuration(durationMs: Long): String = if (durationMs < 1000) "${durationMs}ms" else "%.1fs".format(durationMs / 1000.0)
