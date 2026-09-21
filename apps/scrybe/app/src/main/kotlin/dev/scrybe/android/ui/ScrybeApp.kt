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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.twobits.debuglogui.CrashWarningViewModel
import com.twobits.design.components.AppWhatsNewDialog
import com.twobits.design.components.ProgressFooter
import dev.scrybe.android.navigation.Screen
import dev.scrybe.android.navigation.ScrybeNavHost
import dev.scrybe.core.transcription.TranscriptionChunkProgressTracker
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
 * [DebugLogStore.staleStartWarning][com.twobits.debuglog.DebugLogStore.staleStartWarning]
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
    // contentWindowInsets is zeroed out deliberately: individual screens under ScrybeNavHost
    // already manage their own top/side system-bar insets (there's no topBar here for Scaffold to
    // reserve space for), so this Scaffold's only job is reserving bottom space for the toast —
    // letting it also fold system-bar insets into innerPadding would double up with what each
    // screen already applies on its own.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // AnimatedVisibility(visible = false) collapses to zero height, so innerPadding's
            // bottom value below shrinks back to zero the moment this isn't showing, and grows to
            // exactly its measured height while it is — this is what actually reduces the content
            // area and makes the footer come up from the real bottom edge, instead of floating
            // over content that has no idea it exists (the previous Box+align(BottomCenter)
            // overlay). No navigationBarsPadding() here — ProgressFooter applies gesture-nav
            // clearance to its own inner content instead, so its card can reach the true bottom
            // edge unconditionally. See its own doc comment for why.
            ProgressFooter(
                visible = transcriptionProgressState.isTranscribing,
                primaryText = if (transcriptionProgressState.isCancelling) "Cancelling…" else "Transcribing…",
                secondaryText = transcriptionProgressState.label.takeIf { it.isNotBlank() },
                tertiaryText = transcriptionProgressState.progressDetailText(),
                progressFraction = transcriptionProgressState.chunkProgress?.asFraction(),
                onCancel = onCancelTranscription,
                isCancelling = transcriptionProgressState.isCancelling,
            )
        },
    ) { innerPadding ->
        // fillMaxSize() here (and on ScrybeNavHost's own NavHost below) was dropped when this
        // Box's modifier was replaced during the Scaffold conversion above — without it, this
        // content box has no guarantee of actually filling the space the Scaffold reserves for
        // it, which is what let the toast (this Scaffold's bottomBar) visually sit short of the
        // true screen edge on some screens instead of flush against it, as intended.
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
        }
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

private fun TranscriptionChunkProgressTracker.Progress.asFraction(): Float = completed.toFloat() / total

/**
 * The footer's third line: chunk progress and the queued-behind count, whichever of the two
 * apply, joined when both do. Kept to one line — this and [secondaryText] (the title) are the
 * only two the footer has room for once [primaryText] is spent on the plain status word.
 */
private fun TranscriptionProgressUiState.progressDetailText(): String? {
    val chunkText = chunkProgress?.let { "chunk ${it.completed} of ${it.total}" }
    val queuedText = queuedCount.takeIf { it > 0 }?.let { "$it more queued" }
    return listOfNotNull(chunkText, queuedText).joinToString(" · ").ifBlank { null }
}
