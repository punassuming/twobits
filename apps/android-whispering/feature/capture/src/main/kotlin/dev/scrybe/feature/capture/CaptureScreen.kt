package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scrybe.core.common.ScrybeLayoutDefaults
import dev.scrybe.core.common.ScrybeSectionCard
import dev.scrybe.core.common.ScrybeSectionHeader
import dev.scrybe.core.common.scrybeContentWidth
import dev.scrybe.core.model.SessionStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val LIVE_WAVEFORM_TARGET_BAR_COUNT = 72
private val LIVE_WAVEFORM_MAX_BAR_WIDTH = 2.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToSessionDetail: (String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
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
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
            if (audioGranted) {
                viewModel.startRecording()
            }
        }

    DisposableEffect(view, uiState.phase, uiState.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = uiState.keepScreenOn && uiState.phase == CapturePhase.RECORDING
        onDispose {
            view.keepScreenOn = previous
        }
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Scrybe", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Record, review, and revisit sessions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = ScrybeLayoutDefaults.screenHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    end = ScrybeLayoutDefaults.screenHorizontalPadding,
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(ScrybeLayoutDefaults.screenVerticalSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                HomeCard {
                    CaptureHeroHeader(
                        phase = uiState.phase,
                        hasRequiredPermissions = hasRequiredPermissions,
                        elapsedMs = uiState.elapsedMs,
                    )
                    AmplitudeVisualizer(
                        amplitudes = uiState.amplitudeHistory,
                        currentAmplitudeRatio = uiState.currentAmplitudeRatio,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                    )
                    RecordActionButton(
                        onClick = {
                            when (uiState.phase) {
                                CapturePhase.IDLE -> {
                                    if (hasRequiredPermissions) {
                                        viewModel.startRecording()
                                    } else {
                                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                                    }
                                }

                                CapturePhase.RECORDING -> viewModel.stopRecording()
                                CapturePhase.STOPPING -> Unit
                            }
                        },
                        ringColor =
                            if (uiState.phase == CapturePhase.RECORDING) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        centerColor =
                            if (uiState.phase == CapturePhase.RECORDING) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        isActive = uiState.phase == CapturePhase.RECORDING,
                        amplitudeRatio = uiState.currentAmplitudeRatio,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
            }
            if (uiState.phase == CapturePhase.IDLE && uiState.recentSessions.isEmpty()) {
                item {
                    IntroGuidanceSection(hasRecentSessions = false)
                }
            }
            if (uiState.phase != CapturePhase.RECORDING) {
                item {
                    RecentRecordingsSection(
                        sessions = uiState.recentSessions.take(3),
                        onOpenSession = onNavigateToSessionDetail,
                    )
                }
            }
            uiState.errorMessage?.let { message ->
                item {
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
        }
    }
}

@Composable
private fun RecentRecordingsSection(
    sessions: List<RecentCaptureSession>,
    onOpenSession: (String) -> Unit,
) {
    HomeCard {
        ScrybeSectionHeader(
            title = "Recent recordings",
            subtitle = "Jump back into the latest saved sessions.",
        )
        if (sessions.isEmpty()) {
            Text(
                text = "No recordings yet. Start one above and it will appear here as soon as it is saved.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        } else {
            sessions.forEach { session ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
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
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            SessionMetaPill(session = session)
                        }
                        Text(
                            text = session.createdAtLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (session.speakerCount > 1) {
                            Text(
                                text = "${session.speakerCount} speakers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        session.transcriptPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureHeroHeader(
    phase: CapturePhase,
    hasRequiredPermissions: Boolean,
    elapsedMs: Long,
) {
    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120)) },
        label = "capture-phase",
    ) { currentPhase ->
        Text(
            text =
                when (currentPhase) {
                    CapturePhase.IDLE ->
                        if (hasRequiredPermissions) "Ready to record" else "Microphone permission required"
                    CapturePhase.RECORDING -> "Recording ${formatElapsedTime(elapsedMs)}"
                    CapturePhase.STOPPING -> "Saving recording…"
                },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun IntroGuidanceSection(hasRecentSessions: Boolean) {
    HomeCard {
        ScrybeSectionHeader(
            title = if (hasRecentSessions) "Make each recording easier to review" else "Start with a confident first recording",
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
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    ScrybeSectionCard(
        modifier = Modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun TopBarAction(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun RecordActionButton(
    onClick: () -> Unit,
    ringColor: Color,
    centerColor: Color,
    isActive: Boolean = false,
    amplitudeRatio: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val burstScale = remember { Animatable(0f) }
    val burstAlpha = remember { Animatable(0f) }
    val centerBounce = remember { Animatable(1f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            burstScale.snapTo(1f)
            burstAlpha.snapTo(0.4f)
            coroutineScope {
                launch {
                    burstScale.animateTo(2.2f, animationSpec = tween(durationMillis = 500))
                }
                launch {
                    burstAlpha.animateTo(0f, animationSpec = tween(durationMillis = 500))
                }
            }
            centerBounce.snapTo(0.85f)
            centerBounce.animateTo(1f, animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
        } else {
            centerBounce.snapTo(1.1f)
            centerBounce.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "record-action")
    val audioReactiveScale by animateFloatAsState(
        targetValue = 1f + (amplitudeRatio.coerceIn(0f, 1f) * if (isActive) 0.12f else 0.03f),
        animationSpec = tween(durationMillis = 140),
        label = "record-audio-reactive-scale",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.1f else 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = if (isActive) 1180 else 1680,
                        easing = LinearEasing,
                    ),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "record-action-scale",
    )
    val outerRingScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = if (isActive) 1.03f else 1.02f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = if (isActive) 920 else 1720, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "record-outer-ring",
    )
    val middleRingScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = if (isActive) 1.02f else 1.015f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = if (isActive) 1080 else 1460, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "record-middle-ring",
    )
    val innerRingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.015f else 1.01f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = if (isActive) 820 else 1240, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "record-inner-ring",
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2f).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = if (isActive) 1500 else 2600, easing = LinearEasing),
            ),
        label = "record-wave-phase",
    )
    val waveStrength =
        when {
            amplitudeRatio > 0f -> 0.004f + (amplitudeRatio.coerceIn(0f, 1f) * 0.02f)
            isActive -> 0.008f
            else -> 0f
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .scrybeContentWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(178.dp)
                    .graphicsLayer {
                        val combinedScale = pulseScale * audioReactiveScale
                        scaleX = combinedScale
                        scaleY = combinedScale
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (burstAlpha.value > 0f) {
                Canvas(
                    modifier =
                        Modifier
                            .size(172.dp)
                            .graphicsLayer {
                                scaleX = burstScale.value
                                scaleY = burstScale.value
                                alpha = burstAlpha.value
                            },
                ) {
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
            }
            RingLayer(
                diameter = 172.dp,
                color = ringColor.copy(alpha = 0.10f),
                scale = outerRingScale,
                wavePhase = wavePhase,
                waveStrength = waveStrength * 0.75f,
            )
            RingLayer(
                diameter = 148.dp,
                color = ringColor.copy(alpha = 0.16f),
                scale = middleRingScale,
                wavePhase = wavePhase + 0.8f,
                waveStrength = waveStrength,
            )
            RingLayer(
                diameter = 126.dp,
                color = ringColor.copy(alpha = 0.22f),
                scale = innerRingScale,
                wavePhase = wavePhase + 1.6f,
                waveStrength = waveStrength * 1.15f,
            )
            Button(
                onClick = onClick,
                modifier =
                    Modifier
                        .size(98.dp)
                        .graphicsLayer {
                            scaleX = centerBounce.value
                            scaleY = centerBounce.value
                        },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = centerColor),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun RingLayer(
    diameter: Dp,
    color: Color,
    scale: Float,
    wavePhase: Float,
    waveStrength: Float,
) {
    Canvas(
        modifier =
            Modifier
                .size(diameter)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = (this.size.minDimension / 2f) - strokeWidth
        drawCircle(
            color = color,
            radius = baseRadius,
            center = center,
        )
        val path = Path()
        val steps = 120
        repeat(steps + 1) { index ->
            val angle = (index.toFloat() / steps.toFloat()) * ((PI * 2.0).toFloat())
            val perturbation =
                if (waveStrength > 0f) {
                    sin((angle * 6f) + wavePhase).toFloat() * baseRadius * waveStrength
                } else {
                    0f
                }
            val radius = baseRadius + perturbation
            val point =
                Offset(
                    x = center.x + (cos(angle).toFloat() * radius),
                    y = center.y + (sin(angle).toFloat() * radius),
                )
            if (index == 0) {
                path.moveTo(point.x, point.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        path.close()
        drawPath(
            path = path,
            color = color.copy(alpha = 0.45f),
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun AmplitudeVisualizer(
    amplitudes: List<Float>,
    currentAmplitudeRatio: Float,
    modifier: Modifier = Modifier,
) {
    val displayValues = normalizeAmplitudeValues(amplitudes, targetCount = LIVE_WAVEFORM_TARGET_BAR_COUNT)
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accentColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    Canvas(modifier = modifier) {
        val centerY = size.height * 0.5f
        drawLine(
            color = baselineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val barWidth =
            (size.width / max(displayValues.size * 3.6f, 1f))
                .coerceAtLeast(1.dp.toPx())
                .coerceAtMost(LIVE_WAVEFORM_MAX_BAR_WIDTH.toPx())
        val spacing = (size.width - (barWidth * displayValues.size)) / displayValues.size.coerceAtLeast(1)
        val halfHeight = size.height * 0.38f
        val activeIndex = displayValues.lastIndex
        displayValues.forEachIndexed { index, amplitude ->
            val shapedAmplitude = visualAmplitude(amplitude)
            val lineHeight = halfHeight * shapedAmplitude
            val x = (index * (barWidth + spacing)) + barWidth / 2
            val color =
                when {
                    index == activeIndex -> accentColor.copy(alpha = 0.65f + (currentAmplitudeRatio * 0.35f))
                    index >= displayValues.lastIndex - 4 -> barColor.copy(alpha = 0.78f)
                    else -> barColor.copy(alpha = 0.34f + (index.toFloat() / displayValues.size) * 0.32f)
                }
            drawLine(
                color = color,
                start = Offset(x, centerY - lineHeight),
                end = Offset(x, centerY + lineHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SessionMetaPill(session: RecentCaptureSession) {
    val presentation = recentSessionModePresentation(session)
    Surface(
        color = presentation.containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = presentation.contentDescription,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            tint = presentation.tint,
        )
    }
}

@Composable
private fun recentSessionModePresentation(session: RecentCaptureSession): RecentSessionModePresentation {
    val colorScheme = MaterialTheme.colorScheme
    return when {
        session.isArchived ->
            RecentSessionModePresentation(
                icon = Icons.Filled.Archive,
                contentDescription = "Archived recording",
                tint = colorScheme.onTertiaryContainer,
                containerColor = colorScheme.tertiaryContainer,
            )
        session.status == SessionStatus.TRANSCRIBING ->
            RecentSessionModePresentation(
                icon = Icons.Filled.HourglassEmpty,
                contentDescription = "Transcription in progress",
                tint = colorScheme.onSecondaryContainer,
                containerColor = colorScheme.secondaryContainer,
            )
        session.status == SessionStatus.TRANSCRIBED ->
            RecentSessionModePresentation(
                icon = Icons.Filled.Description,
                contentDescription = "Transcribed recording",
                tint = colorScheme.onPrimaryContainer,
                containerColor = colorScheme.primaryContainer,
            )
        session.status == SessionStatus.EDITED ->
            RecentSessionModePresentation(
                icon = Icons.Filled.Edit,
                contentDescription = "Edited transcript",
                tint = colorScheme.onPrimaryContainer,
                containerColor = colorScheme.primaryContainer,
            )
        session.status == SessionStatus.FAILED ->
            RecentSessionModePresentation(
                icon = Icons.Filled.Error,
                contentDescription = "Transcription failed",
                tint = colorScheme.onErrorContainer,
                containerColor = colorScheme.errorContainer,
            )
        else ->
            RecentSessionModePresentation(
                icon = Icons.Filled.CheckCircle,
                contentDescription = "Saved recording",
                tint = colorScheme.onSecondaryContainer,
                containerColor = colorScheme.secondaryContainer,
            )
    }
}

private data class RecentSessionModePresentation(
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color,
    val containerColor: Color,
)

private fun visualAmplitude(value: Float): Float {
    val gated = if (value < 0.02f) 0f else value
    return (gated * 0.9f).coerceIn(0f, 1f)
}

private fun normalizeAmplitudeValues(
    values: List<Float>,
    targetCount: Int,
): List<Float> {
    if (targetCount <= 0) return emptyList()
    if (values.isEmpty()) return List(targetCount) { 0f }
    if (values.size == 1) return List(targetCount) { values.first() }

    return List(targetCount) { index ->
        val position = (index.toFloat() / (targetCount - 1).coerceAtLeast(1)) * values.lastIndex
        val lowerIndex = position.toInt().coerceIn(0, values.lastIndex)
        val upperIndex = (lowerIndex + 1).coerceAtMost(values.lastIndex)
        val fraction = position - lowerIndex
        val lowerValue = values[lowerIndex]
        val upperValue = values[upperIndex]
        lowerValue + ((upperValue - lowerValue) * fraction)
    }
}

private fun formatElapsedTime(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
