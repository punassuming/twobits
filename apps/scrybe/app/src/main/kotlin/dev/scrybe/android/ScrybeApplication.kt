package dev.scrybe.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.SessionStatus
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
        applicationScope.launch {
            waveformBackfiller.backfillMissingWaveforms()
        }
        applicationScope.launch {
            reconcileOrphanedTranscribingSessions()
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
     * Called once per process start, before any screen is reachable: any `TRANSCRIBING` row seen
     * here is unconditionally stale, because this process has never yet run a transcription of
     * its own — there is no race with a genuinely in-flight transcription to guard against.
     */
    private suspend fun reconcileOrphanedTranscribingSessions() {
        recordingSessionDao.updateSessionsByStatus(
            oldStatus = SessionStatus.TRANSCRIBING.name,
            newStatus = SessionStatus.FAILED.name,
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
