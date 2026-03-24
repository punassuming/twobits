package dev.scrybe.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.scrybe.android.navigation.ScrybeNavHost

@Composable
fun ScrybeApp() {
    val navController = rememberNavController()
    val whatsNewViewModel: WhatsNewViewModel = hiltViewModel()
    val whatsNewState by whatsNewViewModel.uiState.collectAsState()

    ScrybeNavHost(navController = navController)

    if (whatsNewState.isVisible) {
        AlertDialog(
            onDismissRequest = whatsNewViewModel::dismiss,
            title = {
                Text(
                    text = if (whatsNewState.versionName.isBlank()) {
                        "What's New"
                    } else {
                        "What's New in ${whatsNewState.versionName}"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        text = whatsNewState.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    whatsNewState.notes.forEach { note ->
                        Text(
                            text = "\u2022 $note",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = whatsNewViewModel::dismiss) {
                    Text("Close")
                }
            },
        )
    }
}
