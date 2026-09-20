package dev.scrybe.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.backup.BackupOperation
import dev.scrybe.core.backup.BackupPassphraseHolder
import dev.scrybe.core.backup.BackupProgress
import dev.scrybe.core.backup.BackupProgressTracker
import dev.scrybe.core.backup.BackupReader
import dev.scrybe.core.backup.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * What the Backup screen is doing and what it last produced.
 *
 * [pendingRestoreUri] drives the passphrase prompt: the file is inspected as soon as it is picked,
 * and only a backup whose header says it is encrypted asks for anything. That is why the header
 * stays in the clear.
 */
data class BackupUiState(
    val progress: BackupProgress = BackupProgress.idle,
    val message: String? = null,
    val isError: Boolean = false,
    val pendingRestoreUri: Uri? = null,
)

@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val progressTracker: BackupProgressTracker,
        private val passphraseHolder: BackupPassphraseHolder,
        private val backupReader: BackupReader,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BackupUiState())
        val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                progressTracker.progress.collect { progress -> _uiState.value = _uiState.value.copy(progress = progress) }
            }
        }

        /** A filename that sorts chronologically and says which app wrote it. */
        fun suggestedFileName(): String = "scrybe-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.scrybe"

        fun backUp(
            destination: Uri,
            passphrase: String?,
        ) {
            _uiState.value = _uiState.value.copy(message = null, isError = false)
            BackupWorker.enqueue(
                context = context,
                operation = BackupOperation.BACKUP,
                uri = destination,
                passphrase = passphrase?.takeIf { it.isNotEmpty() }?.toCharArray(),
                passphraseHolder = passphraseHolder,
            )
        }

        fun exportRecordings(folder: Uri) {
            _uiState.value = _uiState.value.copy(message = null, isError = false)
            BackupWorker.enqueue(
                context = context,
                operation = BackupOperation.EXPORT,
                uri = folder,
                passphraseHolder = passphraseHolder,
            )
        }

        /**
         * Reads only the header, so an unencrypted backup restores without a prompt and an encrypted
         * one asks before any work starts.
         */
        fun onRestoreFilePicked(source: Uri) {
            viewModelScope.launch {
                val encrypted =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(source)?.let { backupReader.inspect(it).encrypted }
                        }
                    }
                encrypted.fold(
                    onSuccess = { isEncrypted ->
                        when (isEncrypted) {
                            null -> showError("Could not open that file.")
                            true -> _uiState.value = _uiState.value.copy(pendingRestoreUri = source, message = null, isError = false)
                            false -> restore(source, passphrase = null)
                        }
                    },
                    onFailure = { showError(it.message ?: "That file could not be read as a backup.") },
                )
            }
        }

        fun restore(
            source: Uri,
            passphrase: String?,
        ) {
            _uiState.value = _uiState.value.copy(pendingRestoreUri = null, message = null, isError = false)
            BackupWorker.enqueue(
                context = context,
                operation = BackupOperation.RESTORE,
                uri = source,
                passphrase = passphrase?.takeIf { it.isNotEmpty() }?.toCharArray(),
                passphraseHolder = passphraseHolder,
            )
        }

        fun cancelPendingRestore() {
            _uiState.value = _uiState.value.copy(pendingRestoreUri = null)
        }

        fun dismissMessage() {
            _uiState.value = _uiState.value.copy(message = null, isError = false)
        }

        private fun showError(text: String) {
            _uiState.value = _uiState.value.copy(message = text, isError = true, pendingRestoreUri = null)
        }

        override fun onCleared() {
            // The passphrase must not outlive the screen that collected it.
            passphraseHolder.clear()
            super.onCleared()
        }
    }
