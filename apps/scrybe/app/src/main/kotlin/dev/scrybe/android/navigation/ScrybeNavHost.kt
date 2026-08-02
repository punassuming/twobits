package dev.scrybe.android.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.scrybe.android.ui.ScrybeWhatsNewScreen
import dev.scrybe.feature.capture.CaptureScreen
import dev.scrybe.feature.filemanager.FileManagerScreen
import dev.scrybe.feature.profiles.ProfilesScreen
import dev.scrybe.feature.sessiondetail.SessionDetailScreen
import dev.scrybe.feature.settings.AIConfigScreen
import dev.scrybe.feature.settings.AiCallDebugScreen
import dev.scrybe.feature.settings.CrashLogScreen
import dev.scrybe.feature.settings.PeopleScreen
import dev.scrybe.feature.settings.ProScreen
import dev.scrybe.feature.settings.RecordingTypesScreen
import dev.scrybe.feature.settings.SettingsScreen
import dev.scrybe.feature.tasks.TaskInboxScreen

sealed class Screen(
    val route: String,
) {
    object Capture : Screen("capture")

    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "session_detail/$sessionId"
    }

    object Profiles : Screen("profiles")

    object FileManager : Screen("file_manager")

    object Settings : Screen("settings")

    object Tasks : Screen("tasks")

    object AiConfig : Screen("ai_config")

    object AiCallDebugLog : Screen("ai_call_debug_log")

    object CrashLog : Screen("crash_log")

    object RecordingTypes : Screen("recording_types")

    object WhatsNew : Screen("whats_new")

    object People : Screen("people")

    object Pro : Screen("pro")
}

@Composable
fun ScrybeNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Capture.route,
) {
    val recordingCompletionViewModel: RecordingCompletionViewModel = hiltViewModel()

    LaunchedEffect(recordingCompletionViewModel) {
        recordingCompletionViewModel.events.collect { event ->
            when (event) {
                RecordingCompletionNavEvent.OpenHome -> {
                    navController.navigate(Screen.Capture.route) {
                        popUpTo(Screen.Capture.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                is RecordingCompletionNavEvent.OpenSessionReview -> {
                    navController.navigate(Screen.SessionDetail.createRoute(event.sessionId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val fadeEnter = fadeIn()
    val fadeExit = fadeOut()
    val slideEnter = slideInHorizontally { it } + fadeIn()
    val slideExit = slideOutHorizontally { -it / 3 } + fadeOut()
    val popSlideEnter = slideInHorizontally { -it / 3 } + fadeIn()
    val popSlideExit = slideOutHorizontally { it } + fadeOut()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideEnter },
        exitTransition = { slideExit },
        popEnterTransition = { popSlideEnter },
        popExitTransition = { popSlideExit },
    ) {
        composable(
            Screen.Capture.route,
            enterTransition = { fadeEnter },
            exitTransition = { fadeExit },
            popEnterTransition = { fadeEnter },
            popExitTransition = { fadeExit },
        ) { backStackEntry ->
            val unminimizeRequested by backStackEntry.savedStateHandle
                .getStateFlow("unminimize", false)
                .collectAsState()
            CaptureScreen(
                unminimizeRequested = unminimizeRequested,
                onUnminimizeConsumed = { backStackEntry.savedStateHandle["unminimize"] = false },
                onNavigateToSessionDetail = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }
        composable(Screen.SessionDetail.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            SessionDetailScreen(sessionId = sessionId, onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.Profiles.route,
            enterTransition = { fadeEnter },
            exitTransition = { fadeExit },
            popEnterTransition = { fadeEnter },
            popExitTransition = { fadeExit },
        ) {
            ProfilesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.FileManager.route) {
            FileManagerScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Tasks.route) {
            TaskInboxScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
            )
        }
        composable(
            Screen.Settings.route,
            enterTransition = { fadeEnter },
            exitTransition = { fadeExit },
            popEnterTransition = { fadeEnter },
            popExitTransition = { fadeExit },
        ) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFileManager = { navController.navigate(Screen.FileManager.route) },
                onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                onNavigateToAiConfig = { navController.navigate(Screen.AiConfig.route) },
                onNavigateToWhatsNew = { navController.navigate(Screen.WhatsNew.route) },
                onNavigateToPeople = { navController.navigate(Screen.People.route) },
                onNavigateToPro = { navController.navigate(Screen.Pro.route) },
                onNavigateToRecordingTypes = { navController.navigate(Screen.RecordingTypes.route) },
            )
        }
        composable(Screen.AiConfig.route) {
            AIConfigScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAiCallLog = { navController.navigate(Screen.AiCallDebugLog.route) },
                onNavigateToCrashLog = { navController.navigate(Screen.CrashLog.route) },
            )
        }
        composable(Screen.AiCallDebugLog.route) {
            AiCallDebugScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.CrashLog.route) {
            CrashLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RecordingTypes.route) {
            RecordingTypesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.People.route) {
            PeopleScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.WhatsNew.route) {
            ScrybeWhatsNewScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { target ->
                    when (target) {
                        "settings" -> navController.navigate(Screen.Settings.route)
                        "profiles" -> navController.navigate(Screen.Profiles.route)
                        "ai_config" -> navController.navigate(Screen.AiConfig.route)
                        else -> Unit
                    }
                },
            )
        }
        composable(Screen.Pro.route) {
            ProScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
