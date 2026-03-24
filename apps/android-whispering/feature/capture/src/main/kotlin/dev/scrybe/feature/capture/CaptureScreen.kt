package dev.scrybe.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.max

@Composable
fun CaptureScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (uiState.phase) {
            CapturePhase.IDLE -> {
                if (!hasRequiredPermissions) {
                    Text(
                        text = "Microphone permission is required before you can record.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Button(
                    onClick = {
                        if (hasRequiredPermissions) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(requiredPermissions.toTypedArray())
                        }
                    },
                    modifier = Modifier.size(120.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                ) {
                    Text("Record")
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNavigateToHistory) {
                        Text("History")
                    }
                    Button(onClick = onNavigateToProfiles) {
                        Text("Profiles")
                    }
                    Button(onClick = onNavigateToSettings) {
                        Text("Settings")
                    }
                }
            }
            CapturePhase.RECORDING -> {
                Button(
                    onClick = { viewModel.stopRecording() },
                    modifier = Modifier.size(120.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                ) {
                    Text("Stop")
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Recording\u2026 ${formatElapsedTime(uiState.elapsedMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                AmplitudeVisualizer(
                    amplitudes = uiState.amplitudeHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                )
            }
            CapturePhase.STOPPING -> {
                Text(text = "Stopping\u2026", style = MaterialTheme.typography.bodyMedium)
            }
        }
        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Error: $message",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AmplitudeVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
) {
    val displayValues = if (amplitudes.isEmpty()) List(24) { 0f } else amplitudes.takeLast(24)
    Canvas(modifier = modifier) {
        val barWidth = size.width / max(displayValues.size * 2, 1)
        val spacing = barWidth
        displayValues.forEachIndexed { index, amplitude ->
            val lineHeight = size.height * (0.15f + amplitude * 0.85f)
            val x = (index * (barWidth + spacing)) + barWidth / 2
            val startY = (size.height - lineHeight) / 2
            drawLine(
                color = Color(0xFF2E7D32),
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
