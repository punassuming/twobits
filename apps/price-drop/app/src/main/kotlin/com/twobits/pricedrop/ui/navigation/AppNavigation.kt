package com.twobits.pricedrop.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twobits.debuglogui.CrashWarningViewModel
import com.twobits.debuglogui.DebugLogScreen
import com.twobits.design.components.AppWhatsNewDialog
import com.twobits.design.components.ProgressFooter
import com.twobits.pricedrop.ui.ask.AskProgressViewModel
import com.twobits.pricedrop.ui.ask.AskScreen
import com.twobits.pricedrop.ui.barcode.BarcodeScanScreen
import com.twobits.pricedrop.ui.drops.DropsScreen
import com.twobits.pricedrop.ui.onboarding.OnboardingScreen
import com.twobits.pricedrop.ui.onboarding.OnboardingViewModel
import com.twobits.pricedrop.ui.pro.ProScreen
import com.twobits.pricedrop.ui.product.PriceCheckProgressViewModel
import com.twobits.pricedrop.ui.product.ProductDetailScreen
import com.twobits.pricedrop.ui.search.SearchScreen
import com.twobits.pricedrop.ui.settings.AIConfigScreen
import com.twobits.pricedrop.ui.settings.ServicesScreen
import com.twobits.pricedrop.ui.settings.SettingsScreen
import com.twobits.pricedrop.ui.watch.WatchScreen
import com.twobits.pricedrop.ui.whatsnew.WhatsNewPopupViewModel
import com.twobits.pricedrop.ui.whatsnew.WhatsNewScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    notificationProductId: Long? = null,
    onNotificationProductConsumed: () -> Unit = {},
    uiTestStartDestination: String? = null,
    suppressWhatsNew: Boolean = false,
) {
    val onboardingComplete by onboardingViewModel.completed.collectAsState()
    if (onboardingComplete == null && uiTestStartDestination == null) {
        // Hold the start destination until the first-run flag has loaded.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Navigate to a product when the app is launched (or resumed) from an OS notification.
    // Wait for onboarding state so we don't navigate before the graph is ready.
    LaunchedEffect(notificationProductId, onboardingComplete) {
        if (notificationProductId != null && onboardingComplete == true) {
            navController.navigate(Screen.ProductDetail.createRoute(notificationProductId)) {
                launchSingleTop = true
            }
            onNotificationProductConsumed()
        }
    }

    val whatsNewViewModel: WhatsNewPopupViewModel = hiltViewModel()
    val whatsNewState by whatsNewViewModel.uiState.collectAsState()
    val askProgressViewModel: AskProgressViewModel = hiltViewModel()
    val askProgressState by askProgressViewModel.state.collectAsState()
    val priceCheckProgressViewModel: PriceCheckProgressViewModel = hiltViewModel()
    val priceCheckProgressState by priceCheckProgressViewModel.state.collectAsState()
    val crashWarningViewModel: CrashWarningViewModel = hiltViewModel()
    val staleStartWarning by crashWarningViewModel.staleStartWarning.collectAsState()
    DisposableEffect(navController) {
        val listener =
            NavController.OnDestinationChangedListener { _, destination, _ ->
                crashWarningViewModel.recordBreadcrumb("nav:${destination.route}")
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val startDestination =
        uiTestStartDestination
            ?: if (onboardingComplete == true) Screen.Watch.route else Screen.Onboarding.route
    val slideEnter = slideInHorizontally { it } + fadeIn()
    val slideExit = slideOutHorizontally { -it / 3 } + fadeOut()
    val popSlideEnter = slideInHorizontally { -it / 3 } + fadeIn()
    val popSlideExit = slideOutHorizontally { it } + fadeOut()
    // contentWindowInsets is zeroed out deliberately: individual screens under NavHost already
    // manage their own top/side system-bar insets (there's no topBar here for Scaffold to reserve
    // space for), so this Scaffold's only job is reserving bottom space for the footer — letting
    // it also fold system-bar insets into innerPadding would double up with what each screen
    // already applies on its own.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // AnimatedVisibility inside each footer collapses it to zero height when not showing,
            // so innerPadding's bottom value shrinks back to zero the moment both disappear, and
            // grows to whichever's measured height while one is visible — this is what actually
            // reduces the content area and makes a footer come up from the real bottom edge,
            // matching Scrybe's and Shelf Snap's own nav-root footers. No navigationBarsPadding()
            // here — ProgressFooter applies gesture-nav clearance to its own inner content
            // instead, so its card can reach the true bottom edge unconditionally. See its own
            // doc comment for why.
            //
            // Two independent footers, not one merged state, for the same reason Shelf Snap's
            // nav root keeps its two separate: Ask and a price-check refresh are unrelated
            // features that can genuinely run at once.
            Column {
                ProgressFooter(
                    visible = askProgressState != null,
                    primaryText = askProgressState?.label ?: "",
                    startedAtMs = askProgressState?.startedAtMs,
                    onCancel = askProgressViewModel::cancel,
                    onViewDetails = { navController.navigate(Screen.Ask.route) },
                )
                ProgressFooter(
                    visible = priceCheckProgressState != null,
                    primaryText = priceCheckProgressState?.step ?: "",
                    startedAtMs = priceCheckProgressState?.startedAtMs,
                    onCancel = priceCheckProgressViewModel::cancel,
                    onViewDetails = {
                        priceCheckProgressState?.productId?.let {
                            navController.navigate(Screen.ProductDetail.createRoute(it))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { popSlideEnter },
                popExitTransition = { popSlideExit },
            ) {
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
                        onNavigateToServices = { navController.navigate(Screen.Services.route) },
                        onNavigateToWhatsNew = { navController.navigate(Screen.WhatsNew.route) },
                        onNavigateToDebugLog = { navController.navigate(Screen.DebugLog.route) },
                    )
                }
                composable(Screen.AiConfig.route) {
                    AIConfigScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Services.route) {
                    ServicesScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.DebugLog.route) {
                    DebugLogScreen(onBack = { navController.popBackStack() })
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
                            onboardingViewModel.markComplete()
                            navController.navigate(Screen.Watch.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        },
                    )
                }
            }
            if (onboardingComplete == true && whatsNewState.isVisible && !suppressWhatsNew) {
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
        } // Box
    } // Scaffold
}

/**
 * [DebugLogStore.staleStartWarning][com.twobits.debuglog.DebugLogStore.staleStartWarning]
 * surfaced as a one-time dialog — a native crash (a bad model file, a LiteRT-LM abort) has no
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
        title = { Text("PriceDrop closed unexpectedly") },
        text = {
            Text(
                "It looks like the app closed while running \"$opLabel\" last time — likely a crash " +
                    "in on-device Ask. Check the Debug Log for details.",
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
