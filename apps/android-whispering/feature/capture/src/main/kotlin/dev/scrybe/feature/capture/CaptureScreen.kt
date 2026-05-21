package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.common.ScrybeSectionHeader
import dev.scrybe.core.common.scrybeContentWidth
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.SessionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToSessionDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf<RecordingMode?>(null) }
    var pendingMode by remember { mutableStateOf(RecordingMode.JOURNAL) }

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
            if (audioGranted) viewModel.startRecordingWithMode(pendingMode)
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
                            session.locationLabel?.contains(searchQuery, ignoreCase = true) == true
                    )
            }
        }

    BackHandler(enabled = uiState.phase != CapturePhase.IDLE) {
        // No-op: keep user on screen while recording; foreground service continues.
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scrybe", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search sessions")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, contentDescription = "All recordings")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.phase != CapturePhase.IDLE) {
                RecordingActiveView(
                    state = uiState,
                    paddingValues = paddingValues,
                    onStop = viewModel::stopRecording,
                    onBack = {},
                    onCancel = viewModel::cancelRecording,
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = ScrybeLayoutDefaults.screenHorizontalPadding,
                            top = paddingValues.calculateTopPadding() + 4.dp,
                            end = ScrybeLayoutDefaults.screenHorizontalPadding,
                            bottom = paddingValues.calculateBottomPadding() + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (searchOpen) {
                        item(key = "search") {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search sessions…") },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .scrybeContentWidth(),
                                singleLine = true,
                            )
                        }
                    }
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
                    items(filteredSessions, key = { it.id }) { session ->
                        HomeSessionCard(
                            session = session,
                            onClick = { onNavigateToSessionDetail(session.id) },
                        )
                    }
                    item(key = "bottom-spacer") { Spacer(Modifier.height(80.dp)) }
                }
                FloatingActionButton(
                    onClick = viewModel::showModePicker,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Start recording")
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
                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                }
            },
            onDismiss = viewModel::dismissModePicker,
        )
    }
}

@Composable
private fun RecordingActiveView(
    state: CaptureUiState,
    paddingValues: PaddingValues,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
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
        Spacer(Modifier.weight(1f))
        RecordingStopButtons(
            modeName = state.activeMode.label,
            enabled = !isStopping,
            onStop = onStop,
            onCancel = onCancel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        ModeBadge(mode = state.activeMode)
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
            onClick = onStop,
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
            val color =
                if (isRecent) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + gradientRatio * 0.47f)
                }
            val heightFraction = (0.08f + amplitude * 0.92f).coerceIn(0.08f, 1f)
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(heightFraction)
                        .background(color, RoundedCornerShape(2.dp)),
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
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember { mutableStateOf(RecordingMode.JOURNAL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
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
            RecordingMode.entries.chunked(2).forEach { row ->
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
            Button(
                onClick = { onStartRecording(selectedMode) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Start recording")
            }
        }
    }
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
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Canvas(modifier = modifier.height(28.dp)) {
        if (samples.isEmpty()) return@Canvas
        val targetCount = 40
        val step = samples.size.toFloat() / targetCount
        val barWidth = (size.width / (targetCount * 2.2f)).coerceAtLeast(1f)
        val gap = size.width / targetCount - barWidth
        repeat(targetCount) { i ->
            val srcIdx = (i * step).toInt().coerceIn(0, samples.lastIndex)
            val amp = samples[srcIdx].coerceIn(0f, 1f)
            val barHeight = (4f + amp * (size.height - 4f)).coerceAtLeast(4f)
            val x = i * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = barColor,
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

@Composable
private fun ModeBadge(mode: RecordingMode) {
    val accentColor = modeAccentColor(mode)
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = modeIcon(mode),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeSessionCard(
    session: RecentCaptureSession,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .scrybeContentWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeSessionCardHeader(session)
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

@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PersonSearch,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${session.speakerCount} speakers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasOpenTasks) {
            SessionTasksChip(count = session.openTaskCount)
        }
        session.tags.take(3).forEach { tag ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = "#$tag",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    ScrybeSectionCard(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScrybeSectionHeader(
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
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
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
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "$openTaskTotal open task${if (openTaskTotal == 1) "" else "s"} across your sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun modeAccentColor(mode: RecordingMode): Color =
    when (mode) {
        RecordingMode.MEETING -> Color(0xFF89C7FF)
        RecordingMode.IDEA -> Color(0xFFFFD580)
        RecordingMode.TASKS -> Color(0xFF88D7A8)
        RecordingMode.CONVERSATION -> Color(0xFFC4ABFF)
        RecordingMode.STORY -> Color(0xFFFF9EC4)
        RecordingMode.INTERVIEW -> Color(0xFFFFB695)
        RecordingMode.JOURNAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun modeIcon(mode: RecordingMode): ImageVector =
    when (mode) {
        RecordingMode.MEETING -> Icons.Filled.Groups
        RecordingMode.IDEA -> Icons.Filled.Lightbulb
        RecordingMode.TASKS -> Icons.Filled.TaskAlt
        RecordingMode.CONVERSATION -> Icons.Filled.Forum
        RecordingMode.STORY -> Icons.Filled.MenuBook
        RecordingMode.INTERVIEW -> Icons.Filled.PersonSearch
        RecordingMode.JOURNAL -> Icons.Filled.Book
    }
