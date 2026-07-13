package dev.scrybe.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.scrybe.android.ui.ScrybeApp
import dev.scrybe.android.ui.theme.ScrybeTheme
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ThemeMode
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferencesDataStore: AppPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uiTestRoute = intent.getStringExtra(EXTRA_UI_TEST_ROUTE).takeIf { BuildConfig.DEBUG }
        val suppressUiTestDialogs = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_TEST_SUPPRESS_DIALOGS, false)
        setContent {
            val themeMode by preferencesDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ScrybeTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ScrybeApp(
                        uiTestRoute = uiTestRoute,
                        suppressUiTestDialogs = suppressUiTestDialogs,
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
