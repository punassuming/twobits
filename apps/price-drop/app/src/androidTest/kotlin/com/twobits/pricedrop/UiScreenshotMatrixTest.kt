package com.twobits.pricedrop

import android.content.Intent
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.twobits.pricedrop.data.local.PriceDropDatabase
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.model.WatchedProduct
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class UiScreenshotMatrixTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val theme = InstrumentationRegistry.getArguments().getString("theme") ?: "light"
    private val outputDir by lazy { requireNotNull(context.getExternalFilesDir("ui-test/$theme")) }

    @Before
    fun prepareDevice() {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        normalizeLocale()
        shell("settings put global window_animation_scale 0")
        shell("settings put global transition_animation_scale 0")
        shell("settings put global animator_duration_scale 0")
        shell("wm size 1080x2400")
        shell("wm density 420")
        shell("settings put system font_scale 1.0")
        shell("cmd alarm set-timezone America/Phoenix")
        shell("pm grant $PACKAGE android.permission.CAMERA")
        shell("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
    }

    @Test
    fun captureMatrix() {
        capture("watch", "watch-empty")
        seedProduct()
        capture("watch", "watch-seeded", expectedText = "Noise-cancelling headphones")
        capture("drops", "drops")
        capture("search", "search")
        capture("ask", "ask")
        capture("product/$FIXTURE_PRODUCT_ID", "product-detail")
        capture("barcode_scan", "barcode")
        capture("settings", "settings")
        capture("ai_config", "ai-config")
        capture("pro", "pro")
        capture("onboarding", "onboarding")
        capture("whats_new", "whats-new")
    }

    private fun seedProduct() {
        val database =
            Room
                .databaseBuilder(context, PriceDropDatabase::class.java, "pricedrop.db")
                .enableMultiInstanceInvalidation()
                .build()
        runBlocking {
            database.watchedProductDao().insert(
                WatchedProduct(
                    id = FIXTURE_PRODUCT_ID,
                    title = "Noise-cancelling headphones",
                    brand = "Example Audio",
                    category = "Electronics",
                    currentPrice = 249.99,
                    targetPrice = 220.0,
                    imageUrl = "",
                    productUrl = "https://example.invalid/headphones",
                    retailers = "Example Store",
                    shipping = 0.0,
                    seller = "Example Store",
                    source = "fixture",
                    confidence = 98,
                    addedAt = FIXED_TIME,
                    lastCheckedAt = 0L,
                    trackedHigh = 299.99,
                    trackedLow = 239.99,
                    trackedAvg = 269.99,
                ),
            )
            database.dropDao().insert(
                Drop(
                    id = 1,
                    productId = FIXTURE_PRODUCT_ID,
                    type = "price_drop",
                    oldPrice = 279.99,
                    newPrice = 249.99,
                    retailer = "Example Store",
                    detectedAt = FIXED_TIME,
                ),
            )
        }
        database.close()
    }

    private fun capture(
        route: String,
        name: String,
        expectedText: String? = null,
    ) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(MainActivity.EXTRA_UI_TEST_ROUTE, route)
                .putExtra(MainActivity.EXTRA_UI_TEST_SUPPRESS_DIALOGS, true),
        )
        check(device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), SCREEN_TIMEOUT_MS)) {
            "PriceDrop did not render route $route"
        }
        if (expectedText != null) {
            check(device.wait(Until.hasObject(By.text(expectedText)), SCREEN_TIMEOUT_MS)) {
                "PriceDrop did not render fixture text '$expectedText' on route $route"
            }
        }
        device.waitForIdle(IDLE_TIMEOUT_MS)
        Thread.sleep(SETTLE_DELAY_MS)
        check(device.takeScreenshot(File(outputDir, "$name.png"), 1.0f, 100)) {
            "Could not capture $name"
        }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    @Suppress("DEPRECATION")
    private fun normalizeLocale() {
        Locale.setDefault(Locale.US)
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.US) }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    private companion object {
        const val PACKAGE = "com.twobits.pricedrop"
        const val FIXTURE_PRODUCT_ID = 1L
        const val FIXED_TIME = 1_735_689_600_000L
        const val SCREEN_TIMEOUT_MS = 10_000L
        const val IDLE_TIMEOUT_MS = 1_500L
        const val SETTLE_DELAY_MS = 750L
    }
}
