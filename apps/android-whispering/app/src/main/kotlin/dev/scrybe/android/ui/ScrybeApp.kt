package dev.scrybe.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.scrybe.android.navigation.Screen
import dev.scrybe.android.navigation.ScrybeNavHost

@Composable
fun ScrybeApp() {
    val navController = rememberNavController()
    val whatsNewViewModel: WhatsNewViewModel = hiltViewModel()
    val activeRecordingViewModel: ActiveRecordingViewModel = hiltViewModel()
    val whatsNewState by whatsNewViewModel.uiState.collectAsState()
    val activeRecordingState by activeRecordingViewModel.uiState.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar =
        currentRoute in
            setOf(
                Screen.Capture.route,
                Screen.History.route,
                Screen.Profiles.route,
                Screen.Settings.route,
            )

    Column(modifier = Modifier.fillMaxSize()) {
        MainContentBox(
            navController = navController,
            activeRecordingState = activeRecordingState,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                NavigationBarItem(
                    selected = currentRoute == Screen.Capture.route,
                    onClick = {
                        navController.navigate(Screen.Capture.route) {
                            popUpTo(Screen.Capture.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Mic, contentDescription = "Record", modifier = Modifier.size(28.dp)) },
                    label = { Text("Record") },
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.History.route,
                    onClick = {
                        navController.navigate(Screen.History.route) {
                            popUpTo(Screen.Capture.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History", modifier = Modifier.size(28.dp)) },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.Profiles.route,
                    onClick = {
                        navController.navigate(Screen.Profiles.route) {
                            popUpTo(Screen.Capture.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Tune, contentDescription = "Profiles", modifier = Modifier.size(28.dp)) },
                    label = { Text("Profiles") },
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Capture.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp)) },
                    label = { Text("Settings") },
                )
            }
        }
    }

    if (whatsNewState.isVisible) {
        AlertDialog(
            onDismissRequest = whatsNewViewModel::dismiss,
            title = {
                Text(
                    text =
                        if (whatsNewState.isFirstRun) {
                            "Welcome to Scrybe"
                        } else if (whatsNewState.versionName.isBlank()) {
                            "What's New"
                        } else {
                            "What's New in ${whatsNewState.versionName}"
                        },
                )
            },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (whatsNewState.summary.isNotBlank()) {
                        Text(
                            text = whatsNewState.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = whatsNewState.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    whatsNewState.notes.forEach { note ->
                        Text(
                            text = "• $note",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = whatsNewViewModel::dismiss) {
                    Text(whatsNewState.confirmLabel)
                }
            },
        )
    }
}

@Composable
private fun MainContentBox(
    navController: NavHostController,
    activeRecordingState: ActiveRecordingUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        ScrybeNavHost(navController = navController)

        AnimatedVisibility(
            visible = activeRecordingState.isRecording,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        ) {
            ActiveRecordingBanner(
                elapsedMs = activeRecordingState.elapsedMs,
                amplitudeRatio = activeRecordingState.amplitudeRatio,
                onOpen = {
                    navController.navigate(Screen.Capture.route) {
                        launchSingleTop = true
                    }
                },
            )
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

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .scale(reactiveScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                )
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 10.dp, end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Recording active",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Elapsed ${formatBannerElapsed(elapsedMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.RadioButtonChecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatBannerElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
