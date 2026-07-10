package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.twobits.design.components.AppSectionCard
import com.twobits.design.components.AppSectionHeader
import dev.scrybe.core.common.CustomTypeIcon
import dev.scrybe.core.common.ModeBadge
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.SessionStatusChip
import dev.scrybe.core.common.customTypeIcon
import dev.scrybe.core.common.modeAccentColor
import dev.scrybe.core.common.modeIcon
import dev.scrybe.core.common.scrybeContentWidth
import dev.scrybe.core.common.shapeLiveAmplitude
import dev.scrybe.core.common.shapeWaveformBars
import dev.scrybe.core.model.CustomRecordingType
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TransformProfile
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToSessionDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    unminimizeRequested: Boolean = false,
    onUnminimizeConsumed: () -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val transformDialog by viewModel.transformDialog.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf<RecordingMode?>(null) }
    var pendingMode by remember { mutableStateOf(RecordingMode.JOURNAL) }
    var pendingCustomTypeId by remember { mutableStateOf<String?>(null) }
    var folderModeEnabled by remember { mutableStateOf(false) }
    var expandedFolderIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val requiredPermissions =
        remember {
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    val hasRequiredPermissions =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val audioGranted =
                results[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (audioGranted) {
                val customId = pendingCustomTypeId
                if (customId != null) {
                    viewModel.startRecordingWithCustomType(customId)
                    pendingCustomTypeId = null
                } else {
                    viewModel.startRecordingWithMode(pendingMode)
                }
            }
        }

    DisposableEffect(view, uiState.phase, uiState.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = uiState.keepScreenOn && uiState.phase != CapturePhase.IDLE
        onDispose { view.keepScreenOn = previous }
    }

    val filteredSessions =
        remember(uiState.recentSessions, searchQuery, filterMode) {
            uiState.recentSessions.filter { session ->
                (filterMode == null || session.mode == filterMode) &&
                    (
                        searchQuery.isBlank() ||
                            session.title.contains(searchQuery, ignoreCase = true) ||
                            session.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                            session.locationLabel?.contains(searchQuery, ignoreCase = true) == true ||
                            session.transcriptPreview?.contains(searchQuery, ignoreCase = true) == true
                    )
            }
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.unminimize()
        }
    }

    LaunchedEffect(unminimizeRequested) {
        if (unminimizeRequested) {
            viewModel.unminimize()
            onUnminimizeConsumed()
        }
    }

    BackHandler(enabled = uiState.isSelecting) {
        viewModel.clearSelection()
    }

    BackHandler(enabled = uiState.phase != CapturePhase.IDLE && !uiState.minimized) {
        viewModel.minimize()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelecting) {
                        Text("${uiState.selectedSessionIds.size} selected")
                    } else {
                        Text("Scrybe", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    if (uiState.isSelecting) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (uiState.isSelecting) {
                        if (uiState.selectedSessionIds.size == 1) {
                            IconButton(onClick = {
                                val session = uiState.recentSessions.find { it.id == uiState.selectedSessionIds.first() }
                                renameText = session?.title.orEmpty()
                                showRenameDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                        }
                        IconButton(onClick = { showArchiveConfirm = true }) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archive selected")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = viewModel::openTransformDialog) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = "Transform selected")
                        }
                    } else {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search sessions")
                        }
                        IconButton(onClick = { folderModeEnabled = !folderModeEnabled }) {
                            Icon(
                                if (folderModeEnabled) Icons.Filled.ViewList else Icons.Filled.FolderOpen,
                                contentDescription = if (folderModeEnabled) "List view" else "Folder view",
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.phase != CapturePhase.IDLE && !uiState.minimized) {
                RecordingActiveView(
                    state = uiState,
                    paddingValues = paddingValues,
                    onStop = { viewModel.stopRecording() },
                    onStopSaveRawOnly = { viewModel.stopRecording(skipTransform = true) },
                    onBack = viewModel::minimize,
                    onCancel = viewModel::cancelRecording,
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                    onDismissResult = viewModel::dismissTranscriptResult,
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search sessions…") },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = ScrybeLayoutDefaults.screenHorizontalPadding,
                                        vertical = 4.dp,
                                    ).padding(top = paddingValues.calculateTopPadding()),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = ScrybeLayoutDefaults.screenHorizontalPadding,
                                top = if (searchOpen) 4.dp else paddingValues.calculateTopPadding() + 4.dp,
                                end = ScrybeLayoutDefaults.screenHorizontalPadding,
                                bottom = paddingValues.calculateBottomPadding() + 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item(key = "filter") {
                            ModeFilterRow(selected = filterMode, onSelect = { filterMode = it })
                        }
                        if (uiState.openTaskTotal > 0) {
                            item(key = "task-nudge") {
                                TaskNudgeBanner(
                                    openTaskTotal = uiState.openTaskTotal,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .scrybeContentWidth(),
                                )
                            }
                        }
                        uiState.errorMessage?.let { message ->
                            item(key = "error") {
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .scrybeContentWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                ) {
                                    Text(
                                        text = "Error: $message",
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                        if (filteredSessions.isEmpty() &&
                            uiState.phase == CapturePhase.IDLE &&
                            searchQuery.isBlank() &&
                            filterMode == null
                        ) {
                            item(key = "intro") { IntroGuidanceSection() }
                        }
                        if (filteredSessions.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "no-results") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "No sessions match \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (folderModeEnabled) {
                            val grouped = filteredSessions.groupBy { it.folderId }
                            val folderIds =
                                grouped.keys
                                    .filterNotNull()
                                    .sortedBy { uiState.folderNames[it] ?: it }
                            val unfiled = grouped[null] ?: emptyList()
                            folderIds.forEach { folderId ->
                                val name = uiState.folderNames[folderId] ?: folderId
                                val expanded = folderId in expandedFolderIds
                                val sessions = grouped[folderId] ?: emptyList()
                                if (sessions.isEmpty()) return@forEach
                                item(key = "folder-$folderId") {
                                    FolderSectionHeader(
                                        name = name,
                                        count = sessions.size,
                                        expanded = expanded,
                                        onToggle = {
                                            expandedFolderIds =
                                                if (expanded) {
                                                    expandedFolderIds - folderId
                                                } else {
                                                    expandedFolderIds + folderId
                                                }
                                        },
                                    )
                                }
                                if (expanded) {
                                    items(sessions, key = { "f-${it.id}" }) { session ->
                                        HomeSessionCard(
                                            session = session,
                                            isSelected = session.id in uiState.selectedSessionIds,
                                            isSelecting = uiState.isSelecting,
                                            onClick = {
                                                if (uiState.isSelecting) {
                                                    viewModel.toggleSelection(session.id)
                                                } else {
                                                    onNavigateToSessionDetail(session.id)
                                                }
                                            },
                                            onLongClick = { viewModel.enterSelectionMode(session.id) },
                                        )
                                    }
                                }
                            }
                            if (unfiled.isNotEmpty()) {
                                item(key = "folder-unfiled") {
                                    val expanded = "unfiled" in expandedFolderIds
                                    FolderSectionHeader(
                                        name = "No folder",
                                        count = unfiled.size,
                                        expanded = expanded,
                                        onToggle = {
                                            expandedFolderIds =
                                                if (expanded) {
                                                    expandedFolderIds - "unfiled"
                                                } else {
                                                    expandedFolderIds + "unfiled"
                                                }
                                        },
                                    )
                                }
                                if ("unfiled" in expandedFolderIds) {
                                    items(unfiled, key = { "u-${it.id}" }) { session ->
                                        HomeSessionCard(
                                            session = session,
                                            isSelected = session.id in uiState.selectedSessionIds,
                                            isSelecting = uiState.isSelecting,
                                            onClick = {
                                                if (uiState.isSelecting) {
                                                    viewModel.toggleSelection(session.id)
                                                } else {
                                                    onNavigateToSessionDetail(session.id)
                                                }
                                            },
                                            onLongClick = { viewModel.enterSelectionMode(session.id) },
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredSessions, key = { it.id }) { session ->
                                HomeSessionCard(
                                    session = session,
                                    isSelected = session.id in uiState.selectedSessionIds,
                                    isSelecting = uiState.isSelecting,
                                    onClick = {
                                        if (uiState.isSelecting) {
                                            viewModel.toggleSelection(session.id)
                                        } else {
                                            onNavigateToSessionDetail(session.id)
                                        }
                                    },
                                    onLongClick = { viewModel.enterSelectionMode(session.id) },
                                )
                            }
                        }
                        item(key = "bottom-spacer") { Spacer(Modifier.height(80.dp)) }
                    }
                } // end Column
                if (uiState.phase == CapturePhase.IDLE) {
                    FloatingActionButton(
                        onClick = viewModel::showModePicker,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Start recording")
                    }
                }
            } // end IDLE branch
        }
    }

    if (uiState.showModePickerSheet) {
        ModePickerSheet(
            onStartRecording = { mode ->
                if (hasRequiredPermissions) {
                    viewModel.startRecordingWithMode(mode)
                } else {
                    pendingMode = mode
                    pendingCustomTypeId = null
                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                }
            },
            onStartCustomType = { typeId ->
                if (hasRequiredPermissions) {
                    viewModel.startRecordingWithCustomType(typeId)
                } else {
                    pendingCustomTypeId = typeId
                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                }
            },
            customTypes = uiState.customTypes,
            profiles = profiles,
            onCreateType = { name, profileId, iconName -> viewModel.createCustomType(name, profileId, iconName) },
            onDeleteType = viewModel::deleteCustomType,
            onDismiss = viewModel::dismissModePicker,
        )
    }

    transformDialog?.let { dialog ->
        CaptureTransformPickerSheet(
            dialog = dialog,
            profiles = profiles,
            onPickProfile = viewModel::runTransformFromDialog,
            onDismiss = viewModel::closeTransformDialog,
        )
    }

    if (showDeleteConfirm) {
        val count = uiState.selectedSessionIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $count recording${if (count == 1) "" else "s"}?") },
            text = { Text("This will permanently delete the selected recordings and their transcripts.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelectedSessions()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showArchiveConfirm) {
        val count = uiState.selectedSessionIds.size
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive $count recording${if (count == 1) "" else "s"}?") },
            text = { Text("Archived recordings are hidden from the main list.") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    viewModel.setArchivedForSelected(true)
                }) {
                    Text("Archive")
                }
            },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showRenameDialog) {
        val sessionId = uiState.selectedSessionIds.firstOrNull()
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename recording") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sessionId != null) viewModel.renameSession(sessionId, renameText)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecordingActiveView(
    state: CaptureUiState,
    paddingValues: PaddingValues,
    onStop: () -> Unit,
    onStopSaveRawOnly: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val isStopping = state.phase == CapturePhase.STOPPING
    val isPaused = state.phase == CapturePhase.PAUSED
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
    ) {
        RecordingActiveHeader(
            state = state,
            isStopping = isStopping,
            isPaused = isPaused,
            onBack = onBack,
            onPause = onPause,
            onResume = onResume,
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AmplitudeWaveform(amplitudeHistory = if (isPaused) emptyList() else state.amplitudeHistory)
                Spacer(Modifier.height(8.dp))
                RecordingTimerRow(elapsedMs = state.elapsedMs, isStopping = isStopping, isPaused = isPaused)
            }
        }
        LiveTranscriptPanel(
            state = state,
            onDismissResult = onDismissResult,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (!isStopping || state.liveTranscript == null) {
            RecordingStopButtons(
                modeName = state.activeCustomTypeName ?: state.activeMode.label,
                enabled = !isStopping,
                onStop = onStop,
                onStopSaveRawOnly = onStopSaveRawOnly,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun LiveTranscriptPanel(
    state: CaptureUiState,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isStopping = state.phase == CapturePhase.STOPPING
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Live transcript",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            when {
                isStopping && state.liveTranscript != null -> {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = state.liveTranscript,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDismissResult,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Done")
                        }
                    }
                }
                isStopping -> {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Saving and transcribing…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.phase == CapturePhase.RECORDING &&
                    state.streamingStatus == LiveStreamStatus.STREAMING &&
                    state.streamingPartialTranscript != null -> {
                    Text(
                        text = state.streamingPartialTranscript,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
                !state.autoTranscribeEnabled -> {
                    Text(
                        text = "Auto-transcription is off — enable it in Settings to get a transcript after recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                tween(900, easing = EaseInOut),
                                RepeatMode.Reverse,
                            ),
                        label = "alpha",
                    )
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        )
                        Text(
                            text =
                                when {
                                    state.streamingStatus == LiveStreamStatus.CONNECTING ->
                                        "Connecting live transcript…"
                                    state.streamingStatus == LiveStreamStatus.DROPPED ->
                                        "Live transcript lost connection — will transcribe after you stop"
                                    state.activeCustomTypeName != null ->
                                        "Will transcribe automatically • ${state.activeCustomTypeName} profile"
                                    else ->
                                        "Will transcribe automatically • processed as ${state.activeMode.label}"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingActiveHeader(
    state: CaptureUiState,
    isStopping: Boolean,
    isPaused: Boolean,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = !isStopping) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        ModeBadge(mode = state.activeMode, customLabel = state.activeCustomTypeName)
        Spacer(Modifier.weight(1f))
        if (isStopping) {
            Text(
                text = "Stopping…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        } else {
            Surface(
                onClick = if (isPaused) onResume else onPause,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = if (isPaused) "Resume" else "Pause",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordingTimerRow(
    elapsedMs: Long,
    isStopping: Boolean,
    isPaused: Boolean,
) {
    val isActivelyRecording = !isPaused && !isStopping
    val infiniteTransition = rememberInfiniteTransition(label = "rec-pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dot-alpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary.copy(
                                alpha = if (isActivelyRecording) dotAlpha else 1f,
                            ),
                            CircleShape,
                        ),
            )
            Text(
                text =
                    when {
                        isStopping -> "Stopping…"
                        isPaused -> "Paused"
                        else -> "Recording"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            text = formatElapsed(elapsedMs),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun RecordingStopButtons(
    modeName: String,
    enabled: Boolean,
    onStop: () -> Unit,
    onStopSaveRawOnly: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onStop,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                "Stop — process as $modeName",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        OutlinedButton(
            onClick = onStopSaveRawOnly,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 11.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                "Stop, save raw transcript only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Cancel recording",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AmplitudeWaveform(amplitudeHistory: List<Float>) {
    val bars = amplitudeHistory.takeLast(48)
    val recentCount = 6
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val totalBars = 48
        val padding = totalBars - bars.size
        repeat(padding) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(0.08f)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            RoundedCornerShape(2.dp),
                        ),
            )
        }
        bars.forEachIndexed { index, amplitude ->
            val isRecent = index >= bars.size - recentCount
            val gradientRatio = index.toFloat() / (bars.size - recentCount).coerceAtLeast(1)
            // sqrt shaping keeps quiet speech visibly alive instead of hovering at the floor.
            val shaped = shapeLiveAmplitude(amplitude)
            val color =
                if (isRecent) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f + shaped * 0.45f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + gradientRatio * 0.42f + shaped * 0.12f)
                }
            val heightFraction = (0.08f + shaped * 0.92f).coerceIn(0.08f, 1f)
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(heightFraction)
                        .background(color, RoundedCornerShape(percent = 50)),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePickerSheet(
    onStartRecording: (RecordingMode) -> Unit,
    onStartCustomType: (String) -> Unit,
    customTypes: List<CustomRecordingType>,
    profiles: List<TransformProfile>,
    onCreateType: (String, String?, String?) -> Unit,
    onDeleteType: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember { mutableStateOf(RecordingMode.JOURNAL) }
    var showCreateTypeDialog by remember { mutableStateOf(false) }
    var typePendingDelete by remember { mutableStateOf<CustomRecordingType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    // Scrollable + inset-aware: the sheet's content (modes grid, preview, custom
                    // types, action buttons) can exceed the sheet height, which used to clip the
                    // Start recording / New type buttons behind the navigation bar.
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "What are you capturing?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose a mode to shape the AI pipeline for this recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RecordingMode.entries.filter { it != RecordingMode.CUSTOM }.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { mode ->
                        ModeCard(
                            mode = mode,
                            isSelected = mode == selectedMode,
                            onClick = { selectedMode = mode },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            ModeOutputPreview(selectedMode = selectedMode)
            if (customTypes.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Custom types",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val profileMap = profiles.associate { it.id to it.name }
                customTypes.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { customType ->
                            CustomTypeCard(
                                customType = customType,
                                profileName = customType.defaultProfileId?.let { profileMap[it] },
                                onClick = { onStartCustomType(customType.id) },
                                onDelete = { typePendingDelete = customType },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onStartRecording(selectedMode) },
                    modifier = Modifier.weight(1f).padding(bottom = 16.dp),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Start recording")
                }
                OutlinedButton(
                    onClick = { showCreateTypeDialog = true },
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("New type")
                }
            }
        }
    }

    if (showCreateTypeDialog) {
        CreateTypeDialog(
            profiles = profiles,
            onConfirm = { name, profileId, iconName ->
                onCreateType(name, profileId, iconName)
                showCreateTypeDialog = false
            },
            onDismiss = { showCreateTypeDialog = false },
        )
    }

    typePendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { typePendingDelete = null },
            title = { Text("Delete \"${pending.name}\"?") },
            text = {
                Text(
                    "Recordings made with this type are kept and become Journal recordings. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteType(pending.id)
                    typePendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { typePendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateTypeDialog(
    profiles: List<TransformProfile>,
    onConfirm: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var selectedIcon by remember { mutableStateOf(CustomTypeIcon.LABEL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New recording type") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Custom types record audio just like standard modes. Link a transform profile so Scrybe automatically processes the transcript your way after recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Type name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomTypeIcon.entries.forEach { option ->
                        val selected = option == selectedIcon
                        Surface(
                            onClick = { selectedIcon = option },
                            shape = CircleShape,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                        ) {
                            Icon(
                                imageVector = option.vector,
                                contentDescription = option.name.lowercase(),
                                modifier = Modifier.padding(8.dp).size(20.dp),
                                tint =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
                if (profiles.isNotEmpty()) {
                    Text(
                        "Default transform profile (optional)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    profiles.forEach { profile ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProfileId = if (selectedProfileId == profile.id) null else profile.id }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = selectedProfileId == profile.id,
                                onCheckedChange = { checked ->
                                    selectedProfileId = if (checked) profile.id else null
                                },
                            )
                            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedProfileId, selectedIcon.name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ModeOutputPreview(
    selectedMode: RecordingMode,
    modifier: Modifier = Modifier,
) {
    val accent = modeAccentColor(selectedMode)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = "Will produce: ${selectedMode.outputDescription}",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
        )
    }
}

@Composable
private fun MiniWaveform(
    samples: List<Float>,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.height(28.dp)) {
        if (samples.isEmpty()) return@Canvas
        val targetCount = 48
        // Peak-normalized + sqrt-shaped so quiet recordings still read as a waveform instead of
        // a near-flat line, and transients survive the downsampling.
        val bars = shapeWaveformBars(samples, targetCount)
        val barWidth = (size.width / (targetCount * 2.2f)).coerceAtLeast(1f)
        val gap = size.width / targetCount - barWidth
        bars.forEachIndexed { i, amp ->
            val barHeight = (4f + amp * (size.height - 4f)).coerceAtLeast(4f)
            val x = i * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = baseColor.copy(alpha = 0.28f + amp * 0.42f),
                start =
                    androidx.compose.ui.geometry
                        .Offset(x, size.height / 2f - barHeight / 2f),
                end =
                    androidx.compose.ui.geometry
                        .Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: RecordingMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = modeAccentColor(mode)
    val containerColor =
        if (isSelected) {
            accentColor.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (isSelected) {
            accentColor
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = modeIcon(mode),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = mode.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Text(
                text = mode.outputDescription,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A user-created recording type rendered with the same card language as the built-in [ModeCard]s
 * — icon, name, output subtitle, CUSTOM accent — so custom types read as first-class modes.
 * Starts a recording on tap; the trailing delete affordance asks for confirmation and reassigns
 * the type's recordings to Journal.
 */
@Composable
private fun CustomTypeCard(
    customType: CustomRecordingType,
    profileName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = modeAccentColor(RecordingMode.CUSTOM)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = customTypeIcon(customType.iconName),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${customType.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clickable(onClick = onDelete),
                )
            }
            Text(
                text = customType.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profileName ?: "Plain transcript",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeSessionCard(
    session: RecentCaptureSession,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .scrybeContentWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSelecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSelected, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    HomeSessionCardHeader(session)
                }
            } else {
                HomeSessionCardHeader(session)
            }
            if (session.waveformSamples.isNotEmpty()) {
                MiniWaveform(samples = session.waveformSamples, modifier = Modifier.fillMaxWidth())
            }
            HomeSessionCardFooter(session = session)
            session.transcriptPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeSessionCardHeader(session: RecentCaptureSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeBadge(mode = session.mode)
                session.locationLabel?.let { loc ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            loc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                session.createdAtLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.durationMs > 0L) {
                Text(
                    formatCardDuration(session.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatCardDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        "%dh %02dm".format(minutes / 60, minutes % 60)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun HomeSessionCardFooter(session: RecentCaptureSession) {
    val hasMultipleSpeakers = session.speakerCount > 1
    val hasOpenTasks = session.openTaskCount > 0
    val isArchived = session.isArchived
    val hasSpecialStatus =
        session.status == SessionStatus.FAILED ||
            session.status == SessionStatus.TRANSCRIBING ||
            session.status == SessionStatus.PARTIAL_TRANSCRIPTION
    val hasContent =
        hasMultipleSpeakers ||
            hasOpenTasks ||
            isArchived ||
            hasSpecialStatus ||
            session.tags.isNotEmpty()
    if (!hasContent) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left section: status + speaker count + open tasks
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isArchived) {
                Icon(
                    Icons.Filled.Archive,
                    contentDescription = "Archived",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (hasSpecialStatus) {
                val icon =
                    when (session.status) {
                        SessionStatus.FAILED -> Icons.Filled.Error
                        SessionStatus.TRANSCRIBING -> Icons.Filled.HourglassEmpty
                        else -> Icons.Filled.Description
                    }
                Icon(icon, contentDescription = session.status.name, modifier = Modifier.size(14.dp))
            }
            if (hasMultipleSpeakers) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${session.speakerCount} spk",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hasOpenTasks) {
                SessionTasksChip(count = session.openTaskCount)
            }
        }
        // Right section: tags
        if (session.tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                session.tags.take(3).forEach { tag ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "#$tag",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTasksChip(count: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "$count task${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ModeFilterRow(
    selected: RecordingMode?,
    onSelect: (RecordingMode?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        RecordingMode.entries.forEach { mode ->
            val accentColor = modeAccentColor(mode)
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(if (selected == mode) null else mode) },
                label = { Text(mode.label) },
                leadingIcon = {
                    Icon(modeIcon(mode), contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor,
                        selectedLeadingIconColor = accentColor,
                    ),
            )
        }
    }
}

@Composable
private fun IntroGuidanceSection() {
    AppSectionCard(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppSectionHeader(
            title = "Start with a confident first recording",
            subtitle =
                "Scrybe saves the raw audio first, then layers review tools on top so you can revisit the important moments later.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IntroGuidanceRow(
                icon = Icons.Filled.CheckCircle,
                title = "Capture first, process after",
                body = "Recordings are saved before transcription, speaker analysis, or tagging workflows begin.",
            )
            IntroGuidanceRow(
                icon = Icons.Filled.Description,
                title = "Review the details quickly",
                body = "Open a session to scrub the waveform, inspect transcripts, and jump between speakers or intent markers.",
            )
            IntroGuidanceRow(
                icon = Icons.Filled.Archive,
                title = "Keep your library organized",
                body = "Rename, tag, archive, and revisit sessions without losing track of the original audio.",
            )
        }
    }
}

@Composable
private fun IntroGuidanceRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskNudgeBanner(
    openTaskTotal: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "$openTaskTotal open task${if (openTaskTotal == 1) "" else "s"} across your sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FolderSectionHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .scrybeContentWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (expanded) Icons.Filled.ViewList else Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentSessionMiniRow(session: RecentCaptureSession) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModeBadge(mode = session.mode)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildString {
                        if (session.durationMs > 0L) append(formatCardDuration(session.durationMs))
                        if (session.durationMs > 0L) append(" · ")
                        append(session.createdAtLabel)
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SessionStatusChip(status = session.status)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTransformPickerSheet(
    dialog: CaptureTransformDialogState,
    profiles: List<dev.scrybe.core.model.TransformProfile>,
    onPickProfile: (dev.scrybe.core.model.TransformProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val titleText =
                if (dialog.sessionTitles.size == 1) {
                    "Transform: ${dialog.sessionTitles.first()}"
                } else {
                    "Transform ${dialog.sessionIds.size} recordings"
                }
            Text(titleText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            dialog.result?.let { result ->
                Text(
                    "✓ ${result.profileName} complete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                HorizontalDivider()
            }
            if (dialog.runningProfileId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Running transform…")
                }
            } else {
                profiles.forEach { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = profile.description.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        leadingContent = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
                        modifier = Modifier.clickable { onPickProfile(profile) },
                    )
                }
                if (profiles.isEmpty()) {
                    Text(
                        "No transform profiles — add one in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
