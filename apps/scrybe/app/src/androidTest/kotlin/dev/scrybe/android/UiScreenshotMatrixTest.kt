package dev.scrybe.android

import android.content.Intent
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.scrybe.core.database.AppDatabase
import dev.scrybe.core.database.RecordingSessionEntity
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
        shell("pm grant $PACKAGE android.permission.RECORD_AUDIO")
        shell("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
    }

    @Test
    fun captureMatrix() {
        capture("capture", "capture-empty")
        seedSession()
        capture("capture", "history-seeded")
        capture("session_detail/$FIXTURE_SESSION_ID", "session-detail")
        capture("profiles", "profiles")
        capture("tasks", "tasks")
        capture("file_manager", "file-manager")
        capture("settings", "settings")
        capture("ai_config", "ai-config")
        capture("pro", "pro")
        capture("recording_types", "recording-types")
        capture("people", "people")
        capture("whats_new", "whats-new")
    }

    private fun seedSession() {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, "scrybe-db").build()
        runBlocking {
            database.recordingSessionDao().insertSession(
                RecordingSessionEntity(
                    id = FIXTURE_SESSION_ID,
                    title = "Product planning interview",
                    tags = "research,roadmap",
                    audioFilePath = "/data/local/tmp/twobits-fixture.m4a",
                    durationMs = 754_000,
                    fileSizeBytes = 4_200_000,
                    audioFormat = "AAC",
                    sampleRateHz = 44_100,
                    encodingBitRate = 128_000,
                    channelCount = 1,
                    waveformSamples = "0.1,0.4,0.7,0.3,0.8,0.2",
                    status = "TRANSCRIBED",
                    isArchived = false,
                    estimatedTranscriptionCostUsd = 0.08,
                    locationLabel = "Design studio",
                    createdAt = FIXED_TIME,
                    updatedAt = FIXED_TIME,
                ),
            )
        }
        database.close()
    }

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
            "Scrybe did not render route $route"
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
        const val PACKAGE = "dev.scrybe.android"
        const val FIXTURE_SESSION_ID = "ui-fixture-session"
        const val FIXED_TIME = 1_735_689_600_000L
        const val SCREEN_TIMEOUT_MS = 10_000L
        const val IDLE_TIMEOUT_MS = 1_500L
        const val SETTLE_DELAY_MS = 750L
    }
}
