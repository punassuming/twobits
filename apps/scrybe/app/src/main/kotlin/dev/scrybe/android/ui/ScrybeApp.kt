package dev.scrybe.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.twobits.design.components.AppWhatsNewDialog
import dev.scrybe.android.navigation.Screen
import dev.scrybe.android.navigation.ScrybeNavHost
import dev.scrybe.feature.capture.OnboardingScreen
import dev.scrybe.feature.capture.OnboardingViewModel

@Composable
fun ScrybeApp(
    uiTestRoute: String? = null,
    suppressUiTestDialogs: Boolean = false,
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val hasSeenOnboarding by onboardingViewModel.hasSeenOnboarding.collectAsState()
    if (hasSeenOnboarding == null) return
    if (hasSeenOnboarding == false && uiTestRoute == null) {
        OnboardingScreen(
            onComplete = onboardingViewModel::completeOnboarding,
            onSaveApiKey = onboardingViewModel::saveApiKey,
        )
        return
    }
    ScrybeMainContent(
        startDestination = uiTestRoute ?: Screen.Capture.route,
        suppressWhatsNew = suppressUiTestDialogs,
    )
}

@Composable
private fun ScrybeMainContent(
    startDestination: String,
    suppressWhatsNew: Boolean,
) {
    val navController = rememberNavController()
    val whatsNewViewModel: WhatsNewViewModel = hiltViewModel()
    val activeRecordingViewModel: ActiveRecordingViewModel = hiltViewModel()
    val transcriptionProgressViewModel: TranscriptionProgressViewModel = hiltViewModel()
    val crashWarningViewModel: CrashWarningViewModel = hiltViewModel()
    val whatsNewState by whatsNewViewModel.uiState.collectAsState()
    val activeRecordingState by activeRecordingViewModel.uiState.collectAsState()
    val transcriptionProgressState by transcriptionProgressViewModel.uiState.collectAsState()
    val staleStartWarning by crashWarningViewModel.staleStartWarning.collectAsState()
    MainContentBox(
        navController = navController,
        activeRecordingState = activeRecordingState,
        transcriptionProgressState = transcriptionProgressState,
        onCancelTranscription = transcriptionProgressViewModel::cancel,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
    )
    if (whatsNewState.isVisible && !suppressWhatsNew) {
        AppWhatsNewDialog(
            title = whatsNewState.title,
            categories = whatsNewState.categories,
            confirmLabel = whatsNewState.confirmLabel,
            onDismiss = whatsNewViewModel::dismiss,
            onViewHistory = { navController.navigate(Screen.WhatsNew.route) },
        )
    }
    staleStartWarning?.let { entry ->
        CrashWarningDialog(
            opLabel = entry.op.orEmpty().removeSuffix("-start"),
            onViewDebugLog = {
                crashWarningViewModel.dismiss()
                navController.navigate(Screen.DebugLog.route)
            },
            onDismiss = crashWarningViewModel::dismiss,
        )
    }
}

/**
 * [DebugLogStore.staleStartWarning][dev.scrybe.core.transcription.DebugLogStore.staleStartWarning]
 * surfaced as a one-time dialog — a native crash (a bad model file, an ONNX/LiteRT abort) has no
 * catchable Kotlin exception to report through the usual error paths, so without this the app
 * would just silently relaunch with no explanation for what happened last time.
 */
@Composable
private fun CrashWarningDialog(
    opLabel: String,
    onViewDebugLog: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scrybe closed unexpectedly") },
        text = {
            Text(
                "It looks like the app closed while running \"$opLabel\" last time — likely a crash " +
                    "in on-device transcription. Check the Debug Log for details.",
            )
        },
        confirmButton = {
            TextButton(onClick = onViewDebugLog) { Text("View Debug Log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        },
    )
}

@Composable
private fun MainContentBox(
    navController: NavHostController,
    activeRecordingState: ActiveRecordingUiState,
    transcriptionProgressState: TranscriptionProgressUiState,
    onCancelTranscription: () -> Unit,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        ScrybeNavHost(navController = navController, startDestination = startDestination)

        AnimatedVisibility(
            visible = activeRecordingState.isRecording,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        ) {
            ActiveRecordingBanner(
                elapsedMs = activeRecordingState.elapsedMs,
                amplitudeRatio = activeRecordingState.amplitudeRatio,
                onOpen = {
                    try {
                        navController
                            .getBackStackEntry(Screen.Capture.route)
                            .savedStateHandle["unminimize"] = true
                    } catch (_: IllegalArgumentException) {
                    }
                    navController.navigate(Screen.Capture.route) {
                        launchSingleTop = true
                    }
                },
            )
        }

        TranscriptionProgressToast(
            visible = transcriptionProgressState.isTranscribing,
            label = transcriptionProgressState.label,
            queuedCount = transcriptionProgressState.queuedCount,
            onCancel = onCancelTranscription,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun ActiveRecordingBanner(
    elapsedMs: Long,
    amplitudeRatio: Float,
    onOpen: () -> Unit,
) {
    val reactiveScale by animateFloatAsState(
        targetValue = 1f + (amplitudeRatio.coerceIn(0f, 1f) * 0.14f),
        label = "active-recording-banner-scale",
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .scale(reactiveScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
            )
            Text(
                text = "Recording · ${formatBannerElapsed(elapsedMs)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun formatBannerElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
