package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.max

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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.OpenSessionDetail -> onNavigateToSessionDetail(event.sessionId)
            }
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
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Scrybe", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Capture audio, review the recording, transcribe it with OpenAI, and turn it into polished notes or action items.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when (uiState.phase) {
            CapturePhase.IDLE -> {
                HomeCard {
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
                    )
                }
                QuickActionRow(
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToProfiles = onNavigateToProfiles,
                    onNavigateToSettings = onNavigateToSettings,
                )
            }
            CapturePhase.RECORDING -> {
                HomeCard {
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
                    )
                }
            }
            CapturePhase.STOPPING -> {
                HomeCard {
                    Text(text = "Stopping\u2026", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "We’re saving the recording and opening the review screen next.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
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
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
        FilledTonalButton(
            onClick = onNavigateToHistory,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 88.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.height(6.dp))
                Text("Records", textAlign = TextAlign.Center)
            }
        }
        FilledTonalButton(
            onClick = onNavigateToProfiles,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 88.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.height(6.dp))
                Text("Profiles", textAlign = TextAlign.Center)
            }
        }
        FilledTonalButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 88.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.height(6.dp))
                Text("Settings", textAlign = TextAlign.Center)
            }
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
) {
    val infiniteTransition = rememberInfiniteTransition(label = "record-action")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 1400 else 2200,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-action-scale",
    )
    val outerRingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (isActive) 1.06f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1200 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-outer-ring",
    )
    val middleRingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isActive) 1.04f else 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1600 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-middle-ring",
    )
    val innerRingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.03f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 900 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "record-inner-ring",
    )

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
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            contentAlignment = Alignment.Center,
        ) {
            RingLayer(
                size = 220.dp,
                color = ringColor.copy(alpha = 0.10f),
                scale = outerRingScale,
            )
            RingLayer(
                size = 180.dp,
                color = ringColor.copy(alpha = 0.16f),
                scale = middleRingScale,
            )
            RingLayer(
                size = 144.dp,
                color = ringColor.copy(alpha = 0.24f),
                scale = innerRingScale,
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
    }
}

@Composable
private fun RingLayer(size: Dp, color: Color, scale: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape),
    )
}

@Composable
private fun AmplitudeVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
) {
    val displayValues = if (amplitudes.isEmpty()) List(24) { 0f } else amplitudes.takeLast(24)
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barWidth = size.width / max(displayValues.size * 2, 1)
        val spacing = barWidth
        displayValues.forEachIndexed { index, amplitude ->
            val lineHeight = size.height * (0.15f + amplitude * 0.85f)
            val x = (index * (barWidth + spacing)) + barWidth / 2
            val startY = (size.height - lineHeight) / 2
            drawLine(
                color = barColor,
                start = androidx.compose.ui.geometry.Offset(x, startY),
                end = androidx.compose.ui.geometry.Offset(x, startY + lineHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatElapsedTime(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
