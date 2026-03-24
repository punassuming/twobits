package dev.scrybe.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.scrybe.feature.capture.CaptureScreen
import dev.scrybe.feature.history.HistoryScreen
import dev.scrybe.feature.sessiondetail.SessionDetailScreen
import dev.scrybe.feature.profiles.ProfilesScreen
import dev.scrybe.feature.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Capture : Screen("capture")
    object History : Screen("history")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "session_detail/$sessionId"
    }
    object Profiles : Screen("profiles")
    object Settings : Screen("settings")
}

@Composable
fun ScrybeNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Capture.route
    ) {
        composable(Screen.Capture.route) {
            CaptureScreen(
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.SessionDetail.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            SessionDetailScreen(sessionId = sessionId, onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Profiles.route) {
            ProfilesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
