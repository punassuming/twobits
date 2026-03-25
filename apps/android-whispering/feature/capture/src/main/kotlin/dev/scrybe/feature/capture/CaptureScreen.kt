package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CaptureScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSessionDetail: (String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val hasRequiredPermissions = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (requiredPermissions.all { results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top),
    ) {
        Text("Scrybe", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Capture audio, review the recording, transcribe it with OpenAI, and turn it into polished notes or action items.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        HomeCard {
            AnimatedContent(
                targetState = uiState.phase,
                transitionSpec = { fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120)) },
                label = "capture-phase",
            ) { phase ->
                when (phase) {
                    CapturePhase.IDLE -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!hasRequiredPermissions) {
                                Text(
                                    text = "Microphone permission is required before you can record.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                            RecordActionButton(
                                onClick = {
                                    if (hasRequiredPermissions) {
                                        viewModel.startRecording()
                                    } else {
                                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                                    }
                                },
                                label = "Start Recording",
                                ringColor = MaterialTheme.colorScheme.primary,
                                centerColor = MaterialTheme.colorScheme.error,
                                supportingText = "Fast capture with background recording, transcription, and reusable AI transforms.",
                            )
                        }
                    }
                    CapturePhase.RECORDING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Recording\u2026 ${formatElapsedTime(uiState.elapsedMs)}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = "The waveform below should react to your voice while audio is being captured.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            AmplitudeVisualizer(
                                amplitudes = uiState.amplitudeHistory,
                                currentAmplitudeRatio = uiState.currentAmplitudeRatio,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            RecordActionButton(
                                onClick = { viewModel.stopRecording() },
                                label = "Stop Recording",
                                ringColor = MaterialTheme.colorScheme.tertiary,
                                centerColor = MaterialTheme.colorScheme.primary,
                                isActive = true,
                                amplitudeRatio = uiState.currentAmplitudeRatio,
                                supportingText = "Recording stays active if you leave this screen. You can stop from the notification or come back here.",
                            )
                        }
                    }
                    CapturePhase.STOPPING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = "Stopping\u2026", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "We’re saving the recording and opening the review screen next.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        QuickActionRow(
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToProfiles = onNavigateToProfiles,
            onNavigateToSettings = onNavigateToSettings,
        )
        RecentRecordingsSection(
            sessions = uiState.recentSessions,
            onOpenSession = onNavigateToSessionDetail,
        )
        uiState.errorMessage?.let { message ->
            Card(
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

@Composable
private fun RecentRecordingsSection(
    sessions: List<RecentCaptureSession>,
    onOpenSession: (String) -> Unit,
) {
    HomeCard {
        Text(
            text = "Recent Records",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(session.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            )
                            Text(
                                text = session.status.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = session.createdAtLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun QuickActionRow(
    onNavigateToHistory: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard(
            onClick = onNavigateToHistory,
            modifier = Modifier.weight(1f),
            icon = {
                Icon(Icons.Filled.History, contentDescription = null)
            },
            label = "Records",
        )
        QuickActionCard(
            onClick = onNavigateToProfiles,
            modifier = Modifier.weight(1f),
            icon = {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            },
            label = "Profiles",
        )
        QuickActionCard(
            onClick = onNavigateToSettings,
            modifier = Modifier.weight(1f),
            icon = {
                Icon(Icons.Filled.Settings, contentDescription = null)
            },
            label = "Settings",
        )
    }
}

@Composable
private fun QuickActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
) {
    Card(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun RecordActionButton(
    onClick: () -> Unit,
    label: String,
    ringColor: Color,
    centerColor: Color,
    isActive: Boolean = false,
    amplitudeRatio: Float = 0f,
    supportingText: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "record-action")
    val audioReactiveScale by animateFloatAsState(
        targetValue = 1f + (amplitudeRatio.coerceIn(0f, 1f) * if (isActive) 0.18f else 0.04f),
        animationSpec = tween(durationMillis = 140),
        label = "record-audio-reactive-scale",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 1180 else 1680,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-action-scale",
    )
    val outerRingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (isActive) 1.08f else 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1000 else 1720, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-outer-ring",
    )
    val middleRingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isActive) 1.05f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1320 else 1460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-middle-ring",
    )
    val innerRingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.035f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 820 else 1240, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-inner-ring",
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2f).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1500 else 2600, easing = LinearEasing),
        ),
        label = "record-wave-phase",
    )
    val waveStrength = when {
        amplitudeRatio > 0f -> 0.008f + (amplitudeRatio.coerceIn(0f, 1f) * 0.028f)
        isActive -> 0.012f
        else -> 0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    val combinedScale = pulseScale * audioReactiveScale
                    scaleX = combinedScale
                    scaleY = combinedScale
                },
            contentAlignment = Alignment.Center,
        ) {
            RingLayer(
                diameter = 220.dp,
                color = ringColor.copy(alpha = 0.10f),
                scale = outerRingScale,
                wavePhase = wavePhase,
                waveStrength = waveStrength * 0.8f,
            )
            RingLayer(
                diameter = 180.dp,
                color = ringColor.copy(alpha = 0.16f),
                scale = middleRingScale,
                wavePhase = wavePhase + 0.8f,
                waveStrength = waveStrength,
            )
            RingLayer(
                diameter = 144.dp,
                color = ringColor.copy(alpha = 0.24f),
                scale = innerRingScale,
                wavePhase = wavePhase + 1.6f,
                waveStrength = waveStrength * 1.15f,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.size(116.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = centerColor),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        supportingText?.let { text ->
            Text(
                text = text,
                modifier = Modifier.widthIn(max = 420.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
        modifier = Modifier
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
            val perturbation = if (waveStrength > 0f) {
                sin((angle * 6f) + wavePhase).toFloat() * baseRadius * waveStrength
            } else {
                0f
            }
            val radius = baseRadius + perturbation
            val point = Offset(
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
    val displayValues = if (amplitudes.isEmpty()) {
        List(28) { 0f }
    } else {
        amplitudes.takeLast(28)
    }
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accentColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    Canvas(modifier = modifier) {
        val baselineY = size.height * 0.84f
        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val barWidth = size.width / max(displayValues.size * 2, 1)
        val spacing = barWidth * 0.92f
        val ceilingPadding = size.height * 0.12f
        val activeIndex = displayValues.lastIndex
        displayValues.forEachIndexed { index, amplitude ->
            val shapedAmplitude = visualAmplitude(amplitude)
            val lineHeight = (baselineY - ceilingPadding) * shapedAmplitude
            val x = (index * (barWidth + spacing)) + barWidth / 2
            val startY = baselineY - lineHeight
            val color = when {
                index == activeIndex -> accentColor.copy(alpha = 0.65f + (currentAmplitudeRatio * 0.35f))
                index >= displayValues.lastIndex - 4 -> barColor.copy(alpha = 0.78f)
                else -> barColor.copy(alpha = 0.34f + (index.toFloat() / displayValues.size) * 0.32f)
            }
            drawLine(
                color = color,
                start = Offset(x, startY),
                end = Offset(x, baselineY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun visualAmplitude(value: Float): Float {
    val gated = if (value < 0.02f) 0f else value
    return (0.02f + (gated * 0.98f)).coerceIn(0.02f, 1f)
}

private fun formatElapsedTime(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
