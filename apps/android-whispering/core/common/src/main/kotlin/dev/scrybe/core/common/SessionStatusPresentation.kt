package dev.scrybe.core.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.scrybe.core.model.SessionStatus

/**
 * Shared session-status presentation helpers used across history, session detail,
 * and any other UI surfaces that need to render a [SessionStatus]. Keeping these
 * centralized ensures a consistent icon/color/label mapping everywhere.
 */
object SessionStatusPresentation {
    fun label(
        status: SessionStatus,
        isArchived: Boolean,
    ): String =
        when {
            isArchived -> "Archived"
            status == SessionStatus.TRANSCRIBING -> "Transcribing"
            status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED -> "Transcribed"
            status == SessionStatus.FAILED -> "Failed"
            else -> "Recorded"
        }

    fun icon(
        status: SessionStatus,
        isArchived: Boolean,
    ): ImageVector =
        when {
            isArchived -> Icons.Filled.Archive
            status == SessionStatus.TRANSCRIBING -> Icons.Filled.HourglassEmpty
            status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED -> Icons.Filled.CheckCircle
            status == SessionStatus.FAILED -> Icons.Filled.Error
            // Covers RECORDED, IDLE, RECORDING, STOPPING, QUEUED and any future pre-transcription states
            else -> Icons.Filled.Mic
        }

    @Composable
    @ReadOnlyComposable
    fun color(
        status: SessionStatus,
        isArchived: Boolean,
    ): Color =
        when {
            isArchived -> MaterialTheme.colorScheme.tertiary
            status == SessionStatus.TRANSCRIBING -> MaterialTheme.colorScheme.secondary
            status == SessionStatus.TRANSCRIBED || status == SessionStatus.EDITED -> MaterialTheme.colorScheme.primary
            status == SessionStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
}
