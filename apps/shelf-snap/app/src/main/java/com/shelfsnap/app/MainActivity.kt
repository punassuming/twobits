package com.shelfsnap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shelfsnap.app.ui.navigation.AppNavigation
import com.shelfsnap.app.ui.theme.ShelfSnapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uiTestRoute = intent.getStringExtra(EXTRA_UI_TEST_ROUTE).takeIf { BuildConfig.DEBUG }
        val suppressUiTestDialogs = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_TEST_SUPPRESS_DIALOGS, false)
        setContent {
            ShelfSnapTheme {
                AppNavigation(
                    startDestination = uiTestRoute ?: com.shelfsnap.app.ui.navigation.Screen.Inventory.route,
                    suppressWhatsNew = suppressUiTestDialogs,
                )
            }
        }
    }

    companion object {
        const val EXTRA_UI_TEST_ROUTE = "twobits.ui_test.route"
        const val EXTRA_UI_TEST_SUPPRESS_DIALOGS = "twobits.ui_test.suppress_dialogs"
    }
}
