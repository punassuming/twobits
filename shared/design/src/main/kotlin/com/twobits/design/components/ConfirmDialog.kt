package com.twobits.design.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The standard confirmation dialog. Use it before any action the user can't take back (delete,
 * clear, reset). When [destructive] is true the confirm button is drawn in the error color.
 *
 * Prefer an undo snackbar ([showUndoSnackbar]) over this dialog when the action can be reversed
 * cheaply — a confirmation interrupts every time, an undo only costs the user when they need it.
 */
@Composable
fun ConfirmDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel",
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/**
 * Shows "[message] · Undo" and suspends until the snackbar is gone. Returns true when the user
 * tapped Undo. Any snackbar already showing is dismissed first so undo offers never queue up
 * behind each other.
 */
suspend fun SnackbarHostState.showUndoSnackbar(
    message: String,
    actionLabel: String = "Undo",
): Boolean {
    currentSnackbarData?.dismiss()
    return showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = SnackbarDuration.Short,
    ) == SnackbarResult.ActionPerformed
}
