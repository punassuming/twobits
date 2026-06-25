package com.twobits.pricedrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.twobits.pricedrop.notifications.PriceDropNotifier
import com.twobits.pricedrop.ui.navigation.AppNavigation
import com.twobits.pricedrop.ui.theme.PriceDropTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Holds a product ID delivered via notification tap; cleared after navigation.
    private val pendingProductId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        // Only read notification intent on fresh launch — not on config-change recreation.
        if (savedInstanceState == null) {
            intent
                .getLongExtra(PriceDropNotifier.EXTRA_PRODUCT_ID, -1L)
                .takeIf { it != -1L }
                ?.let { pendingProductId.value = it }
        }
        setContent {
            val notificationProductId by pendingProductId.collectAsState()
            PriceDropTheme {
                AppNavigation(
                    notificationProductId = notificationProductId,
                    onNotificationProductConsumed = { pendingProductId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent
            .getLongExtra(PriceDropNotifier.EXTRA_PRODUCT_ID, -1L)
            .takeIf { it != -1L }
            ?.let { pendingProductId.value = it }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
