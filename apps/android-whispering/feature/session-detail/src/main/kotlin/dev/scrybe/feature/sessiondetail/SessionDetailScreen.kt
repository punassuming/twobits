package dev.scrybe.feature.sessiondetail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import dev.scrybe.core.model.Person
import dev.scrybe.core.model.SessionStatus
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var isEditingTranscript by remember { mutableStateOf(false) }
    var isEditingTags by remember { mutableStateOf(false) }
    var deleteTranscriptTarget by remember { mutableStateOf<Transcript?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var transformResult by remember { mutableStateOf<SessionDetailEvent.TransformResult?>(null) }
    var showTransformSheet by remember { mutableStateOf(false) }
    val transformSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                            Text(
                                text = titleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        Box {
                            IconButton(onClick = { actionMenuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = actionMenuExpanded,
                                onDismissRequest = { actionMenuExpanded = false },
                            ) {
                                val hasTranscript = successState.transcripts.isNotEmpty()
                                if (hasTranscript) {
                                    HorizontalDivider()
                                    Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Post-process…") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                                        },
                                        onClick = {
                                            actionMenuExpanded = false
                                            showTransformSheet = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("AI Rename") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                        },
                                        onClick = {
                                            actionMenuExpanded = false
                                            viewModel.aiRename()
                                        },
                                    )
                                }
                                HorizontalDivider()
                                Text(
                                    text = "Export",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                                if (hasTranscript) {
                                    DropdownMenuItem(
                                        text = { Text("Share Transcript") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.IosShare, contentDescription = null)
                                        },
                                        onClick = {
                                            actionMenuExpanded = false
                                            viewModel.shareLatestTranscript()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Export Files") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Description, contentDescription = null)
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.exportAll()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Send to External App") },
                                    enabled = hasTranscript,
                                    leadingIcon = {
                                        Icon(Icons.Filled.IosShare, contentDescription = null)
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.sendToTaskForge()
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
                                    text = { Text("Rename") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        showRenameDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage Tags") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Label, contentDescription = null)
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.clearTagSuggestionState()
                                        isEditingTags = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (successState.session.isArchived) "Restore" else "Archive")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (successState.session.isArchived) {
                                                Icons.Filled.Unarchive
                                            } else {
                                                Icons.Filled.Archive
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.setArchived(!successState.session.isArchived)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        showDeleteConfirm = true
                                    },
                                )
                                val sessionStatus = successState.session.status
                                val hasStatusItems =
                                    sessionStatus == SessionStatus.FAILED ||
                                        sessionStatus == SessionStatus.TRANSCRIBING
                                if (hasStatusItems) {
                                    HorizontalDivider()
                                    Text(
                                        text = "Status",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                    if (sessionStatus == SessionStatus.FAILED) {
                                        DropdownMenuItem(
                                            text = { Text("Retry Transcription") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                            },
                                            onClick = {
                                                actionMenuExpanded = false
                                                viewModel.transcribe()
                                            },
                                        )
                                    }
                                    if (sessionStatus == SessionStatus.TRANSCRIBING) {
                                        DropdownMenuItem(
                                            text = { Text("Clear Stuck State") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                            },
                                            onClick = {
                                                actionMenuExpanded = false
                                                viewModel.resetTranscriptionState()
                                            },
                                        )
                                    }
                                }
                            }
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
                        SessionOverviewCard(state)
                        PlaybackCard(
                            state = state,
                            onTogglePlayback = viewModel::togglePlayback,
                            onStopPlayback = viewModel::stopPlayback,
                            onSeek = viewModel::seekPlayback,
                        )
                        if (state.speakerSegments.isNotEmpty()) {
                            SpeakerSlotsCard(
                                state = state,
                                onAssignPerson = viewModel::assignPersonToSpeaker,
                                onCreatePerson = viewModel::createPersonAndAssign,
                            )
                        }
                        TranscriptSection(
                            state = state,
                            onEditTranscript = { isEditingTranscript = true },
                            onDeleteTranscript = { deleteTranscriptTarget = it },
                        )
                        if (state.profiles.isNotEmpty() && state.currentTranscript != null) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showTransformSheet = true },
                            ) {
                                Icon(
                                    Icons.Filled.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Post-process transcript")
                            }
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

    if (successState?.shouldPromptForRename == true) {
        RenamePromptDialog(
            initialTitle = successState.session.title,
            onDismiss = viewModel::dismissRenamePrompt,
            onConfirm = viewModel::renameSession,
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

    if (isEditingTranscript && successState != null) {
        EditTranscriptDialog(
            initialValue = successState.currentTranscript?.content.orEmpty(),
            onDismiss = { isEditingTranscript = false },
            onSave = {
                viewModel.saveTranscriptEdit(it)
                isEditingTranscript = false
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
            onSave = { tagsInput ->
                viewModel.saveTags(tagsInput)
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

@Composable
private fun SessionTagsRow(
    tags: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = tags.joinToString("  •  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    onEditTranscript: () -> Unit,
    onDeleteTranscript: (Transcript) -> Unit,
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

    val rawTranscripts = listOfNotNull(state.currentTranscript)
    val transformedTranscripts =
        transcripts
            .filter { it.type != TranscriptType.RAW }
            .filter { it.type != TranscriptType.EDITED }
            .sortedByDescending { it.createdAt }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rawTranscripts.isNotEmpty()) {
            rawTranscripts.forEach { transcript ->
                TranscriptCard(
                    transcript = transcript,
                    titleOverride = if (transcript.type == TranscriptType.EDITED) "Edited" else null,
                    speakerSegments = state.speakerSegments,
                    durationMs = state.session.durationMs,
                    onDelete = { onDeleteTranscript(transcript) },
                    onEdit = onEditTranscript,
                )
            }
            state.originalTranscript
                ?.takeIf { original -> state.currentTranscript?.id != original.id }
                ?.let { original ->
                    TranscriptCard(
                        transcript = original,
                        titleOverride = "Original",
                        speakerSegments = emptyList(),
                        durationMs = state.session.durationMs,
                        onDelete = { onDeleteTranscript(original) },
                        onEdit = onEditTranscript,
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
                    onDelete = { onDeleteTranscript(transcript) },
                    onEdit = onEditTranscript,
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
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember(transcript.id) { mutableStateOf(false) }
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
    val len = paragraphed.length
    return buildAnnotatedString {
        var pos = 0
        for (segment in speakerSegments.sortedBy { it.startMs }) {
            val start = ((segment.startMs.toFloat() / durationMs) * len).toInt().coerceIn(0, len)
            val end = ((segment.endMs.toFloat() / durationMs) * len).toInt().coerceIn(0, len)
            if (start > pos) {
                append(paragraphed.substring(pos, start))
            }
            if (start < end) {
                val speakerIndex =
                    segment.speakerId
                        .filter { it.isDigit() }
                        .toIntOrNull()
                        ?.minus(1) ?: 0
                val color = speakerColorPalette[speakerIndex % speakerColorPalette.size]
                withStyle(SpanStyle(color = color)) {
                    append(paragraphed.substring(start, end))
                }
                pos = end
            }
        }
        if (pos < len) append(paragraphed.substring(pos))
    }
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

@Composable
private fun TagEditorDialog(
    initialTags: List<String>,
    tagSuggestionState: TagSuggestionUiState,
    onDismiss: () -> Unit,
    onSuggest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialTags) { mutableStateOf(initialTags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Tags") },
                    supportingText = { Text("Use commas or new lines. Tags are searchable from Records.") },
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
                                Text(
                                    text = tagSuggestionState.tags.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { value = tagSuggestionState.tags.joinToString(", ") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Use suggestions")
                                }
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
            androidx.compose.material3.TextButton(onClick = { onSave(value) }) {
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
private fun SpeakerSlotsCard(
    state: SessionDetailUiState.Success,
    onAssignPerson: (speakerId: String, personId: String?) -> Unit,
    onCreatePerson: (speakerId: String, name: String) -> Unit,
) {
    val distinctSpeakers =
        state.speakerSegments
            .map { it.speakerId }
            .distinct()
            .sorted()
    if (distinctSpeakers.isEmpty()) return
    var pickerTargetSpeakerId by remember { mutableStateOf<String?>(null) }
    ScrybeSectionCard {
        ScrybeSectionHeader(title = "Speakers")
        distinctSpeakers.forEachIndexed { idx, speakerId ->
            val segment = state.speakerSegments.first { it.speakerId == speakerId }
            val label =
                segment.speakerLabel ?: run {
                    val n = speakerId.filter { it.isDigit() }.takeIf { it.isNotBlank() } ?: "${idx + 1}"
                    "Speaker $n"
                }
            val personName =
                segment.personId?.let { pid -> state.persons.find { it.id == pid }?.name }
            val dotColor = speakerColorPalette[idx % speakerColorPalette.size]
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
                TextButton(onClick = { pickerTargetSpeakerId = speakerId }) {
                    Text(if (personName != null) "Reassign" else "Assign")
                }
            }
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
