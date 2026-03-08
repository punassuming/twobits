package dev.scrybe.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CaptureScreen(
    onNavigateToHistory: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val state = uiState) {
            is CaptureUiState.Idle -> {
                Button(
                    onClick = { viewModel.startRecording() },
                    modifier = Modifier.size(120.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                ) {
                    Text("Record")
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNavigateToHistory) {
                    Text("History")
                }
            }
            is CaptureUiState.Recording -> {
                Button(
                    onClick = { viewModel.stopRecording() },
                    modifier = Modifier.size(120.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                ) {
                    Text("Stop")
                }
                Spacer(Modifier.height(16.dp))
                Text(text = "Recording\u2026", style = MaterialTheme.typography.bodyMedium)
            }
            is CaptureUiState.Stopping -> {
                Text(text = "Stopping\u2026", style = MaterialTheme.typography.bodyMedium)
            }
            is CaptureUiState.Failed -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
