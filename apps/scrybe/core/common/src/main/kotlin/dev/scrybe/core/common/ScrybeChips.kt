package dev.scrybe.core.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.SessionStatus

// ── Recording mode accent colors ─────────────────────────────────────────────

@Composable
@ReadOnlyComposable
fun modeAccentColor(mode: RecordingMode): Color =
    when (mode) {
        RecordingMode.MEETING -> Color(0xFF89C7FF)
        RecordingMode.IDEA -> Color(0xFFFFD580)
        RecordingMode.TASKS -> Color(0xFF7DD4DC)
        RecordingMode.CONVERSATION -> Color(0xFFC4ABFF)
        RecordingMode.STORY -> Color(0xFFFF9EC4)
        RecordingMode.INTERVIEW -> Color(0xFFFFB695)
        RecordingMode.JOURNAL -> MaterialTheme.colorScheme.onSurfaceVariant
        RecordingMode.CUSTOM -> MaterialTheme.colorScheme.secondary
    }

fun modeIcon(mode: RecordingMode): ImageVector =
    when (mode) {
        RecordingMode.MEETING -> Icons.Filled.Groups
        RecordingMode.IDEA -> Icons.Filled.Lightbulb
        RecordingMode.TASKS -> Icons.Filled.TaskAlt
        RecordingMode.CONVERSATION -> Icons.Filled.Forum
        RecordingMode.STORY -> Icons.Filled.MenuBook
        RecordingMode.INTERVIEW -> Icons.Filled.PersonSearch
        RecordingMode.JOURNAL -> Icons.Filled.Book
        RecordingMode.CUSTOM -> Icons.Filled.Label
    }

// ── Shared chips ──────────────────────────────────────────────────────────────

/**
 * Pill badge showing the recording mode icon and label with a mode-specific accent color.
 * Used on session cards, the recording active view, and the task inbox.
 */
@Composable
fun ModeBadge(mode: RecordingMode) {
    val accentColor = modeAccentColor(mode)
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.18f),
    ) {
        Row(
            modifier =
                androidx.compose.ui.Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = modeIcon(mode),
                contentDescription = null,
                tint = accentColor,
                modifier =
                    androidx.compose.ui.Modifier
                        .size(12.dp),
            )
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
            )
        }
    }
}

/**
 * Pill chip showing session transcription status with icon + label. Returns without
 * rendering anything for pre-transcription statuses (IDLE, RECORDING, etc.).
 */
@Composable
fun SessionStatusChip(
    status: SessionStatus,
    isArchived: Boolean = false,
) {
    val label = SessionStatusPresentation.label(status, isArchived)
    val icon = SessionStatusPresentation.icon(status, isArchived)
    val color = SessionStatusPresentation.color(status, isArchived)

    val preTranscriptionStatuses =
        setOf(
            SessionStatus.IDLE,
            SessionStatus.RECORDING,
            SessionStatus.STOPPING,
            SessionStatus.RECORDED,
        )
    if (!isArchived && status in preTranscriptionStatuses) {
        return
    }

    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Row(
            modifier =
                androidx.compose.ui.Modifier
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier =
                    androidx.compose.ui.Modifier
                        .size(11.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
