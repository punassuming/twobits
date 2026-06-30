package com.shelfsnap.app.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shelfsnap.app.data.model.LocalGemmaModel
import com.shelfsnap.app.data.model.LocalMoondreamModel
import com.twobits.core.localmodels.LocalModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataStore: DataStore<Preferences>,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val modelsDir: File
            get() = File(context.getExternalFilesDir(null), "local_models").also { it.mkdirs() }

        private object Keys {
            val SELECTED_VISION_MODEL = stringPreferencesKey("local_vision_model")
            val SELECTED_LLM_MODEL = stringPreferencesKey("local_llm_model")
        }

        private val _moondreamStates =
            MutableStateFlow<Map<LocalMoondreamModel, LocalModelState>>(
                LocalMoondreamModel.entries.associateWith { resolveVisionState(it) },
            )
        val moondreamStates: StateFlow<Map<LocalMoondreamModel, LocalModelState>> =
            _moondreamStates.asStateFlow()

        private val _selectedMoondream = MutableStateFlow<LocalMoondreamModel?>(null)
        val selectedMoondream: StateFlow<LocalMoondreamModel?> = _selectedMoondream.asStateFlow()

        private val _gemmaStates =
            MutableStateFlow<Map<LocalGemmaModel, LocalModelState>>(
                LocalGemmaModel.entries.associateWith { resolveLlmState(it) },
            )
        val gemmaStates: StateFlow<Map<LocalGemmaModel, LocalModelState>> = _gemmaStates.asStateFlow()

        private val _selectedGemma = MutableStateFlow<LocalGemmaModel?>(null)
        val selectedGemma: StateFlow<LocalGemmaModel?> = _selectedGemma.asStateFlow()

        init {
            refreshStates()
            scope.launch {
                dataStore.data.map { it[Keys.SELECTED_VISION_MODEL] }.collect { name ->
                    _selectedMoondream.value = name?.let { LocalMoondreamModel.fromName(it) }
                }
            }
            scope.launch {
                dataStore.data.map { it[Keys.SELECTED_LLM_MODEL] }.collect { name ->
                    _selectedGemma.value = name?.let { LocalGemmaModel.fromName(it) }
                }
            }
        }

        private fun refreshStates() {
            _moondreamStates.value = LocalMoondreamModel.entries.associateWith { resolveVisionState(it) }
            _gemmaStates.value = LocalGemmaModel.entries.associateWith { resolveLlmState(it) }
        }

        private fun visionFile(model: LocalMoondreamModel): File? {
            val f = File(modelsDir, model.fileName)
            return if (f.exists() && f.length() > 0) f else null
        }

        private fun llmFile(model: LocalGemmaModel): File? {
            val f = File(modelsDir, model.fileName)
            return if (f.exists() && f.length() > 0) f else null
        }

        private fun resolveVisionState(model: LocalMoondreamModel): LocalModelState = visionFile(model)?.let { LocalModelState.Ready(it.absolutePath) } ?: LocalModelState.Absent

        private fun resolveLlmState(model: LocalGemmaModel): LocalModelState = llmFile(model)?.let { LocalModelState.Ready(it.absolutePath) } ?: LocalModelState.Absent

        suspend fun importMoondream(
            uri: Uri,
            model: LocalMoondreamModel,
        ) {
            if (_moondreamStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                try {
                    updateVisionState(model, LocalModelState.Acquiring(0))
                    copyFromUri(uri, File(modelsDir, model.fileName)) { progress ->
                        updateVisionState(model, LocalModelState.Acquiring(progress))
                    }
                    updateVisionState(model, resolveVisionState(model))
                } catch (e: Exception) {
                    updateVisionState(model, LocalModelState.Error(e.message ?: "Import failed"))
                }
            }
        }

        suspend fun importGemma(
            uri: Uri,
            model: LocalGemmaModel,
        ) {
            if (_gemmaStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                try {
                    updateLlmState(model, LocalModelState.Acquiring(0))
                    copyFromUri(uri, File(modelsDir, model.fileName)) { progress ->
                        updateLlmState(model, LocalModelState.Acquiring(progress))
                    }
                    updateLlmState(model, resolveLlmState(model))
                } catch (e: Exception) {
                    updateLlmState(model, LocalModelState.Error(e.message ?: "Import failed"))
                }
            }
        }

        fun deleteMoondream(model: LocalMoondreamModel) {
            File(modelsDir, model.fileName).delete()
            if (_selectedMoondream.value == model) {
                scope.launch { dataStore.edit { it.remove(Keys.SELECTED_VISION_MODEL) } }
            }
            updateVisionState(model, LocalModelState.Absent)
        }

        fun deleteGemma(model: LocalGemmaModel) {
            File(modelsDir, model.fileName).delete()
            if (_selectedGemma.value == model) {
                scope.launch { dataStore.edit { it.remove(Keys.SELECTED_LLM_MODEL) } }
            }
            updateLlmState(model, LocalModelState.Absent)
        }

        fun selectMoondream(model: LocalMoondreamModel) {
            scope.launch { dataStore.edit { it[Keys.SELECTED_VISION_MODEL] = model.name } }
        }

        fun selectGemma(model: LocalGemmaModel) {
            scope.launch { dataStore.edit { it[Keys.SELECTED_LLM_MODEL] = model.name } }
        }

        private fun updateVisionState(
            model: LocalMoondreamModel,
            state: LocalModelState,
        ) {
            _moondreamStates.value = _moondreamStates.value + (model to state)
        }

        private fun updateLlmState(
            model: LocalGemmaModel,
            state: LocalModelState,
        ) {
            _gemmaStates.value = _gemmaStates.value + (model to state)
        }

        private fun copyFromUri(
            uri: Uri,
            dest: File,
            onProgress: (Int) -> Unit,
        ) {
            val sizeBytes =
                context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { it.statSize } ?: -1L
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(65_536)
                    var bytesRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (sizeBytes > 0) onProgress(((bytesRead * 100) / sizeBytes).toInt())
                    }
                }
            } ?: throw IOException("Could not open selected file")
        }
    }
