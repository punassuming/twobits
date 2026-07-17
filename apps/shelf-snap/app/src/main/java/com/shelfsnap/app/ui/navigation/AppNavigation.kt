package com.shelfsnap.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shelfsnap.app.ui.camera.CameraScreen
import com.shelfsnap.app.ui.inventory.InventoryScreen
import com.shelfsnap.app.ui.itemdetail.ItemDetailScreen
import com.shelfsnap.app.ui.itemdetail.ListingSummaryScreen
import com.shelfsnap.app.ui.itemdetail.MarketResearchScreen
import com.shelfsnap.app.ui.onboarding.OnboardingScreen
import com.shelfsnap.app.ui.onboarding.OnboardingViewModel
import com.shelfsnap.app.ui.settings.ProScreen
import com.shelfsnap.app.ui.settings.SettingsScreen
import com.shelfsnap.app.ui.summary.SummaryScreen
import com.shelfsnap.app.ui.whatsnew.WhatsNewScreen
import com.shelfsnap.app.ui.whatsnew.WhatsNewViewModel
import com.twobits.design.components.AppWhatsNewDialog

@Composable
fun AppNavigation(
    uiTestStartDestination: String? = null,
    suppressWhatsNew: Boolean = false,
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onboardingComplete by onboardingViewModel.completed.collectAsState()
    if (onboardingComplete == null && uiTestStartDestination == null) {
        // Hold the start destination until the first-run flag has loaded.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val startDestination =
        uiTestStartDestination
            ?: if (onboardingComplete == true) Screen.Inventory.route else Screen.Onboarding.route
    val navController = rememberNavController()
    val whatsNewViewModel: WhatsNewViewModel = hiltViewModel()
    val whatsNewState by whatsNewViewModel.uiState.collectAsState()
    val slideEnter = slideInHorizontally { it } + fadeIn()
    val slideExit = slideOutHorizontally { -it / 3 } + fadeOut()
    val popSlideEnter = slideInHorizontally { -it / 3 } + fadeIn()
    val popSlideExit = slideOutHorizontally { it } + fadeOut()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { slideEnter },
            exitTransition = { slideExit },
            popEnterTransition = { popSlideEnter },
            popExitTransition = { popSlideExit },
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinish = {
                        onboardingViewModel.markComplete()
                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(Screen.Inventory.route) {
                InventoryScreen(
                    onAddItem = { navController.navigate(Screen.Camera.createRoute()) },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onSummaryClick = { navController.navigate(Screen.Summary.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }

            composable(
                route = Screen.Camera.route,
                arguments =
                    listOf(
                        navArgument("itemId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
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
                    itemId = cameraItemId,
                )
            }

            composable(
                route = Screen.ItemDetail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                val openListTab by backStackEntry.savedStateHandle
                    .getStateFlow("open_list_tab", false)
                    .collectAsState()
                ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(Screen.Inventory.route) { inclusive = false }
                        }
                    },
                    onAddPhoto = { navController.navigate(Screen.Camera.createRoute(itemId)) },
                    onNavigateToMarketResearch = {
                        navController.navigate(Screen.MarketResearch.createRoute(itemId))
                    },
                    onNavigateToListingSummary = {
                        navController.navigate(Screen.ListingSummary.createRoute(itemId))
                    },
                    openListTabRequested = openListTab,
                    onOpenListTabConsumed = { backStackEntry.savedStateHandle["open_list_tab"] = false },
                )
            }

            composable(
                route = Screen.MarketResearch.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                MarketResearchScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.ListingSummary.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                ListingSummaryScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onGoToList = {
                        try {
                            navController
                                .getBackStackEntry(Screen.ItemDetail.route)
                                .savedStateHandle["open_list_tab"] = true
                        } catch (_: IllegalArgumentException) {
                        }
                        navController.popBackStack()
                    },
                )
            }

            composable(Screen.Pro.route) {
                ProScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Summary.route) {
                SummaryScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onWhatsNew = { navController.navigate(Screen.WhatsNew.route) },
                    onAiConfig = { navController.navigate(Screen.AiConfig.route) },
                    onNavigateToPro = { navController.navigate(Screen.Pro.route) },
                )
            }

            composable(Screen.WhatsNew.route) {
                WhatsNewScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.AiConfig.route) {
                com.shelfsnap.app.ui.settings.AIConfigScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        if (whatsNewState.isVisible && !suppressWhatsNew) {
            AppWhatsNewDialog(
                title = whatsNewState.title,
                categories = whatsNewState.categories,
                confirmLabel = whatsNewState.confirmLabel,
                onDismiss = whatsNewViewModel::dismiss,
                onViewHistory = { navController.navigate(Screen.WhatsNew.route) },
            )
        }
    } // Box
}
