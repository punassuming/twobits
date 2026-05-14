package dev.scrybe.feature.history

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.scrybeContentWidth
import dev.scrybe.core.model.Folder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAiWorking by viewModel.isAiWorking.collectAsState()
    val transformDialog by viewModel.transformDialog.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var expandedFolderIds by remember { mutableStateOf(emptySet<String>()) }
    var renameTarget by remember { mutableStateOf<HistorySessionItem?>(null) }
    var tagsTarget by remember { mutableStateOf<HistorySessionItem?>(null) }
    var infoTarget by remember { mutableStateOf<Pair<String, RecordInfo>?>(null) }
    var deleteTarget by remember { mutableStateOf<HistorySessionItem?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showMoveToFolder by remember { mutableStateOf(false) }
    var moveSessionTarget by remember { mutableStateOf<String?>(null) }
    var renameFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var moveFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var transformResultEvent by remember { mutableStateOf<HistoryEvent.TransformResult?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTagBrowser by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { viewModel.importRecording(it) }
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is HistoryEvent.ShareText -> {
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, event.title)
                            putExtra(Intent.EXTRA_TEXT, event.text)
                        }
                    context.startActivity(Intent.createChooser(intent, "Share transcript"))
                }
                is HistoryEvent.TransformResult -> {
                    transformResultEvent = event
                }
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelecting) {
                            "${successState?.selection?.selectedSessionIds?.size ?: 0} selected"
                        } else {
                            "Records"
                        },
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
                colors =
                    if (isSelecting) {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                actions = {
                    if (isSelecting && successState != null) {
                        IconButton(onClick = { viewModel.selectAllVisible() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all visible records")
                        }
                        IconButton(onClick = { viewModel.transcribeSelectedSessions() }) {
                            Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Transcribe selected records")
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                HorizontalDivider()
                                Text(
                                    text = "AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text("Run Transform") },
                                    leadingIcon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
                                    onClick = {
                                        viewModel.openTransformDialog(
                                            successState.selection.selectedSessionIds.toList(),
                                        )
                                        showOverflowMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Organize with AI") },
                                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                                    onClick = {
                                        viewModel.suggestAndApplyClusters(
                                            successState.selection.selectedSessionIds,
                                        )
                                        showOverflowMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("AI Rename") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        viewModel.autoRenameSelectedSessions()
                                        showOverflowMenu = false
                                    },
                                )
                                HorizontalDivider()
                                Text(
                                    text = "Manage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Folder") },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        showMoveToFolder = true
                                        showOverflowMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (successState.filters.showArchived) "Restore" else "Archive",
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (successState.filters.showArchived) {
                                                Icons.Filled.Unarchive
                                            } else {
                                                Icons.Filled.Archive
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        viewModel.setArchivedForSelected(
                                            !successState.filters.showArchived,
                                        )
                                        showOverflowMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        confirmBulkDelete = true
                                        showOverflowMenu = false
                                    },
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        val filterCount =
                            (successState?.filters)?.let { activeRecordsFilterCount(it) } ?: 0
                        BadgedBox(
                            badge = { if (filterCount > 0) Badge { Text(filterCount.toString()) } },
                        ) {
                            IconButton(onClick = { showFilters = true }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                            }
                        }
                        IconButton(onClick = { showCreateFolder = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import recording") },
                                    leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                                    onClick = {
                                        importLauncher.launch(arrayOf("audio/*"))
                                        showOverflowMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is HistoryUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

            is HistoryUiState.Error ->
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }

            is HistoryUiState.Success -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = ScrybeLayoutDefaults.screenHorizontalPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .scrybeContentWidth()
                                .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
                    ) {
                        if (isAiWorking) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        FolderPickerChip(
                            currentFolderId = state.currentFolderId,
                            breadcrumb = state.breadcrumb,
                            onClick = { showFolderSheet = true },
                        )
                        if (state.availableTags.isNotEmpty()) {
                            TagFilterRow(
                                availableTags = state.availableTags,
                                selectedTag = state.filters.selectedTag,
                                onSelectTag = { viewModel.selectTag(it) },
                            )
                        }
                        AnimatedVisibility(
                            visible = searchVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        viewModel.updateQuery(it)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (searchQuery.isNotEmpty() && !state.semanticSearchLoading) {
                                                IconButton(
                                                    onClick = { viewModel.triggerSemanticSearch(searchQuery) },
                                                ) {
                                                    Icon(
                                                        Icons.Filled.AutoAwesome,
                                                        contentDescription = "AI search",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    searchQuery = ""
                                                    viewModel.updateQuery("")
                                                    viewModel.clearSemanticSearch()
                                                }) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                                }
                                            }
                                        }
                                    },
                                    placeholder = { Text("Title, tags, or transcript text") },
                                )
                                if (state.semanticSearchLoading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                if (state.semanticRankedIds != null && !state.semanticSearchLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            "AI results · ${state.semanticRankedIds.size} matches",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            onClick = { viewModel.clearSemanticSearch() },
                                        ) {
                                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = state.filters.selectedTag != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.selectTag(null) },
                                label = { Text("Tag: ${state.filters.selectedTag.orEmpty()}") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear tag filter",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                        AnimatedVisibility(
                            visible =
                                !isSelecting &&
                                    state.subfolders.isEmpty() &&
                                    state.currentFolderId == null &&
                                    state.sessions.size >= 3,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.suggestAndApplyClusters() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Organize with AI")
                            }
                        }
                        if (state.sessions.isEmpty() && state.subfolders.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(top = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text(
                                        when {
                                            state.currentFolderId != null -> "This folder is empty"
                                            state.filters.showArchived -> "No archived records"
                                            else -> "No records match that search or filter"
                                        },
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            importLauncher.launch(
                                                arrayOf("audio/*"),
                                            )
                                        },
                                    ) {
                                        Row(
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp),
                                            verticalAlignment =
                                                Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.FileOpen,
                                                contentDescription = null,
                                            )
                                            Text("Import Recording")
                                        }
                                    }
                                }
                            }
                        } else {
                            val sessionRow: @Composable (HistorySessionItem) -> Unit = { item ->
                                RecordRow(
                                    item = item,
                                    onOpen = { onSessionClick(item.session.id) },
                                    selectionEnabled = state.selection.isSelecting,
                                    selected = item.session.id in state.selection.selectedSessionIds,
                                    onLongPress = { viewModel.enterSelectionMode(item.session.id) },
                                    onToggleSelection = { viewModel.toggleSelection(item.session.id) },
                                    onArchive = {
                                        val id = item.session.id
                                        scope.launch {
                                            viewModel.setArchived(id, true)
                                            val result = snackbarHostState.showSnackbar("Archived", actionLabel = "Undo")
                                            if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(id, false)
                                        }
                                    },
                                    onRestore = {
                                        val id = item.session.id
                                        scope.launch {
                                            viewModel.setArchived(id, false)
                                            val result = snackbarHostState.showSnackbar("Restored", actionLabel = "Undo")
                                            if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(id, true)
                                        }
                                    },
                                    onTransform = { viewModel.openTransformDialog(listOf(item.session.id)) },
                                    onRename = { renameTarget = item },
                                    onManageTags = { tagsTarget = item },
                                    onDelete = { deleteTarget = item },
                                    onInfo = { infoTarget = item.session.id to item.toRecordInfo() },
                                    onOpenWith = { openAudioWith(context, item.session) },
                                    onSaveCopy = { viewModel.saveAudioCopy(item.session.id) },
                                    onShareTranscript = { viewModel.shareTranscript(item.session.id) },
                                    onMoveToFolder = { moveSessionTarget = item.session.id },
                                    onRetryTranscription = { viewModel.retryTranscription(item.session.id) },
                                    onResetTranscriptionState = { viewModel.resetTranscriptionState(item.session.id) },
                                    showRecordingInfo = state.interactionPreferences.showRecordingInfoInList,
                                    onTagClick = { tag -> viewModel.selectTag(tag) },
                                )
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    for (folder in state.subfolders) {
                                        item(key = "folder-${folder.id}") {
                                            FolderRow(
                                                folder = folder,
                                                expanded = folder.id in expandedFolderIds,
                                                onClick = {
                                                    expandedFolderIds =
                                                        if (folder.id in expandedFolderIds) {
                                                            expandedFolderIds - folder.id
                                                        } else {
                                                            expandedFolderIds + folder.id
                                                        }
                                                },
                                                onRename = { renameFolderTarget = folder },
                                                onDelete = { deleteFolderTarget = folder },
                                                onMove = { moveFolderTarget = folder },
                                            )
                                        }
                                        if (folder.id in expandedFolderIds) {
                                            val folderItems =
                                                state.sessionsByFolderId[folder.id].orEmpty()
                                            if (folderItems.isEmpty()) {
                                                item(key = "folder-${folder.id}-empty") {
                                                    Text(
                                                        "This folder is empty",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
                                                    )
                                                }
                                            } else {
                                                items(
                                                    folderItems,
                                                    key = { "fi-${it.session.id}" },
                                                ) { item ->
                                                    Box(Modifier.padding(start = 28.dp)) {
                                                        sessionRow(item)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    items(state.sessions, key = { it.session.id }) { item ->
                                        sessionRow(item)
                                    }
                                }
                                if (state.subfolders.isNotEmpty()) {
                                    val allExpanded = state.subfolders.all { it.id in expandedFolderIds }
                                    val collapsedCount = state.subfolders.count { it.id !in expandedFolderIds }
                                    Surface(
                                        onClick = {
                                            expandedFolderIds =
                                                if (allExpanded) {
                                                    emptySet()
                                                } else {
                                                    state.subfolders.map { it.id }.toSet()
                                                }
                                        },
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(bottom = 12.dp, end = 4.dp),
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        tonalElevation = 4.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                                contentDescription = if (allExpanded) "Collapse all" else "Expand all",
                                                modifier = Modifier.size(18.dp),
                                            )
                                            if (!allExpanded && collapsedCount > 0) {
                                                Text(
                                                    "$collapsedCount",
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTagBrowser && successState != null) {
        TagBrowserSheet(
            availableTags = successState.availableTags,
            selectedTag = successState.filters.selectedTag,
            onSelectTag = { viewModel.selectTag(it) },
            onDismiss = { showTagBrowser = false },
        )
    }

    transformDialog?.let { dialog ->
        TransformPickerSheet(
            dialogState = dialog,
            profiles = profiles,
            onRunProfile = { viewModel.runTransformFromDialog(it) },
            onDismiss = { viewModel.closeTransformDialog() },
        )
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
            onSuggestAiTitle = { viewModel.suggestTitleForSession(item.session.id) },
        )
    }

    tagsTarget?.let { item ->
        HistoryTagsDialog(
            initialTags = item.session.tags,
            onDismiss = { tagsTarget = null },
            onSave = { tagsInput ->
                viewModel.saveTags(item.session.id, tagsInput)
                tagsTarget = null
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

    infoTarget?.let { (sessionId, info) ->
        RecordInfoDialog(
            info = info,
            onDismiss = { infoTarget = null },
            onDelete = {
                viewModel.deleteSession(sessionId)
            },
        )
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onConfirm = { name ->
                viewModel.createFolder(name, successState?.currentFolderId)
                showCreateFolder = false
            },
        )
    }

    if (showMoveToFolder && successState != null) {
        MoveFolderDialog(
            folders = successState.allFolders,
            onDismiss = { showMoveToFolder = false },
            onSelect = { folderId ->
                viewModel.moveSessionsToFolder(
                    sessionIds = successState.selection.selectedSessionIds.toList(),
                    folderId = folderId,
                )
                showMoveToFolder = false
            },
        )
    }

    moveSessionTarget?.let { sessionId ->
        if (successState != null) {
            MoveFolderDialog(
                folders = successState.allFolders,
                onDismiss = { moveSessionTarget = null },
                onSelect = { folderId ->
                    viewModel.moveSessionsToFolder(
                        sessionIds = listOf(sessionId),
                        folderId = folderId,
                    )
                    moveSessionTarget = null
                },
            )
        }
    }

    renameFolderTarget?.let { folder ->
        RenameFolderDialog(
            initialName = folder.name,
            onDismiss = { renameFolderTarget = null },
            onConfirm = { newName ->
                viewModel.renameFolder(folder.id, newName)
                renameFolderTarget = null
            },
        )
    }

    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("Delete Folder") },
            text = { Text("Delete \"${folder.name}\"? Its recordings will be moved to the root level.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        deleteFolderTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (moveFolderTarget != null && successState != null) {
        val targetFolder = moveFolderTarget!!
        val availableFolders =
            remember(targetFolder.id, successState.allFolders) {
                val excludedIds =
                    (folderDescendantIds(targetFolder.id, successState.allFolders) + targetFolder.id).toSet()
                successState.allFolders.filter { it.id !in excludedIds }
            }
        MoveFolderDialog(
            folders = availableFolders,
            onDismiss = { moveFolderTarget = null },
            onSelect = { newParentId ->
                viewModel.moveFolderToParent(targetFolder.id, newParentId)
                moveFolderTarget = null
            },
        )
    }

    transformResultEvent?.let { result ->
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { transformResultEvent = null },
            title = { Text("${result.profileName} result") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = result.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { transformResultEvent = null }) {
                    Text("Done")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                    TextButton(
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, result.text)
                                }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                            transformResultEvent = null
                        },
                    ) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            },
        )
    }
    if (showFolderSheet && successState != null) {
        FolderPickerBottomSheet(
            allFolders = successState.allFolders,
            currentFolderId = successState.currentFolderId,
            onNavigate = { folderId ->
                viewModel.navigateToFolder(folderId)
                showFolderSheet = false
            },
            onCreateFolder = {
                showFolderSheet = false
                showCreateFolder = true
            },
            onDismiss = { showFolderSheet = false },
        )
    }
}

@Composable
private fun HistoryTagsDialog(
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialTags) { mutableStateOf(initialTags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Tags") },
                supportingText = { Text("Use commas or new lines. Tags are searchable from Records.") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun folderDescendantIds(
    folderId: String,
    allFolders: List<Folder>,
): Set<String> {
    val result = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(folderId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        allFolders.filter { it.parentFolderId == current }.forEach { child ->
            result.add(child.id)
            queue.add(child.id)
        }
    }
    return result
}

@Composable
private fun FolderPickerChip(
    currentFolderId: String?,
    breadcrumb: List<Folder>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = breadcrumb.lastOrNull()?.name ?: "All Records"
    FilterChip(
        selected = currentFolderId != null,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                if (currentFolderId != null) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerBottomSheet(
    allFolders: List<Folder>,
    currentFolderId: String?,
    onNavigate: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedFolders = remember(allFolders) {
        allFolders.sortedWith(compareBy({ it.parentFolderId ?: "" }, { it.name }))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Browse Folders",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCreateFolder) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Surface(
                    onClick = { onNavigate(null) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (currentFolderId == null) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (currentFolderId == null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "All Records",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (currentFolderId == null) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                sortedFolders
                    .forEach { folder ->
                        val isSelected = folder.id == currentFolderId
                        Surface(
                            onClick = { onNavigate(folder.id) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = if (folder.parentFolderId != null) 48.dp else 24.dp,
                                    end = 24.dp,
                                    top = 14.dp,
                                    bottom = 14.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (isSelected) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun TagFilterRow(
    availableTags: List<Pair<String, Int>>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availableTags.forEach { (tag, count) ->
            FilterChip(
                selected = tag == selectedTag,
                onClick = { if (tag == selectedTag) onSelectTag(null) else onSelectTag(tag) },
                label = { Text("$tag ($count)", maxLines = 1) },
                trailingIcon =
                    if (tag == selectedTag) {
                        {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}
