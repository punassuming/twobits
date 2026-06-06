package com.shelfsnap.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shelfsnap.app.ui.camera.CameraScreen
import com.shelfsnap.app.ui.inventory.InventoryScreen
import com.shelfsnap.app.ui.itemdetail.ItemDetailScreen
import com.shelfsnap.app.ui.settings.SettingsScreen
import com.shelfsnap.app.ui.whatsnew.SSWhatsNewUiState
import com.shelfsnap.app.ui.whatsnew.WhatsNewScreen
import com.shelfsnap.app.ui.whatsnew.WhatsNewViewModel
import com.shelfsnap.app.ui.summary.SummaryScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val whatsNewViewModel: WhatsNewViewModel = hiltViewModel()
    val whatsNewState by whatsNewViewModel.uiState.collectAsState()

    Box {
    NavHost(
        navController = navController,
        startDestination = Screen.Inventory.route,
        modifier = Modifier,
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
                onWhatsNew = { navController.navigate(Screen.WhatsNew.route) },
                onAiConfig = { navController.navigate(Screen.AiConfig.route) }
            )
        }

        composable(Screen.WhatsNew.route) {
            WhatsNewScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AiConfig.route) {
            com.shelfsnap.app.ui.settings.AIConfigScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
    if (whatsNewState.isVisible) {
        SSWhatsNewDialog(state = whatsNewState, onDismiss = whatsNewViewModel::dismiss)
    }
    } // Box
}

@Composable
private fun SSWhatsNewDialog(
    state: SSWhatsNewUiState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.notes.forEach { note ->
                    Text(
                        text = "• $note",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(state.confirmLabel)
            }
        },
    )
}
