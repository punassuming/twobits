package dev.scrybe.feature.sessiondetail

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
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
    var deleteTranscriptTarget by remember { mutableStateOf<Transcript?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionDetailEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is SessionDetailEvent.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share transcript"))
                }
                is SessionDetailEvent.ShareFile -> {
                    val file = File(event.path)
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share audio"))
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
                    Text(
                        text = successState?.session?.title ?: "Session Review",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                                DropdownMenuItem(
                                    text = { Text("Share audio file") },
                                    leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.shareAudioFile()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share transcript") },
                                    enabled = successState.transcripts.isNotEmpty(),
                                    leadingIcon = { Icon(Icons.Filled.IosShare, contentDescription = null) },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.shareLatestTranscript()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export files") },
                                    leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.exportAll()
                                    },
                                )
                                if (successState.isTranscribing) {
                                    DropdownMenuItem(
                                        text = { Text("Clear Stuck State") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            viewModel.resetTranscriptionState()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (successState.session.isArchived) "Restore" else "Archive") },
                                    leadingIcon = {
                                        Icon(
                                            if (successState.session.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        viewModel.setArchived(!successState.session.isArchived)
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
            is SessionDetailUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is SessionDetailUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }

            is SessionDetailUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SessionOverviewCard(state)
                    PlaybackCard(
                        state = state,
                        onSeek = viewModel::seekPlayback,
                        onTogglePlayback = viewModel::togglePlayback,
                        onStopPlayback = viewModel::stopPlayback,
                    )
                    TranscriptSection(
                        state = state,
                        onEditTranscript = { isEditingTranscript = true },
                        onDeleteTranscript = { deleteTranscriptTarget = it },
                    )
                    TransformSection(
                        state = state,
                        onTransformDefault = viewModel::transformDefaultProfile,
                        onTransformProfile = viewModel::transform,
                    )
                }
            }
        }
    }

    val successState = uiState as? SessionDetailUiState.Success
    if (successState?.shouldPromptForRename == true) {
        RenamePromptDialog(
            initialTitle = successState.session.title,
            onDismiss = viewModel::dismissRenamePrompt,
            onConfirm = viewModel::renameSession,
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

    deleteTranscriptTarget?.let { transcript ->
        AlertDialog(
            onDismissRequest = { deleteTranscriptTarget = null },
            title = {
                Text(
                    when (transcript.type) {
                        TranscriptType.TRANSFORMED -> "Delete Transformation"
                        TranscriptType.EDITED -> "Delete Edited Transcript"
                        TranscriptType.RAW -> "Delete Transcript"
                    }
                )
            },
            text = {
                Text(
                    when (transcript.type) {
                        TranscriptType.TRANSFORMED -> "Delete this generated transformation output?"
                        TranscriptType.EDITED -> "Delete this edited transcript and fall back to the machine transcript?"
                        TranscriptType.RAW -> "Delete the transcription for this recording?"
                    }
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
}

@Composable
private fun SessionOverviewCard(state: SessionDetailUiState.Success) {
    val audioFile = File(state.session.audioFilePath)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.session.isArchived) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.session.isArchived) {
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
            Text(
                text = "Recorded ${state.session.createdAt.atZone(ZoneId.systemDefault()).format(SUMMARY_TIME_FORMATTER)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactMetaItem("Duration", formatDuration(state.session.durationMs))
                CompactMetaItem("Status", state.session.status.name)
                CompactMetaItem("Audio", state.session.audioFormat.name)
            }
            Text(
                text = "${audioFile.name.ifBlank { state.session.audioFilePath }} · ${formatFileSize(state.session.fileSizeBytes)} · ${state.session.sampleRateHz / 1000} kHz · ${state.session.encodingBitRate / 1000} kbps · ${if (state.session.channelCount == 1) "Mono" else "Stereo"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            state.session.estimatedTranscriptionCostUsd?.let { cost ->
                Text(
                    text = "Estimated transcription cost ${formatUsd(cost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RowScope.CompactMetaItem(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransformSection(
    state: SessionDetailUiState.Success,
    onTransformDefault: () -> Unit,
    onTransformProfile: (String) -> Unit,
) {
    if (state.profiles.isEmpty()) {
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Post-process", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Run a saved prompt against the latest transcription.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onTransformDefault,
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
                    onRun = { onTransformProfile(profile.id) },
                )
            }
        }
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
            modifier = Modifier
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
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isDefault) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = "No transcripts yet. You can transcribe this recording from here.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val rawTranscripts = listOfNotNull(state.currentTranscript)
    val transformedTranscripts = transcripts
        .filter { it.type != TranscriptType.RAW }
        .filter { it.type != TranscriptType.EDITED }
        .sortedByDescending { it.createdAt }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rawTranscripts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Transcription", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = onEditTranscript, enabled = state.currentTranscript != null) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
            }
            rawTranscripts.forEach { transcript ->
                TranscriptCard(
                    transcript = transcript,
                    titleOverride = if (transcript.type == TranscriptType.EDITED) "Edited transcript" else "Transcript",
                    onDelete = { onDeleteTranscript(transcript) },
                )
            }
            state.originalTranscript
                ?.takeIf { original -> state.currentTranscript?.id != original.id }
                ?.let { original ->
                    TranscriptCard(
                        transcript = original,
                        titleOverride = "Original machine transcript",
                        onDelete = { onDeleteTranscript(original) },
                    )
                }
        }
        if (transformedTranscripts.isNotEmpty()) {
            Text("Transformations", style = MaterialTheme.typography.labelLarge)
            transformedTranscripts.forEach { transcript ->
                TranscriptCard(
                    transcript = transcript,
                    titleOverride = transcript.type.name,
                    onDelete = { onDeleteTranscript(transcript) },
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
        onDelete = null,
    )
}

@Composable
private fun TranscriptCard(
    transcript: Transcript,
    titleOverride: String,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember(transcript.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
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
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = titleOverride,
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
            Text(
                text = transcript.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (expanded) "Tap to collapse" else "Tap to expand",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
