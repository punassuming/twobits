package dev.scrybe.feature.history

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.service.recording.RecordingForegroundService
import dev.scrybe.service.recording.RecordingServiceActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState(initial = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<HistorySessionItem?>(null) }
    var infoTarget by remember { mutableStateOf<RecordInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<HistorySessionItem?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = requiredPermissions.all {
            results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            context.startForegroundService(
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = RecordingServiceActions.ACTION_START
                }
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val successState = uiState as? HistoryUiState.Success
    val isSelecting = successState?.selection?.isSelecting == true

    BackHandler(enabled = isSelecting) {
        viewModel.clearSelection()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (!isSelecting) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            deleteTarget = null
                        } else {
                            val granted = requiredPermissions.all {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            if (granted) {
                                context.startForegroundService(
                                    Intent(context, RecordingForegroundService::class.java).apply {
                                        action = RecordingServiceActions.ACTION_START
                                    }
                                )
                            } else {
                                permissionLauncher.launch(requiredPermissions.toTypedArray())
                            }
                        }
                    },
                    text = {
                        Text(if (isRecording) "Recording…" else "Record Again")
                    },
                    icon = {
                        Icon(Icons.Filled.Mic, contentDescription = "Record again")
                    },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelecting) {
                            "${successState?.selection?.selectedSessionIds?.size ?: 0} selected"
                        } else {
                            "Records"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelecting) viewModel.clearSelection() else onNavigateBack()
                    }) {
                        Icon(
                            if (isSelecting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelecting) "Clear selection" else "Back",
                        )
                    }
                },
                actions = {
                    if (isSelecting && successState != null) {
                        IconButton(onClick = { viewModel.selectAllVisible() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all visible records")
                        }
                        IconButton(onClick = { viewModel.transformSelectedSessions() }) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = "Run default transform")
                        }
                        IconButton(
                            onClick = {
                                viewModel.setArchivedForSelected(!successState.filters.showArchived)
                            },
                        ) {
                            Icon(
                                Icons.Filled.Archive,
                                contentDescription = if (successState.filters.showArchived) "Restore selected records" else "Archive selected records",
                            )
                        }
                        IconButton(onClick = { confirmBulkDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected records")
                        }
                    } else {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filter records")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is HistoryUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is HistoryUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }

            is HistoryUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.updateQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Search records or transcript text") },
                    )
                    Text(
                        text = buildFilterSummary(state.filters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.sessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No records match that search or filter")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.sessions, key = { it.session.id }) { item ->
                                RecordRow(
                                    item = item,
                                    onOpen = { onSessionClick(item.session.id) },
                                    selectionEnabled = state.selection.isSelecting,
                                    selected = item.session.id in state.selection.selectedSessionIds,
                                    onLongPress = { viewModel.enterSelectionMode(item.session.id) },
                                    onToggleSelection = { viewModel.toggleSelection(item.session.id) },
                                    onArchive = { viewModel.setArchived(item.session.id, true) },
                                    onRestore = { viewModel.setArchived(item.session.id, false) },
                                    onTransform = { viewModel.transformWithDefaultProfile(item.session.id) },
                                    onRename = { renameTarget = item },
                                    onDelete = { deleteTarget = item },
                                    onInfo = { infoTarget = item.toRecordInfo() },
                                    onOpenWith = { openAudioWith(context, item.session) },
                                    onSaveCopy = { viewModel.saveAudioCopy(item.session.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters && successState != null) {
        RecordsFilterDialog(
            current = successState.filters,
            onDismiss = { showFilters = false },
            onApply = {
                viewModel.updateFilters(it)
                showFilters = false
            },
        )
    }

    if (confirmBulkDelete && successState != null) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("Delete Selected Records") },
            text = {
                Text(
                    "Delete ${successState.selection.selectedSessionIds.size} selected records and their transcript data?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedSessions()
                        confirmBulkDelete = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    renameTarget?.let { item ->
        RenameSessionDialog(
            initialTitle = item.session.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                viewModel.renameSession(item.session.id, newTitle)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Record") },
            text = { Text("Delete ${item.session.title} and its transcript data?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(item.session.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    infoTarget?.let { info ->
        RecordInfoDialog(
            info = info,
            onDismiss = { infoTarget = null },
        )
    }
}
