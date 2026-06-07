package dev.scrybe.android.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.scrybe.android.ui.ScrybeWhatsNewScreen
import dev.scrybe.feature.capture.CaptureScreen
import dev.scrybe.feature.filemanager.FileManagerScreen
import dev.scrybe.feature.history.HistoryScreen
import dev.scrybe.feature.profiles.ProfilesScreen
import dev.scrybe.feature.sessiondetail.SessionDetailScreen
import dev.scrybe.feature.settings.AIConfigScreen
import dev.scrybe.feature.settings.SettingsScreen
import dev.scrybe.feature.tasks.TaskInboxScreen

sealed class Screen(
    val route: String,
) {
    object Capture : Screen("capture")

    object History : Screen("history")

    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "session_detail/$sessionId"
    }

    object Profiles : Screen("profiles")

    object FileManager : Screen("file_manager")

    object Settings : Screen("settings")

    object Tasks : Screen("tasks")

    object AiConfig : Screen("ai_config")

    object WhatsNew : Screen("whats_new")
}

@Composable
fun ScrybeNavHost(navController: NavHostController) {
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
        startDestination = Screen.Capture.route,
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
        ) {
            CaptureScreen(
                onNavigateToSessionDetail = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }
        composable(
            Screen.History.route,
            enterTransition = { fadeEnter },
            exitTransition = { fadeExit },
            popEnterTransition = { fadeEnter },
            popExitTransition = { fadeExit },
        ) {
            HistoryScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
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
            val prevRoute = navController.previousBackStackEntry?.destination?.route
            val tabRoutes = setOf(Screen.Capture.route, Screen.History.route, Screen.Profiles.route, Screen.Settings.route)
            ProfilesScreen(
                onNavigateBack = if (prevRoute != null && prevRoute !in tabRoutes) {
                    { navController.popBackStack() }
                } else null,
            )
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
            )
        }
        composable(Screen.AiConfig.route) {
            AIConfigScreen(onBack = { navController.popBackStack() })
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
    }
}
