package com.shelfsnap.app

import android.content.Intent
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.shelfsnap.app.data.local.AppDatabase
import com.shelfsnap.app.data.local.ItemEntity
import com.shelfsnap.app.data.model.Citation
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.MarketComp
import com.shelfsnap.app.data.model.MarketQuery
import com.shelfsnap.app.data.model.MarketResearch
import com.shelfsnap.app.data.model.MarketResearchDebug
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
    }

    @Test
    fun captureMatrix() {
        capture("inventory", "inventory-empty")
        seedItem()
        capture("inventory", "inventory-seeded")
        capture("camera?itemId=-1", "camera")
        capture("item_detail/$FIXTURE_ITEM_ID", "item-detail")
        capture("market_research/$FIXTURE_ITEM_ID", "market-research")
        capture("listing_summary/$FIXTURE_ITEM_ID", "listing-summary")
        capture("summary", "summary")
        capture("settings", "settings")
        capture("ai_config", "ai-config")
        capture("pro", "pro")
        capture("whats_new", "whats-new")
    }

    private fun seedItem() {
        val database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, "shelf_snap.db")
                .build()
        runBlocking {
            database.itemDao().insert(
                ItemEntity(
                    id = FIXTURE_ITEM_ID,
                    category = "Audio",
                    description = "Walnut bookshelf speakers with light cosmetic wear.",
                    condition = Condition.GOOD,
                    estimatedValue = 189.0,
                    confidencePercent = 92,
                    isDraft = false,
                    brand = "Kanto",
                    model = "YU4",
                    color = "Walnut",
                    quantity = 1,
                    originalPrice = 399.0,
                    tags = listOf("audio", "speakers", "walnut"),
                    createdAt = FIXED_TIME,
                    updatedAt = FIXED_TIME,
                    title = "Kanto YU4 speakers",
                    marketResearch = fixtureMarketResearch(),
                ),
            )
        }
        database.close()
    }

    private fun fixtureMarketResearch(): MarketResearch =
        MarketResearch(
            comps =
                listOf(
                    MarketComp(
                        platformKey = "ebay",
                        title = "Kanto YU4 powered speakers - walnut",
                        price = 184.0,
                        sold = true,
                        date = "Completed sale",
                        sourceUrl = "https://www.ebay.com/itm/fixture-184",
                    ),
                    MarketComp(
                        platformKey = "ebay",
                        title = "Kanto YU4 bookshelf speakers with stands",
                        price = 212.0,
                        sold = true,
                        date = "Completed sale",
                        sourceUrl = "https://www.ebay.com/itm/fixture-212",
                    ),
                ),
            suggestedPrices = mapOf("ebay" to 205.0, "mercari" to 198.0),
            averageSoldPrice = 198.0,
            lowPrice = 184.0,
            highPrice = 212.0,
            confidencePercent = 58,
            citations =
                listOf(
                    Citation("Kanto YU4 powered speakers", "https://www.ebay.com/itm/fixture-184"),
                    Citation("Kanto YU4 speakers with stands", "https://www.ebay.com/itm/fixture-212"),
                ),
            retrievedAt = FIXED_TIME,
            searchProviderKey = "serper",
            searchResultCount = 12,
            debug =
                MarketResearchDebug(
                    queries =
                        listOf(
                            MarketQuery("Serper.dev", "Kanto YU4 used speakers price", 8),
                            MarketQuery("SearchAPI.io", "Kanto YU4 site:ebay.com/itm sold", 8),
                        ),
                    searchMs = 840,
                    synthesisMs = 1_120,
                    totalMs = 1_960,
                ),
        )

    private fun capture(
        route: String,
        name: String,
    ) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(MainActivity.EXTRA_UI_TEST_ROUTE, route)
                .putExtra(MainActivity.EXTRA_UI_TEST_SUPPRESS_DIALOGS, true),
        )
        check(device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), SCREEN_TIMEOUT_MS)) {
            "Shelf Snap did not render route $route"
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
        const val PACKAGE = "com.shelfsnap.app"
        const val FIXTURE_ITEM_ID = 1L
        const val FIXED_TIME = 1_735_689_600_000L
        const val SCREEN_TIMEOUT_MS = 10_000L
        const val IDLE_TIMEOUT_MS = 1_500L
        const val SETTLE_DELAY_MS = 750L
    }
}
