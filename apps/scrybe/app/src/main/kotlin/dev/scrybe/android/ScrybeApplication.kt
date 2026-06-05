package dev.scrybe.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.transforms.DefaultProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScrybeApplication : Application() {
    @Inject lateinit var transformProfileDao: TransformProfileDao

    @Inject lateinit var preferencesDataStore: AppPreferencesDataStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val deletedIds = preferencesDataStore.deletedDefaultProfileIds.first()
            DefaultProfiles.ALL.forEach { profile ->
                if (profile.id in deletedIds) return@forEach
                val existingProfile = transformProfileDao.getProfileById(profile.id)
                val modeValue = profile.mode?.name
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
                            mode = modeValue,
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
                            mode = modeValue,
                        ),
                    )
                } else if (existingProfile.mode != modeValue) {
                    transformProfileDao.insertProfile(existingProfile.copy(mode = modeValue))
                }
            }
        }
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
