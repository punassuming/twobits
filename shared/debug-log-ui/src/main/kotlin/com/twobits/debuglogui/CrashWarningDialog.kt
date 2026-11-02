package com.twobits.debuglogui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * [CrashWarningViewModel.staleStartWarning] surfaced as a one-time dialog. A native crash (a bad
 * model file, an ONNX/LiteRT abort) has no catchable Kotlin exception to report through the usual
 * error paths, so without this the app would silently relaunch with no explanation.
 *
 * @param appName shown in the title ("Scrybe closed unexpectedly").
 * @param opLabel the operation that was running when the app died.
 * @param likelyCause short phrase completing "likely a crash in …", e.g. "on-device transcription".
 */
@Composable
fun CrashWarningDialog(
    appName: String,
    opLabel: String,
    likelyCause: String,
    onViewDebugLog: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$appName closed unexpectedly") },
        text = {
            Text(
                "It looks like the app closed while running \"$opLabel\" last time — likely a crash " +
                    "in $likelyCause. The debug log has details.",
            )
        },
        confirmButton = {
            TextButton(onClick = onViewDebugLog) { Text("View debug log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
