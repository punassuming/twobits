package dev.scrybe.android

import android.app.Application
import com.twobits.localai.DeviceDiagnostics
import com.twobits.localai.LocalInferenceMemoryGuard
import dagger.hilt.android.HiltAndroidApp
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import dev.scrybe.core.transcription.DebugLogStore
import dev.scrybe.core.transforms.DefaultProfiles
import dev.scrybe.service.recording.WaveformBackfiller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScrybeApplication : Application() {
    @Inject lateinit var transformProfileDao: TransformProfileDao

    @Inject lateinit var recordingSessionDao: RecordingSessionDao

    @Inject lateinit var preferencesDataStore: AppPreferencesDataStore

    @Inject lateinit var waveformBackfiller: WaveformBackfiller

    @Inject lateinit var debugLogStore: DebugLogStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        debugLogStore.install()
        // A device fingerprint once per launch — on-device inference crashes are heavily
        // device/chipset dependent, so a crash entry with no idea which device it happened on is
        // far harder to reproduce or triage than one timestamped next to this.
        debugLogStore.record(
            DebugLogEntry(
                timestampMs = System.currentTimeMillis(),
                type = DebugLogEntryType.AI_CALL,
                op = "app-start",
                endpoint = "device-info",
                requestSummary = DeviceDiagnostics.summary(LocalInferenceMemoryGuard.snapshot(this)?.totalMb),
                success = true,
            ),
        )
        // Captured synchronously, before any launch{} below can be delayed by the dispatcher —
        // see reconcileOrphanedTranscribingSessions()'s doc comment for why this exact ordering
        // is what makes that sweep safe.
        val appStartTimeMs = System.currentTimeMillis()
        applicationScope.launch {
            waveformBackfiller.backfillMissingWaveforms()
        }
        applicationScope.launch {
            reconcileOrphanedTranscribingSessions(appStartTimeMs)
        }
        applicationScope.launch {
            val deletedIds = preferencesDataStore.deletedDefaultProfileIds.first()
            DefaultProfiles.ALL.forEach { profile ->
                if (profile.id in deletedIds) return@forEach
                val existingProfile = transformProfileDao.getProfileById(profile.id)
                if (existingProfile == null) {
                    transformProfileDao.insertProfile(
                        TransformProfileEntity(
                            id = profile.id,
                            name = profile.name,
                            description = profile.description,
                            systemPrompt = profile.systemPrompt,
                            steps = TransformStepsCodec.encode(profile.steps),
                            providerType = profile.providerType.name,
                            isDefault = profile.isDefault,
                        ),
                    )
                } else if (existingProfile.systemPrompt == LEGACY_PROFILE_PROMPTS[profile.id]) {
                    transformProfileDao.insertProfile(
                        existingProfile.copy(
                            name = profile.name,
                            description = profile.description,
                            systemPrompt = profile.systemPrompt,
                            steps = TransformStepsCodec.encode(profile.steps),
                            providerType = profile.providerType.name,
                        ),
                    )
                }
            }
        }
    }

    /**
     * A session left at [SessionStatus.TRANSCRIBING] by a killed process (force-stop, low-memory
     * kill, native crash — see [DebugLogStore]'s own exit-reason capture for the same failure
     * class) has no live coroutine backing it in this fresh process:
     * `TranscriptionCancellationController`'s job map is populated only while
     * `SessionTranscriptionCoordinator.transcribeSession()` is actually running, and starts empty
     * on every launch. Left alone, such a session shows "Transcribing…" on the global toast
     * forever, and Cancel is a no-op against it (nothing in the map to cancel), cycling between
     * "Cancelling…" and "Transcribing…" without ever resolving — until, coincidentally, the user
     * opens that exact session's detail screen, the only other place a stuck TRANSCRIBING row
     * gets corrected today.
     *
     * This runs asynchronously on [applicationScope] — `onCreate()` returns immediately, so the
     * Activity or recording service can start a genuinely new transcription before this
     * coroutine actually gets scheduled. An unconditional "every TRANSCRIBING row" sweep would
     * then wrongly fail that live session. [appStartTimeMs] (captured synchronously in
     * `onCreate()`, before this — or any — `launch{}`) rules that out: a session this process
     * itself just started transcribing necessarily has `updatedAt >= appStartTimeMs`, since
     * nothing in this process could have touched it before that timestamp was taken, so
     * [RecordingSessionDao.updateSessionsByStatusIfStaleBefore]'s `staleBefore` cutoff excludes
     * it regardless of how delayed this sweep runs. A row genuinely orphaned by a *previous*
     * process necessarily has an older `updatedAt`, so it's still caught.
     */
    private suspend fun reconcileOrphanedTranscribingSessions(appStartTimeMs: Long) {
        recordingSessionDao.updateSessionsByStatusIfStaleBefore(
            oldStatus = SessionStatus.TRANSCRIBING.name,
            newStatus = SessionStatus.FAILED.name,
            staleBefore = appStartTimeMs,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        val LEGACY_PROFILE_PROMPTS =
            mapOf(
                "default-cleanup" to
                    "You are a helpful editor. Clean up the following dictated text by fixing punctuation, removing filler words, and improving readability. Return only the cleaned text.",
                "default-summarize" to
                    "You are a helpful assistant. Summarize the following text concisely. Return only the summary.",
                "default-action-items" to
                    "You are a helpful assistant. Extract all action items from the following text as a bulleted list. Return only the action items.",
            )
    }
}
