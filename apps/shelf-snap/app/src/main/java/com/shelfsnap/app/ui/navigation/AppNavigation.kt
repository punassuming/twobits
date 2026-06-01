package com.shelfsnap.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shelfsnap.app.R
import com.shelfsnap.app.ui.camera.CameraScreen
import com.shelfsnap.app.ui.inventory.InventoryScreen
import com.shelfsnap.app.ui.itemdetail.ItemDetailScreen
import com.shelfsnap.app.ui.settings.SettingsScreen
import com.shelfsnap.app.ui.whatsnew.WhatsNewScreen
import com.shelfsnap.app.ui.summary.SummaryScreen

/**
 * Top-level destinations shown in the persistent bottom navigation bar. [route] is the
 * NavGraph route pattern (used to detect the selected tab); [navTarget] is the concrete
 * route navigated to (differs for Camera, which has an optional argument).
 */
private data class BottomDest(
    val route: String,
    val navTarget: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDest(Screen.Camera.route, Screen.Camera.createRoute(), R.string.nav_camera, Icons.Default.PhotoCamera),
    BottomDest(Screen.Inventory.route, Screen.Inventory.route, R.string.nav_inventory, Icons.Default.Inventory2),
    BottomDest(Screen.Summary.route, Screen.Summary.route, R.string.nav_summary, Icons.Default.Summarize),
    BottomDest(Screen.Settings.route, Screen.Settings.route, R.string.nav_settings, Icons.Default.Settings)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        // Each screen owns its own Scaffold and handles status-bar insets, so the
        // outer one only contributes the bottom navigation bar's height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    navController.navigate(dest.navTarget) {
                                        // Keep a single instance of each top-level tab and
                                        // preserve its scroll/state across tab switches.
                                        popUpTo(Screen.Inventory.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Reserve only the bottom-bar height; screens draw their own status-bar insets.
        NavHost(
            navController = navController,
            startDestination = Screen.Inventory.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Inventory.route) {
                InventoryScreen(
                    onAddItem = { navController.navigate(Screen.Camera.createRoute()) },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onSummaryClick = { navController.navigate(Screen.Summary.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(
                route = Screen.Camera.route,
                arguments = listOf(navArgument("itemId") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStackEntry ->
                val cameraItemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                CameraScreen(
                    onItemSaved = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId)) {
                            popUpTo(Screen.Camera.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    itemId = cameraItemId
                )
            }

            composable(
                route = Screen.ItemDetail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(Screen.Inventory.route) { inclusive = false }
                        }
                    },
                    onAddPhoto = { navController.navigate(Screen.Camera.createRoute(itemId)) }
                )
            }

            composable(Screen.Summary.route) {
                SummaryScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onWhatsNew = { navController.navigate(Screen.WhatsNew.route) }
                )
            }

            composable(Screen.WhatsNew.route) {
                WhatsNewScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
