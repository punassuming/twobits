package dev.scrybe.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twobits.design.components.AppLabeledSectionCard
import dev.scrybe.core.backup.BackupOperation

/**
 * Backing up the whole history, restoring it, and exporting the audio on its own.
 *
 * Three actions rather than one, because they serve different ends. A backup is for coming back to
 * Scrybe — it carries the database, so titles, transcripts and dates survive. The export is for
 * leaving: plain audio files another app can open, with no Scrybe-specific wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var usePassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }

    val createBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
            uri?.let { viewModel.backUp(it, passphrase.takeIf { _ -> usePassphrase }) }
        }
    val pickBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::onRestoreFilePicked)
        }
    val pickExportFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::exportRecordings)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.progress.isRunning) {
                BackupProgressCard(uiState)
            }
            uiState.message?.let { message ->
                AppLabeledSectionCard(title = if (uiState.isError) "Something went wrong" else "Done") {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
                }
            }

            AppLabeledSectionCard(title = "Back up everything") {
                Text(
                    "Saves every recording together with its title, date and transcript in a single " +
                        "file. Restore it on a new phone to pick up where you left off.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PassphraseToggleRow(
                        label = "Protect with a passphrase",
                        checked = usePassphrase,
                        onCheckedChange = { usePassphrase = it },
                    )
                    if (usePassphrase) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "There is no way to recover this passphrase. Without it the backup " +
                                "cannot be opened by anyone, including you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(
                    onClick = { createBackup.launch(viewModel.suggestedFileName()) },
                    enabled = !uiState.progress.isRunning && (!usePassphrase || passphrase.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back up")
                }
            }

            AppLabeledSectionCard(title = "Restore from a backup") {
                Text(
                    "Adds everything from a backup file. Recordings already on this phone are left " +
                        "untouched, so restoring twice is safe.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    enabled = !uiState.progress.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose a backup file")
                }
            }

            AppLabeledSectionCard(title = "Export recordings only") {
                Text(
                    "Copies the audio files into a folder you choose, named so other apps can open " +
                        "them. No transcripts or titles — use a backup for those.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { pickExportFolder.launch(null) },
                    enabled = !uiState.progress.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose a folder")
                }
            }
        }
    }

    uiState.pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingRestore,
            title = { Text("This backup is protected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase it was created with.")
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restore(uri, restorePassphrase)
                        restorePassphrase = ""
                    },
                    enabled = restorePassphrase.isNotBlank(),
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelPendingRestore()
                    restorePassphrase = ""
                }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BackupProgressCard(uiState: BackupUiState) {
    val label =
        when (uiState.progress.operation) {
            BackupOperation.BACKUP -> "Backing up"
            BackupOperation.RESTORE -> "Restoring"
            BackupOperation.EXPORT -> "Exporting"
            null -> ""
        }
    AppLabeledSectionCard(title = label) {
        val fraction = uiState.progress.fraction
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "${uiState.progress.completed} of ${uiState.progress.total}",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            // Before the item count is known, an indeterminate bar is honest where "1 of 0" is not.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Preparing…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PassphraseToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Deliberately generic. A `.scrybe` file has no registered MIME type, and claiming a specific one
 * makes some file pickers refuse to create it.
 */
private const val BACKUP_MIME_TYPE = "application/octet-stream"
