package dev.scrybe.feature.filemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(onNavigateBack: () -> Unit) {
    val viewModel: FileManagerViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState) {
            FileManagerUiState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            is FileManagerUiState.Error ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text(state.message, color = MaterialTheme.colorScheme.error) }
            is FileManagerUiState.Success ->
                FileManagerContent(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onImportOrphan = viewModel::importOrphan,
                    onDeleteFile = viewModel::deleteFile,
                    onExportBundle = viewModel::exportBundle,
                )
        }
    }
}

@Composable
private fun FileManagerContent(
    state: FileManagerUiState.Success,
    modifier: Modifier = Modifier,
    onImportOrphan: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onExportBundle: (String) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SectionHeader(
                title = "Recordings",
                subtitle =
                    "${state.recordings.size} total" +
                        state.recordings.count { it.sessionId == null }.let { n ->
                            if (n > 0) ", $n orphaned" else ""
                        },
            )
        }
        if (state.recordings.isEmpty()) {
            item { EmptyState("No recording files found") }
        } else {
            items(state.recordings, key = { it.absolutePath }) { entry ->
                RecordingFileRow(
                    entry = entry,
                    onImport = { onImportOrphan(entry.absolutePath) },
                    onDelete = { onDeleteFile(entry.absolutePath) },
                    onExportBundle = { entry.sessionId?.let(onExportBundle) },
                )
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(
                title = "Saved Copies & Exports",
                subtitle = "${state.outputs.size} files",
            )
        }
        if (state.outputs.isEmpty()) {
            item { EmptyState("No saved copies or exports found") }
        } else {
            items(state.outputs, key = { it.absolutePath }) { entry ->
                OutputFileRow(
                    entry = entry,
                    onDelete = { onDeleteFile(entry.absolutePath) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RecordingFileRow(
    entry: RecordingFileEntry,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onExportBundle: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${formatSize(entry.sizeBytes)} · ${formatDate(entry.lastModifiedMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            if (entry.sessionId == null) {
                AssistChip(onClick = {}, label = { Text("Orphaned") })
            }
        },
        trailingContent = {
            Row {
                if (entry.sessionId == null) {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Default.Upload, contentDescription = "Import")
                    }
                } else if (entry.hasTranscript) {
                    IconButton(onClick = onExportBundle) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export bundle")
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        },
    )

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            name = entry.displayName,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun OutputFileRow(
    entry: OutputFileEntry,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${entry.category} · ${formatSize(entry.sizeBytes)} · ${formatDate(entry.lastModifiedMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            name = entry.displayName,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete file?") },
        text = { Text("\"$name\" will be permanently deleted.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1_024 -> "${bytes / 1_024} KB"
        else -> "$bytes B"
    }

private fun formatDate(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
