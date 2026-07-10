package dev.scrybe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.CustomRecordingTypeDao
import dev.scrybe.core.database.CustomRecordingTypeEntity
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.model.RecordingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RecordingTypeProfileOption(
    val id: String,
    val name: String,
)

@HiltViewModel
class RecordingTypesViewModel
    @Inject
    constructor(
        private val customRecordingTypeDao: CustomRecordingTypeDao,
        private val recordingSessionDao: RecordingSessionDao,
        transformProfileDao: TransformProfileDao,
    ) : ViewModel() {
        val types: StateFlow<List<CustomRecordingTypeEntity>> =
            customRecordingTypeDao
                .getAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val profiles: StateFlow<List<RecordingTypeProfileOption>> =
            transformProfileDao
                .getAllProfiles()
                .map { entities -> entities.map { RecordingTypeProfileOption(id = it.id, name = it.name) } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Creates ([id] = null) or updates an existing type in place (REPLACE keeps the id). */
        fun saveType(
            id: String?,
            name: String,
            iconName: String?,
            defaultProfileId: String?,
        ) {
            viewModelScope.launch {
                val existing = id?.let { customRecordingTypeDao.getById(it) }
                customRecordingTypeDao.insert(
                    CustomRecordingTypeEntity(
                        id = id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        defaultProfileId = defaultProfileId,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        iconName = iconName,
                    ),
                )
            }
        }

        /** Deletes a type; its recordings are reassigned to Journal first so none dangle. */
        fun deleteType(id: String) {
            viewModelScope.launch {
                recordingSessionDao.reassignCustomTypeToJournal(
                    customTypeId = id,
                    journalMode = RecordingMode.JOURNAL.name,
                    updatedAt = System.currentTimeMillis(),
                )
                customRecordingTypeDao.delete(id)
            }
        }
    }
