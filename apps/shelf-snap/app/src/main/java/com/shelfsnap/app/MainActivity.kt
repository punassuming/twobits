package com.shelfsnap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.ui.navigation.AppNavigation
import com.shelfsnap.app.ui.theme.ShelfSnapTheme
import com.twobits.design.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var itemRepository: ItemRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uiTestRoute = intent.getStringExtra(EXTRA_UI_TEST_ROUTE).takeIf { BuildConfig.DEBUG }
        val suppressUiTestDialogs = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_TEST_SUPPRESS_DIALOGS, false)
        val themeModeFlow = itemRepository.observeThemeMode()
        setContent {
            val themeMode by themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            ShelfSnapTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(
                        uiTestStartDestination = uiTestRoute,
                        suppressWhatsNew = suppressUiTestDialogs,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_UI_TEST_ROUTE = "twobits.ui_test.route"
        const val EXTRA_UI_TEST_SUPPRESS_DIALOGS = "twobits.ui_test.suppress_dialogs"
    }
}
