package dev.scrybe.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.transforms.DefaultProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScrybeApplication : Application() {

    @Inject lateinit var transformProfileDao: TransformProfileDao

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            if (transformProfileDao.getDefaultProfile() == null) {
                DefaultProfiles.ALL.forEach { profile ->
                    transformProfileDao.insertProfile(
                        TransformProfileEntity(
                            id = profile.id,
                            name = profile.name,
                            description = profile.description,
                            systemPrompt = profile.systemPrompt,
                            providerType = profile.providerType.name,
                            isDefault = profile.isDefault,
                        )
                    )
                }
            }
        }
    }
}
