package com.twobits.pricedrop.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twobits.pricedrop.ui.ask.AskScreen
import com.twobits.pricedrop.ui.barcode.BarcodeScanScreen
import com.twobits.pricedrop.ui.drops.DropsScreen
import com.twobits.pricedrop.ui.onboarding.OnboardingScreen
import com.twobits.pricedrop.ui.product.ProductDetailScreen
import com.twobits.pricedrop.ui.pro.ProScreen
import com.twobits.pricedrop.ui.search.SearchScreen
import com.twobits.pricedrop.ui.settings.AIConfigScreen
import com.twobits.pricedrop.ui.settings.SettingsScreen
import com.twobits.pricedrop.ui.watch.WatchScreen
import com.twobits.pricedrop.ui.whatsnew.WhatsNewScreen

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Watch.route) {
        composable(Screen.Watch.route) {
            WatchScreen(
                onNavigateToProduct = { id -> navController.navigate(Screen.ProductDetail.createRoute(id)) },
                onNavigateToDrops = { navController.navigate(Screen.Drops.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToBarcode = { navController.navigate(Screen.BarcodeScan.route) },
                onNavigateToAsk = { navController.navigate(Screen.Ask.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Drops.route) {
            DropsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { id -> navController.navigate(Screen.ProductDetail.createRoute(id)) },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { id -> navController.navigate(Screen.ProductDetail.createRoute(id)) },
            )
        }
        composable(Screen.BarcodeScan.route) {
            BarcodeScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToProduct = { id -> navController.navigate(Screen.ProductDetail.createRoute(id)) },
            )
        }
        composable(Screen.Ask.route) {
            AskScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(navArgument("productId") { type = NavType.LongType }),
        ) { back ->
            ProductDetailScreen(
                productId = back.arguments?.getLong("productId") ?: return@composable,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPro = { navController.navigate(Screen.Pro.route) },
                onNavigateToAiConfig = { navController.navigate(Screen.AiConfig.route) },
                onNavigateToWhatsNew = { navController.navigate(Screen.WhatsNew.route) },
            )
        }
        composable(Screen.AiConfig.route) {
            AIConfigScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Pro.route) {
            ProScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToByok = { navController.navigate(Screen.AiConfig.route) },
            )
        }
        composable(Screen.WhatsNew.route) {
            WhatsNewScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Watch.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
    }
}
