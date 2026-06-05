package dev.scrybe.feature.sessiondetail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.common.ScrybeSectionHeader
import dev.scrybe.core.common.SessionStatusPresentation
import dev.scrybe.core.common.scrybeContentWidth
import dev.scrybe.core.database.FolderEntity
import dev.scrybe.core.model.Person
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.SessionTask
import dev.scrybe.core.model.SpeakerSegment
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import dev.scrybe.core.model.TransformProfile
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val analysisSuggestion by viewModel.analysisSuggestion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showMoreSheet by remember { mutableStateOf(false) }
    var showEcosystemSheet by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }
    var editingTranscript by remember { mutableStateOf<Transcript?>(null) }
    var isEditingTags by remember { mutableStateOf(false) }
    var deleteTranscriptTarget by remember { mutableStateOf<Transcript?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var transformResult by remember { mutableStateOf<SessionDetailEvent.TransformResult?>(null) }
    var showTransformSheet by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showSpeakerManageSheet by remember { mutableStateOf(false) }
    val transformSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var speakerAssignTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionDetailEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is SessionDetailEvent.ShareText -> {
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, event.title)
                            putExtra(Intent.EXTRA_TEXT, event.text)
                        }
                    context.startActivity(Intent.createChooser(intent, "Share transcript"))
                }
                is SessionDetailEvent.ShareFile -> {
                    val file = File(event.path)
                    val contentUri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(Intent.EXTRA_SUBJECT, event.title)
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(intent, "Share audio"))
                }
                is SessionDetailEvent.NavigateBack -> onNavigateBack()
                is SessionDetailEvent.TransformResult -> {
                    transformResult = event
                }
                is SessionDetailEvent.SendToExternal -> {
                    val intent =
                        Intent(event.action).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, event.title)
                            putExtra(Intent.EXTRA_TEXT, event.text)
                            if (event.packageName.isNotBlank()) {
                                setPackage(event.packageName)
                            }
                        }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            snackbarHostState.showSnackbar(
                                "Unable to send: ${it.message ?: "app not found"}",
                            )
                        }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val successState = uiState as? SessionDetailUiState.Success
            TopAppBar(
                title = {
                    val titleText = successState?.session?.title ?: "Session Review"
                    Column {
                        if (successState != null) {
                            Row(
                                modifier =
                                    Modifier.clickable(
                                        role = Role.Button,
                                        onClickLabel = "Rename recording",
                                        onClick = { showRenameDialog = true },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(text = titleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val subtitle =
                                buildString {
                                    append(
                                        successState.session.createdAt
                                            .atZone(ZoneId.systemDefault())
                                            .format(DETAIL_DATE_FORMATTER),
                                    )
                                    if (successState.session.durationMs > 0L) {
                                        append("  ·  ")
                                        append(formatDuration(successState.session.durationMs))
                                    }
                                }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(text = titleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (successState != null) {
                        IconButton(
                            onClick = viewModel::transcribe,
                            enabled = !successState.isTranscribing,
                        ) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = if (successState.isTranscribing) "Transcribing" else "Transcribe recording",
                            )
                        }
                        IconButton(onClick = { showEcosystemSheet = true }) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Send to")
                        }
                        IconButton(onClick = { showMoreSheet = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is SessionDetailUiState.Loading ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

            is SessionDetailUiState.Error ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }

            is SessionDetailUiState.Success -> {
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
                                .padding(vertical = 12.dp)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
                    ) {
                        SessionMetaStrip(state)
                        PlaybackCard(
                            state = state,
                            onTogglePlayback = viewModel::togglePlayback,
                            onStopPlayback = viewModel::stopPlayback,
                            onSkipBack = viewModel::skipBackward,
                            onSkipForward = viewModel::skipForward,
                            onSeek = viewModel::seekPlayback,
                            onSpeakerClick = { speakerId -> speakerAssignTarget = speakerId },
                            onManageSpeakers = { showSpeakerManageSheet = true },
                        )
                        TabRow(selectedTabIndex = activeTab) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = { Text("Output") },
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                text = { Text("Tasks") },
                            )
                            Tab(
                                selected = activeTab == 2,
                                onClick = { activeTab = 2 },
                                text = { Text("Transcript") },
                            )
                        }
                        when (activeTab) {
                            0 ->
                                OutputTabContent(
                                    state = state,
                                    onOpenTransformSheet = { showTransformSheet = true },
                                    onRunProfile = viewModel::transform,
                                    onAddToFolder = { showFolderSheet = true },
                                    onAnalyze = viewModel::analyzeRecording,
                                    onSuggestTags = viewModel::suggestTags,
                                    onSaveTags = viewModel::saveTags,
                                    onClearTagSuggestions = viewModel::clearTagSuggestionState,
                                )
                            1 ->
                                TasksTabContent(
                                    tasks = state.tasks,
                                    isExtracting = state.isExtractingTasks,
                                    hasTranscript = state.originalTranscript != null || state.currentTranscript != null,
                                    onExtractTasks = viewModel::extractTasks,
                                    onToggleDone = viewModel::toggleTaskDone,
                                    onAddTask = viewModel::addTask,
                                    onShareTasks = viewModel::shareLatestTranscript,
                                )
                            else ->
                                TranscriptTabContent(
                                    state = state,
                                    onEditTranscript = { t -> editingTranscript = t },
                                    onDeleteTranscript = { deleteTranscriptTarget = it },
                                    onResumeTranscription = viewModel::transcribe,
                                )
                        }
                    }
                }
            }
        }
    }

    val successState = uiState as? SessionDetailUiState.Success

    if (showTransformSheet && successState != null) {
        ModalBottomSheet(
            onDismissRequest = { showTransformSheet = false },
            sheetState = transformSheetState,
        ) {
            TransformSection(
                state = successState,
                onTransformDefault = viewModel::transformDefaultProfile,
                onTransformProfile = viewModel::transform,
                onDismiss = { showTransformSheet = false },
            )
        }
    }

    if (showFolderSheet && successState != null) {
        FolderPickerSheet(
            currentFolderId = successState.session.folderId,
            folders = folders,
            onPickFolder = { folderId ->
                viewModel.moveToFolder(folderId)
                showFolderSheet = false
            },
            onCreateFolder = { name ->
                viewModel.createFolderAndMove(name)
                showFolderSheet = false
            },
            onDismiss = { showFolderSheet = false },
        )
    }

    if (showMoreSheet && successState != null) {
        MoreMenuSheet(
            state = successState,
            onDismiss = { showMoreSheet = false },
            editCallbacks =
                MoreMenuEditCallbacks(
                    onRename = {
                        showMoreSheet = false
                        showRenameDialog = true
                    },
                    onManageTags = {
                        showMoreSheet = false
                        viewModel.clearTagSuggestionState()
                        isEditingTags = true
                    },
                    onSetArchived = {
                        showMoreSheet = false
                        viewModel.setArchived(!successState.session.isArchived)
                    },
                    onDelete = {
                        showMoreSheet = false
                        showDeleteConfirm = true
                    },
                    onTranscribe = {
                        showMoreSheet = false
                        viewModel.transcribe()
                    },
                    onResetTranscription = {
                        showMoreSheet = false
                        viewModel.resetTranscriptionState()
                    },
                ),
            exportCallbacks =
                MoreMenuExportCallbacks(
                    onPostProcess = {
                        showMoreSheet = false
                        showTransformSheet = true
                    },
                    onShareTranscript = {
                        showMoreSheet = false
                        viewModel.shareLatestTranscript()
                    },
                    onExportFiles = {
                        showMoreSheet = false
                        viewModel.exportAll()
                    },
                    onSendToExternal = {
                        showMoreSheet = false
                        viewModel.sendToTaskForge()
                    },
                ),
        )
    }

    if (showEcosystemSheet) {
        EcosystemSheet(
            onDismiss = { showEcosystemSheet = false },
            onShareTranscript = {
                viewModel.shareLatestTranscript()
                showEcosystemSheet = false
            },
        )
    }

    if (successState?.shouldPromptForRename == true) {
        RenamePromptDialog(
            initialTitle = successState.session.title,
            onDismiss = viewModel::dismissRenamePrompt,
            onConfirm = viewModel::renameSession,
            onSuggestAiTitle = viewModel::suggestTitle,
        )
    }

    if (showRenameDialog && successState != null) {
        RenamePromptDialog(
            initialTitle = successState.session.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                viewModel.renameSession(newTitle)
                showRenameDialog = false
            },
            onSuggestAiTitle = viewModel::suggestTitle,
        )
    }

    if (showDeleteConfirm && successState != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Recording") },
            text = { Text("Delete \"${successState.session.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession()
                        showDeleteConfirm = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    editingTranscript?.let { target ->
        EditTranscriptDialog(
            initialValue = target.content,
            onDismiss = { editingTranscript = null },
            onSave = { newContent ->
                viewModel.saveTranscriptEdit(newContent, target.id)
                editingTranscript = null
            },
        )
    }

    if (isEditingTags && successState != null) {
        TagEditorDialog(
            initialTags = successState.session.tags,
            tagSuggestionState = successState.tagSuggestionState,
            onDismiss = {
                viewModel.clearTagSuggestionState()
                isEditingTags = false
            },
            onSuggest = viewModel::suggestTags,
            onSave = { tags ->
                viewModel.saveTags(tags)
                isEditingTags = false
            },
        )
    }

    deleteTranscriptTarget?.let { transcript ->
        AlertDialog(
            onDismissRequest = { deleteTranscriptTarget = null },
            title = {
                Text(
                    when (transcript.type) {
                        TranscriptType.TRANSFORMED -> "Delete Transformation"
                        TranscriptType.EDITED -> "Delete Edited Transcript"
                        TranscriptType.RAW -> "Delete Transcript"
                    },
                )
            },
            text = {
                Text(
                    when (transcript.type) {
                        TranscriptType.TRANSFORMED -> "Delete this generated transformation output?"
                        TranscriptType.EDITED -> "Delete this edited transcript and fall back to the machine transcript?"
                        TranscriptType.RAW -> "Delete the transcription for this recording?"
                    },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deleteTranscript(transcript.id)
                        deleteTranscriptTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTranscriptTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    transformResult?.let { result ->
        TransformResultDialog(
            profileName = result.profileName,
            text = result.text,
            onDismiss = { transformResult = null },
            onShare = {
                viewModel.shareLatestTranscript()
                transformResult = null
            },
            onSave = {
                viewModel.saveTranscriptEdit(result.text)
                transformResult = null
            },
        )
    }
    val successForSpeaker = uiState as? SessionDetailUiState.Success
    speakerAssignTarget?.let { speakerId ->
        if (successForSpeaker != null) {
            PersonPickerDialog(
                persons = successForSpeaker.persons,
                currentPersonId = successForSpeaker.speakerSegments.find { it.speakerId == speakerId }?.personId,
                onDismiss = { speakerAssignTarget = null },
                onAssign = { personId ->
                    viewModel.assignPersonToSpeaker(speakerId, personId)
                    speakerAssignTarget = null
                },
                onCreateNew = { name ->
                    viewModel.createPersonAndAssign(speakerId, name)
                    speakerAssignTarget = null
                },
            )
        }
    }
    if (showSpeakerManageSheet && successForSpeaker != null) {
        ModalBottomSheet(onDismissRequest = { showSpeakerManageSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                SpeakerSlotsCard(
                    state = successForSpeaker,
                    onAssignPerson = viewModel::assignPersonToSpeaker,
                    onCreatePerson = viewModel::createPersonAndAssign,
                    onFetchSpeakerInfo = viewModel::fetchSpeakerInfo,
                    onMergeSpeakers = viewModel::mergeSpeakers,
                )
            }
        }
    }
    analysisSuggestion?.let { analysis ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissAnalysis) {
            AnalysisSuggestionSheet(
                state = analysis,
                onAcceptTitle = viewModel::acceptTitleSuggestion,
                onAcceptTags = viewModel::acceptTagsSuggestion,
                onAcceptMode = viewModel::acceptModeSuggestion,
                onDismiss = viewModel::dismissAnalysis,
            )
        }
    }
}

@Composable
private fun OutputTabContent(
    state: SessionDetailUiState.Success,
    onOpenTransformSheet: () -> Unit,
    onRunProfile: (String) -> Unit,
    onAddToFolder: () -> Unit,
    onAnalyze: () -> Unit,
    onSuggestTags: () -> Unit,
    onSaveTags: (List<String>) -> Unit,
    onClearTagSuggestions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing)) {
        TransformedOutputCard(state.transcripts)
        if (state.currentTranscript != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.profiles.isNotEmpty()) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onOpenTransformSheet) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Post-process")
                    }
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onAnalyze) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("✨ Analyze")
                }
            }
        }
        ProfileQuickActionsSection(
            profiles = state.profiles,
            hasTranscript = state.currentTranscript != null,
            onRunProfile = onRunProfile,
            onOpenTransformSheet = onOpenTransformSheet,
            onAddToFolder = onAddToFolder,
        )
        InlineTagsCard(
            tags = state.session.tags,
            tagSuggestionState = state.tagSuggestionState,
            onSaveTags = onSaveTags,
            onSuggestTags = onSuggestTags,
            onClear = onClearTagSuggestions,
        )
    }
}

@Composable
private fun TransformedOutputCard(transcripts: List<Transcript>) {
    val transformedTranscript =
        transcripts
            .filter { it.type == TranscriptType.TRANSFORMED }
            .maxByOrNull { it.createdAt }
    if (transformedTranscript != null) {
        ScrybeSectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            ScrybeSectionHeader(title = "Output", subtitle = "Most recent AI-generated output")
            Text(
                text = transformedTranscript.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        ScrybeSectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = "No output yet — transcribe and post-process to see AI summaries here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TasksTabContent(
    tasks: List<SessionTask>,
    isExtracting: Boolean,
    hasTranscript: Boolean,
    onExtractTasks: () -> Unit,
    onToggleDone: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onShareTasks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScrybeSectionCard(
            containerColor =
                if (tasks.isEmpty()) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
        ) {
            ScrybeSectionHeader(
                title = if (tasks.isEmpty()) "Tasks" else "Tasks (${tasks.size})",
                trailing = {
                    if (tasks.isNotEmpty()) {
                        IconButton(onClick = onShareTasks) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Export tasks", modifier = Modifier.size(20.dp))
                        }
                    }
                },
            )
            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.HourglassEmpty, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No tasks yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add manually below or extract with AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    tasks.forEachIndexed { index, task ->
                        TaskRow(task = task, onToggle = { onToggleDone(task.id) })
                        if (index < tasks.lastIndex) HorizontalDivider()
                    }
                }
            }
            HorizontalDivider()
            AddTaskRow(onAdd = onAddTask)
        }
        if (hasTranscript) {
            OutlinedButton(
                onClick = onExtractTasks,
                enabled = !isExtracting,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(),
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting…")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (tasks.isEmpty()) "Extract tasks" else "Re-extract tasks")
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: SessionTask,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
        ) {
            Text(
                text = task.text,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (task.isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            val meta =
                listOfNotNull(
                    task.assignee,
                    task.dueLabel?.let { "Due $it" },
                ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddTaskRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    if (isEditing) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            placeholder = { Text("Describe a task…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(onDone = {
                    if (text.isNotBlank()) onAdd(text)
                    text = ""
                    isEditing = false
                }),
        )
    } else {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { isEditing = true }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add task", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileQuickActionsSection(
    profiles: List<TransformProfile>,
    hasTranscript: Boolean,
    onRunProfile: (String) -> Unit,
    onOpenTransformSheet: () -> Unit,
    onAddToFolder: () -> Unit,
) {
    val quickProfiles = profiles.take(3)
    if (quickProfiles.isEmpty() && !hasTranscript) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Actions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        )
        quickProfiles.forEach { profile ->
            ExtendedActionCard(
                icon = Icons.Filled.AutoFixHigh,
                label = profile.name,
                color = MaterialTheme.colorScheme.primary,
                onClick = { if (hasTranscript) onRunProfile(profile.id) else onOpenTransformSheet() },
            )
        }
        if (profiles.size > 3) {
            ExtendedActionCard(
                icon = Icons.Filled.MoreHoriz,
                label = "More transforms…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenTransformSheet,
            )
        }
        ExtendedActionCard(icon = Icons.Filled.FolderOpen, label = "Add to folder", color = MaterialTheme.colorScheme.secondary, onClick = onAddToFolder)
    }
}

@Composable
private fun ExtendedActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    ScrybeSectionCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalysisSuggestionSheet(
    state: AnalysisSuggestionState,
    onAcceptTitle: () -> Unit,
    onAcceptTags: () -> Unit,
    onAcceptMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✨ Analysis", style = MaterialTheme.typography.titleMedium)
        if (state.isLoading) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Analyzing recording…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            state.suggestedTitle?.let { title ->
                SuggestionRow(label = "Title", value = "\"$title\"", onAccept = onAcceptTitle)
            }
            state.suggestedTags.takeIf { it.isNotEmpty() }?.let { tags ->
                SuggestionRow(label = "Tags", value = tags.joinToString(", ") { "#$it" }, onAccept = onAcceptTags)
            }
            state.suggestedMode?.let { mode ->
                SuggestionRow(label = "Type", value = mode.label, onAccept = onAcceptMode)
            }
            if (state.suggestedTitle == null && state.suggestedTags.isEmpty() && state.suggestedMode == null) {
                Text("No suggestions available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}

@Composable
private fun SuggestionRow(
    label: String,
    value: String,
    onAccept: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onAccept) { Text("Use") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineTagsCard(
    tags: List<String>,
    tagSuggestionState: TagSuggestionUiState,
    onSaveTags: (List<String>) -> Unit,
    onSuggestTags: () -> Unit,
    onClear: () -> Unit,
) {
    var localTags by remember(tags) { mutableStateOf(tags) }
    var isExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    ScrybeSectionCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Label, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (isExpanded) {
                    onSaveTags(localTags)
                    isExpanded = false
                    query = ""
                    onClear()
                } else {
                    isExpanded = true
                    onSuggestTags()
                }
            }) { Text(if (isExpanded) "Done" else "Edit") }
        }
        if (localTags.isEmpty() && !isExpanded) {
            Text("No tags yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                localTags.forEach { tag ->
                    TagChipEditable(tag = tag, canRemove = isExpanded, onRemove = { localTags = localTags - tag })
                }
            }
        }
        if (isExpanded) {
            TagSuggestionPanel(
                query = query,
                tagSuggestionState = tagSuggestionState,
                existingTags = localTags,
                onQueryChange = { query = it },
                onAddTag = { t ->
                    val clean = t.trim().lowercase()
                    if (clean.isNotBlank() && !localTags.contains(clean)) localTags = localTags + clean
                    query = ""
                },
            )
        }
    }
}

@Composable
private fun TagChipEditable(
    tag: String,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (canRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove tag", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).clickable(onClick = onRemove))
            }
        }
    }
}

@Composable
private fun TagSuggestionPanel(
    query: String,
    tagSuggestionState: TagSuggestionUiState,
    existingTags: List<String>,
    onQueryChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search or create a tag…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (query.isNotBlank()) onAddTag(query) }),
        )
        if (query.isNotBlank() && !existingTags.contains(query.trim().lowercase())) {
            TextButton(onClick = { onAddTag(query) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create \"#${query.trim()}\"")
            }
        }
        when (tagSuggestionState) {
            is TagSuggestionUiState.Loading ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            is TagSuggestionUiState.Success -> {
                val filtered = tagSuggestionState.tags.filter { !existingTags.contains(it) && (query.isBlank() || it.contains(query, ignoreCase = true)) }
                if (filtered.isNotEmpty()) {
                    Text("AI suggested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    filtered.take(5).forEach { tag ->
                        ListItem(headlineContent = { Text("#$tag") }, leadingContent = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }, modifier = Modifier.clickable { onAddTag(tag) })
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun TranscriptTabContent(
    state: SessionDetailUiState.Success,
    onEditTranscript: (dev.scrybe.core.model.Transcript) -> Unit,
    onDeleteTranscript: (dev.scrybe.core.model.Transcript) -> Unit,
    onResumeTranscription: () -> Unit,
) {
    TranscriptSection(
        state = state,
        onEditTranscript = onEditTranscript,
        onDeleteTranscript = onDeleteTranscript,
        onResumeTranscription = onResumeTranscription,
    )
}

private data class MoreMenuEditCallbacks(
    val onRename: () -> Unit,
    val onManageTags: () -> Unit,
    val onSetArchived: () -> Unit,
    val onDelete: () -> Unit,
    val onTranscribe: () -> Unit,
    val onResetTranscription: () -> Unit,
)

private data class MoreMenuExportCallbacks(
    val onPostProcess: () -> Unit,
    val onShareTranscript: () -> Unit,
    val onExportFiles: () -> Unit,
    val onSendToExternal: () -> Unit,
)

@Composable
private fun MoreMenuSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(label, color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreMenuSheet(
    state: SessionDetailUiState.Success,
    onDismiss: () -> Unit,
    editCallbacks: MoreMenuEditCallbacks,
    exportCallbacks: MoreMenuExportCallbacks,
) {
    val hasTranscript = state.transcripts.isNotEmpty()
    val sessionStatus = state.session.status
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(state.session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.session.mode.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            MoreMenuItem(Icons.Filled.Edit, "Rename", onClick = editCallbacks.onRename)
            MoreMenuItem(Icons.Filled.Label, "Manage Tags", onClick = editCallbacks.onManageTags)
            if (hasTranscript) {
                HorizontalDivider()
                MoreMenuSectionLabel("Re-process")
                MoreMenuItem(Icons.Filled.AutoFixHigh, "Post-process…", onClick = exportCallbacks.onPostProcess)
            }
            HorizontalDivider()
            MoreMenuSectionLabel("Export")
            MoreMenuItem(Icons.Filled.IosShare, "Share Transcript", onClick = exportCallbacks.onShareTranscript)
            if (hasTranscript) MoreMenuItem(Icons.Filled.IosShare, "Send to External App", onClick = exportCallbacks.onSendToExternal)
            MoreMenuItem(Icons.Filled.Description, "Export Files", onClick = exportCallbacks.onExportFiles)
            HorizontalDivider()
            MoreMenuSectionLabel("Manage")
            MoreMenuItem(icon = if (state.session.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive, label = if (state.session.isArchived) "Restore" else "Archive", onClick = editCallbacks.onSetArchived)
            MoreMenuItem(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error, onClick = editCallbacks.onDelete)
            if (sessionStatus == SessionStatus.FAILED || sessionStatus == SessionStatus.PARTIAL_TRANSCRIPTION) {
                HorizontalDivider()
                MoreMenuItem(Icons.Filled.Refresh, if (sessionStatus == SessionStatus.FAILED) "Retry Transcription" else "Resume Transcription", onClick = editCallbacks.onTranscribe)
            }
            if (sessionStatus == SessionStatus.TRANSCRIBING) {
                HorizontalDivider()
                MoreMenuItem(Icons.Filled.Refresh, "Clear Stuck State", onClick = editCallbacks.onResetTranscription)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcosystemSheet(
    onDismiss: () -> Unit,
    onShareTranscript: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text("Send to…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            EcosystemRow(Icons.Filled.CalendarToday, "Calendar", "Add tasks with dates", Color(0xFF4285F4)) {}
            EcosystemRow(Icons.Filled.Notifications, "Reminders", "Create reminders", Color(0xFFFF5252)) {}
            EcosystemRow(Icons.Filled.Article, "Notion", "Export as page", MaterialTheme.colorScheme.onSurface) {}
            EcosystemRow(Icons.Filled.Chat, "Slack", "Post summary to channel", Color(0xFFE01E5A)) {}
            EcosystemRow(Icons.Filled.Email, "Email", "Send via email", MaterialTheme.colorScheme.primary) { onShareTranscript() }
            EcosystemRow(Icons.Filled.Bolt, "Shortcuts", "iOS Shortcuts automations", Color(0xFFFF9800)) {}
            EcosystemRow(Icons.Filled.IosShare, "Share…", "System share sheet", MaterialTheme.colorScheme.primary, isLast = true) { onShareTranscript() }
        }
    }
}

@Composable
private fun EcosystemRow(
    icon: ImageVector,
    label: String,
    sub: String,
    color: Color,
    isLast: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.12f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!isLast) HorizontalDivider(Modifier.padding(start = 52.dp))
    }
}

@Composable
private fun TransformResultDialog(
    profileName: String,
    text: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$profileName result") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(text))
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
                TextButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.IosShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = onSave) {
                    Text("Save to session")
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionMetaStrip(
    state: SessionDetailUiState.Success,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (session.isArchived) {
            MetaChip(
                icon = Icons.Filled.Archive,
                text = "Archived",
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
        MetaChip(
            icon = Icons.Filled.Mic,
            text = session.mode.label,
            tint = MaterialTheme.colorScheme.primary,
        )
        session.locationLabel?.let { loc ->
            MetaChip(icon = Icons.Filled.LocationOn, text = loc)
        }
        session.tags.forEach { tag -> TagPill(tag = tag) }
    }
}

@Composable
private fun MetaChip(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun SessionOverviewCard(state: SessionDetailUiState.Success) {
    ScrybeSectionCard(
        containerColor =
            if (state.session.isArchived) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScrybeSectionHeader(
            title = "Recording details",
            subtitle = "Recorded ${state.session.createdAt.atZone(ZoneId.systemDefault()).format(SUMMARY_TIME_FORMATTER)}",
            trailing =
                if (state.session.isArchived) {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "Archived",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                } else {
                    null
                },
        )
        SessionMetaGrid(state = state)
        state.session.estimatedTranscriptionCostUsd?.let { cost ->
            Text(
                text = "Estimated transcription cost ${formatUsd(cost)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.session.tags.isNotEmpty()) {
            SessionTagsRow(tags = state.session.tags)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionTagsRow(
    tags: List<String>,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            TagPill(tag = tag)
        }
    }
}

@Composable
private fun TagPill(
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SessionMetaGrid(state: SessionDetailUiState.Success) {
    val session = state.session
    val audioQuality =
        "${formatFileSize(session.fileSizeBytes)}  ·  ${session.sampleRateHz / 1000} kHz  ·  " +
            "${session.encodingBitRate / 1000} kbps  ·  ${if (session.channelCount == 1) "Mono" else "Stereo"}"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SessionMetaRow("Duration", formatDuration(session.durationMs))
        SessionMetaRow("Status", SessionStatusPresentation.label(session.status, session.isArchived))
        SessionMetaRow("Format", session.audioFormat.name)
        val locationLabel = session.locationLabel
        if (locationLabel != null) {
            SessionMetaRow("Location", locationLabel)
        }
        Text(
            text = audioQuality,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionMetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TransformSection(
    state: SessionDetailUiState.Success,
    onTransformDefault: () -> Unit,
    onTransformProfile: (String) -> Unit,
    onDismiss: () -> Unit = {},
) {
    if (state.profiles.isEmpty()) {
        return
    }
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScrybeSectionHeader(
            title = "Post-process",
            subtitle = "Run a saved prompt against the latest transcription.",
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onTransformDefault()
                onDismiss()
            },
            enabled = state.transcripts.any { it.type.name == "RAW" } && !state.isTransforming,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isTransforming) "Running default..." else "Run Default Profile")
        }
        state.profiles.forEach { profile ->
            TransformProfileRow(
                profile = profile,
                isDefault = profile.id == state.defaultProfileId || profile.isDefault,
                onRun = {
                    onTransformProfile(profile.id)
                    onDismiss()
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TransformProfileRow(
    profile: TransformProfile,
    isDefault: Boolean,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDefault) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Default profile",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onRun) { Text("Run") }
        }
    }
}

@Composable
private fun TranscriptSection(
    state: SessionDetailUiState.Success,
    onEditTranscript: (Transcript) -> Unit,
    onDeleteTranscript: (Transcript) -> Unit,
    onResumeTranscription: () -> Unit = {},
) {
    val transcripts = state.transcripts
    if (state.currentTranscript == null && transcripts.isEmpty()) {
        ScrybeSectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = "No transcripts yet. You can transcribe this recording from here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    if (state.session.status == SessionStatus.PARTIAL_TRANSCRIPTION) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Partial transcript — some audio segments failed. Tap to resume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onResumeTranscription) {
                    Text("Resume")
                }
            }
        }
    }

    val rawTranscripts = listOfNotNull(state.currentTranscript)
    val originalIfDifferent = state.originalTranscript
        ?.takeIf { original -> state.currentTranscript?.id != original.id }
    val transformedTranscripts =
        transcripts
            .filter { it.type != TranscriptType.RAW }
            .filter { it.type != TranscriptType.EDITED }
            .sortedByDescending { it.createdAt }

    val totalCount = rawTranscripts.size +
        (if (originalIfDifferent != null) 1 else 0) +
        transformedTranscripts.size

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rawTranscripts.isNotEmpty()) {
            rawTranscripts.forEach { transcript ->
                TranscriptCard(
                    transcript = transcript,
                    titleOverride = if (transcript.type == TranscriptType.EDITED) "Edited" else null,
                    speakerSegments = state.speakerSegments,
                    durationMs = state.session.durationMs,
                    startExpanded = totalCount <= 1,
                    onDelete = { onDeleteTranscript(transcript) },
                    onEdit = { onEditTranscript(transcript) },
                )
            }
            originalIfDifferent?.let { original ->
                TranscriptCard(
                    transcript = original,
                    titleOverride = "Original",
                    speakerSegments = emptyList(),
                    durationMs = state.session.durationMs,
                    startExpanded = totalCount <= 1,
                    onDelete = { onDeleteTranscript(original) },
                    onEdit = { onEditTranscript(original) },
                )
            }
        }
        if (transformedTranscripts.isNotEmpty()) {
            Text("Transforms", style = MaterialTheme.typography.labelLarge)
            transformedTranscripts.forEach { transcript ->
                TranscriptCard(
                    transcript = transcript,
                    titleOverride = transcript.type.name,
                    speakerSegments = emptyList(),
                    durationMs = state.session.durationMs,
                    startExpanded = totalCount <= 1,
                    onDelete = { onDeleteTranscript(transcript) },
                    onEdit = { onEditTranscript(transcript) },
                )
            }
        }
    }
}

@Composable
private fun TranscriptCard(transcript: Transcript) {
    TranscriptCard(
        transcript = transcript,
        titleOverride = transcript.type.name,
        speakerSegments = emptyList(),
        durationMs = 0L,
        onDelete = null,
        onEdit = null,
    )
}

@Composable
private fun TranscriptCardActions(
    expanded: Boolean,
    onCopy: () -> Unit,
    onToggleExpand: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy transcript",
            )
        }
        IconButton(onClick = onToggleExpand) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit transcript",
                )
            }
        }
        onDelete?.let {
            IconButton(onClick = it) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete transcript",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    transcript: Transcript,
    titleOverride: String?,
    speakerSegments: List<SpeakerSegment>,
    durationMs: Long,
    startExpanded: Boolean = false,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember(transcript.id) { mutableStateOf(startExpanded) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val formattedText =
        remember(transcript.content, speakerSegments.size, durationMs) {
            buildSpeakerAnnotatedString(transcript.content, speakerSegments, durationMs)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                titleOverride?.let { title ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        transcript.transformProfileId?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } ?: Spacer(modifier = Modifier.weight(1f))
                TranscriptCardActions(
                    expanded = expanded,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(transcript.content))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onToggleExpand = { expanded = !expanded },
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
            Text(
                text = formattedText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildSpeakerAnnotatedString(
    content: String,
    speakerSegments: List<SpeakerSegment>,
    durationMs: Long,
): AnnotatedString {
    val paragraphed = content.replace(Regex("([.?!])\\s+([A-Z])"), "$1\n\n$2")
    if (speakerSegments.isEmpty() || durationMs <= 0L) {
        return AnnotatedString(paragraphed)
    }
    val speakerIds =
        speakerSegments
            .map { it.speakerId }
            .distinct()
            .sorted()
    val len = paragraphed.length
    var lastSpeakerId: String? = null
    return buildAnnotatedString {
        var pos = 0
        for (segment in speakerSegments.sortedBy { it.startMs }) {
            val rawStart = ((segment.startMs.toFloat() / durationMs) * len).toInt().coerceIn(0, len)
            val rawEnd = ((segment.endMs.toFloat() / durationMs) * len).toInt().coerceIn(0, len)
            val segEnd = rawEnd.coerceAtLeast(pos)
            if (segEnd <= pos) continue
            val segStart =
                if (segment.speakerId != lastSpeakerId) {
                    snapToWordBoundary(paragraphed, rawStart).coerceAtLeast(pos)
                } else {
                    rawStart.coerceAtLeast(pos)
                }
            if (segStart > pos) append(paragraphed.substring(pos, segStart))
            if (segment.speakerId != lastSpeakerId) {
                val color = speakerColorForIndex(speakerIds.indexOf(segment.speakerId).coerceAtLeast(0))
                val speakerLabel = defaultSpeakerLabel(segment.speakerId, speakerIds.indexOf(segment.speakerId))
                if (length > 0) append("\n\n")
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                    append("$speakerLabel: ")
                }
                lastSpeakerId = segment.speakerId
            }
            if (segStart < segEnd) append(paragraphed.substring(segStart, segEnd))
            pos = segEnd
        }
        if (pos < len) append(paragraphed.substring(pos))
    }
}

private fun snapToWordBoundary(
    text: String,
    pos: Int,
): Int {
    if (pos <= 0) return 0
    if (pos >= text.length) return text.length
    val limit = (pos - 100).coerceAtLeast(0)
    var i = pos
    while (i > limit && !text[i - 1].isWhitespace()) i--
    return i
}

private fun defaultSpeakerLabel(
    speakerId: String,
    fallbackIndex: Int = 0,
): String {
    val num = speakerId.filter { it.isDigit() }.takeIf { it.isNotBlank() } ?: "${fallbackIndex + 1}"
    return "Speaker $num"
}

@Composable
private fun EditTranscriptDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transcript") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                label = { Text("Transcript") },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSave(value) },
                enabled = value.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    initialTags: List<String>,
    tagSuggestionState: TagSuggestionUiState,
    onDismiss: () -> Unit,
    onSuggest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var tags by remember(initialTags) { mutableStateOf(initialTags.distinct()) }
    var draftTag by remember(initialTags) { mutableStateOf("") }

    fun addDraftTag() {
        val normalizedTag = normalizeEditableTag(draftTag)
        if (normalizedTag.isBlank()) {
            draftTag = ""
            return
        }
        tags = (tags + normalizedTag).distinct()
        draftTag = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            EditableTagPill(
                                tag = tag,
                                onRemove = { tags = tags - tag },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = draftTag,
                    onValueChange = { draftTag = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.Enter -> {
                                        addDraftTag()
                                        true
                                    }

                                    Key.Backspace -> {
                                        if (draftTag.isBlank() && tags.isNotEmpty()) {
                                            tags = tags.dropLast(1)
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    else -> false
                                }
                            },
                    singleLine = true,
                    label = { Text("Add a tag") },
                    supportingText = {
                        Text("Press Enter to add a tag. Press Backspace on an empty field to remove the last tag.")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraftTag() }),
                )
                OutlinedButton(
                    onClick = onSuggest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tagSuggestionState !is TagSuggestionUiState.Loading,
                ) {
                    Text(
                        if (tagSuggestionState is TagSuggestionUiState.Loading) {
                            "Suggesting..."
                        } else {
                            "Suggest with AI"
                        },
                    )
                }
                when (tagSuggestionState) {
                    is TagSuggestionUiState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Suggested tags", style = MaterialTheme.typography.labelLarge)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    tagSuggestionState.tags.forEach { suggestedTag ->
                                        SuggestedTagPill(
                                            tag = suggestedTag,
                                            selected = suggestedTag in tags,
                                            onToggle = {
                                                tags =
                                                    if (suggestedTag in tags) {
                                                        tags - suggestedTag
                                                    } else {
                                                        (tags + suggestedTag).distinct()
                                                    }
                                            },
                                        )
                                    }
                                }
                                Text(
                                    text = "Tap any suggestion to add or remove it from this recording.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    is TagSuggestionUiState.Error ->
                        Text(
                            text = tagSuggestionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    TagSuggestionUiState.Idle,
                    TagSuggestionUiState.Loading,
                    -> Unit
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (draftTag.isNotBlank()) {
                        addDraftTag()
                    }
                    onSave(tags)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditableTagPill(
    tag: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.clickable(onClick = onRemove),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "×",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SuggestedTagPill(
    tag: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        tonalElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Text(
            text = tag,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

private fun normalizeEditableTag(value: String): String =
    value
        .trim()
        .replace(Regex("\\s+"), " ")

@Composable
private fun SpeakerSlotsCard(
    state: SessionDetailUiState.Success,
    onAssignPerson: (speakerId: String, personId: String?) -> Unit,
    onCreatePerson: (speakerId: String, name: String) -> Unit,
    onFetchSpeakerInfo: () -> Unit,
    onMergeSpeakers: (sourceSpeakerId: String, targetSpeakerId: String) -> Unit,
) {
    val distinctSpeakers =
        state.speakerSegments
            .map { it.speakerId }
            .distinct()
            .sorted()
    val speakerEntries =
        distinctSpeakers.mapIndexed { idx, speakerId ->
            val seg = state.speakerSegments.first { it.speakerId == speakerId }
            val label = seg.speakerLabel ?: defaultSpeakerLabel(speakerId, idx)
            val personName = seg.personId?.let { pid -> state.persons.find { it.id == pid }?.name }
            Triple(speakerId, label, personName)
        }
    var pickerTargetSpeakerId by remember { mutableStateOf<String?>(null) }
    var mergeSourceSpeakerId by remember { mutableStateOf<String?>(null) }
    ScrybeSectionCard {
        ScrybeSectionHeader(
            title = "Speakers",
            subtitle =
                if (distinctSpeakers.isEmpty()) {
                    "Retrieve speaker turns from the existing transcript without running a full retranscription."
                } else {
                    null
                },
        )
        if (distinctSpeakers.isEmpty()) {
            OutlinedButton(
                onClick = onFetchSpeakerInfo,
                enabled = !state.isFetchingSpeakerInfo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isFetchingSpeakerInfo) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isFetchingSpeakerInfo) "Retrieving speakers..." else "Retrieve speaker info")
            }
            return@ScrybeSectionCard
        }
        speakerEntries.forEachIndexed { idx, (speakerId, label, personName) ->
            SpeakerSlotRow(
                dotColor = speakerColorForIndex(idx),
                label = label,
                personName = personName,
                showMergeButton = distinctSpeakers.size > 1,
                onAssign = { pickerTargetSpeakerId = speakerId },
                onMerge = { mergeSourceSpeakerId = speakerId },
            )
        }
    }
    pickerTargetSpeakerId?.let { speakerId ->
        PersonPickerDialog(
            persons = state.persons,
            currentPersonId = state.speakerSegments.find { it.speakerId == speakerId }?.personId,
            onDismiss = { pickerTargetSpeakerId = null },
            onAssign = { personId ->
                onAssignPerson(speakerId, personId)
                pickerTargetSpeakerId = null
            },
            onCreateNew = { name ->
                onCreatePerson(speakerId, name)
                pickerTargetSpeakerId = null
            },
        )
    }
    mergeSourceSpeakerId?.let { sourceId ->
        val sourceLabel = speakerEntries.firstOrNull { it.first == sourceId }?.second ?: sourceId
        val others = speakerEntries.filter { it.first != sourceId }.map { it.first to it.second }
        MergeSpeakerDialog(
            sourceLabel = sourceLabel,
            otherSpeakers = others,
            onDismiss = { mergeSourceSpeakerId = null },
            onMerge = { targetId ->
                onMergeSpeakers(sourceId, targetId)
                mergeSourceSpeakerId = null
            },
        )
    }
}

@Composable
private fun SpeakerSlotRow(
    dotColor: Color,
    label: String,
    personName: String?,
    showMergeButton: Boolean,
    onAssign: () -> Unit,
    onMerge: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (personName != null) {
                Text(
                    personName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        TextButton(onClick = onAssign) {
            Text(if (personName != null) "Reassign" else "Assign")
        }
        if (showMergeButton) {
            TextButton(onClick = onMerge) {
                Text("Merge")
            }
        }
    }
}

@Composable
private fun MergeSpeakerDialog(
    sourceLabel: String,
    otherSpeakers: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onMerge: (targetSpeakerId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge Speaker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Combine \"$sourceLabel\" into:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                otherSpeakers.forEach { (targetId, targetLabel) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onMerge(targetId) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(targetLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PersonPickerDialog(
    persons: List<Person>,
    currentPersonId: String?,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    var newPersonName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to Person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                persons.forEach { person ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAssign(person.id) }
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(person.name, modifier = Modifier.weight(1f))
                        if (person.id == currentPersonId) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = { newPersonName = it },
                    label = { Text("New person name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newPersonName.isNotBlank()) onCreateNew(newPersonName) else onDismiss() },
                enabled = newPersonName.isNotBlank() || persons.isNotEmpty(),
            ) {
                Text(if (newPersonName.isNotBlank()) "Create & Assign" else "Cancel")
            }
        },
        dismissButton = {
            if (currentPersonId != null) {
                TextButton(onClick = { onAssign(null) }) { Text("Unassign") }
            }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kib = 1024.0
    val mib = kib * 1024.0
    return when {
        bytes >= mib -> String.format("%.1f MB", bytes / mib)
        bytes >= kib -> String.format("%.1f KB", bytes / kib)
        else -> "$bytes B"
    }
}

private fun formatUsd(amount: Double): String = "$" + String.format("%.2f", amount)

private val SUMMARY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

private val DETAIL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d · h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(
    currentFolderId: String?,
    folders: List<FolderEntity>,
    onPickFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newFolderName by remember { mutableStateOf("") }
    var showNewFolderField by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Move to folder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (currentFolderId != null) {
                ListItem(
                    headlineContent = { Text("Remove from folder") },
                    leadingContent = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    modifier = Modifier.clickable { onPickFolder(null) },
                )
                HorizontalDivider()
            }
            folders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    leadingContent = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    trailingContent = {
                        if (folder.id == currentFolderId) {
                            Icon(Icons.Filled.Star, contentDescription = "Current folder", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable { onPickFolder(folder.id) },
                )
            }
            HorizontalDivider()
            if (showNewFolderField) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { if (newFolderName.isNotBlank()) onCreateFolder(newFolderName) },
                        enabled = newFolderName.isNotBlank(),
                    ) { Text("Create") }
                }
            } else {
                ListItem(
                    headlineContent = { Text("New folder…") },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.clickable { showNewFolderField = true },
                )
            }
        }
    }
}
