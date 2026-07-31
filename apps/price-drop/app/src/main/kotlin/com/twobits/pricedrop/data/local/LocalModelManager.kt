package com.twobits.pricedrop.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.localai.ModelDownloader
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
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks PriceDrop's on-device Gemma model, used only by the Ask assistant's Local mode
 * (see `LocalAskSession`). Simpler than Scrybe's/Shelf Snap's equivalents — PriceDrop tracks
 * only [LocalLlmModel], no Whisper/vision model family.
 */
@Singleton
class LocalModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val dataStore: DataStore<Preferences>,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val modelsDir: File
            get() = File(context.getExternalFilesDir(null), "local_models").also { it.mkdirs() }

        private object Keys {
            val SELECTED_LLM_MODEL = stringPreferencesKey("local_llm_model")
        }

        private val _llmStates =
            MutableStateFlow<Map<LocalLlmModel, LocalModelState>>(
                LocalLlmModel.entries.associateWith { resolveLlmState(it) },
            )
        val llmStates: StateFlow<Map<LocalLlmModel, LocalModelState>> = _llmStates.asStateFlow()

        private val _selectedLlm = MutableStateFlow<LocalLlmModel?>(null)
        val selectedLlm: StateFlow<LocalLlmModel?> = _selectedLlm.asStateFlow()

        init {
            // A .part file nobody has resumed in days is more likely abandoned than still
            // wanted, and unlike a Ready model file it's disk usage the UI never surfaces, so
            // it can't otherwise be noticed and cleaned up by hand.
            ModelDownloader.cleanupStalePartialFiles(modelsDir)
            refreshStates()
            scope.launch {
                dataStore.data.map { it[Keys.SELECTED_LLM_MODEL] }.collect { name ->
                    _selectedLlm.value = name?.let { LocalLlmModel.fromName(it) }
                }
            }
        }

        private fun refreshStates() {
            _llmStates.value = LocalLlmModel.entries.associateWith { resolveLlmState(it) }
        }

        fun llmFile(model: LocalLlmModel): File? {
            val f = File(modelsDir, model.fileName)
            return if (f.exists() && f.length() > 0) f else null
        }

        fun anyLlmReady(): LocalLlmModel? = LocalLlmModel.entries.firstOrNull { llmFile(it) != null }

        private fun resolveLlmState(model: LocalLlmModel): LocalModelState =
            llmFile(model)?.let { LocalModelState.Ready(it.absolutePath) } ?: LocalModelState.Absent

        suspend fun downloadLlm(model: LocalLlmModel) {
            if (_llmStates.value[model] is LocalModelState.Acquiring) return
            withContext(Dispatchers.IO) {
                val destFile = File(modelsDir, model.fileName)
                try {
                    updateLlmState(model, LocalModelState.Acquiring(0))
                    ModelDownloader.downloadFile(
                        okHttpClient = okHttpClient,
                        url = model.downloadUrl,
                        dest = destFile,
                        expectedSha256 = model.sha256,
                    ) { progress -> updateLlmState(model, LocalModelState.Acquiring(progress)) }
                    updateLlmState(model, resolveLlmState(model))
                } catch (e: Exception) {
                    // The in-progress .part file is deliberately left alone here — downloadFile
                    // already discarded it for unrecoverable failures (bad checksum, HTTP 4xx,
                    // out of space) and otherwise preserved it so the next attempt (this button,
                    // or an automatic retry) resumes instead of starting over.
                    updateLlmState(model, LocalModelState.Error(e.message ?: "Download failed"))
                }
            }
        }

        fun deleteLlm(model: LocalLlmModel) {
            val destFile = File(modelsDir, model.fileName)
            destFile.delete()
            ModelDownloader.deletePartialFile(destFile)
            if (_selectedLlm.value == model) {
                scope.launch { dataStore.edit { it.remove(Keys.SELECTED_LLM_MODEL) } }
            }
            updateLlmState(model, LocalModelState.Absent)
        }

        fun selectLlm(model: LocalLlmModel) {
            scope.launch { dataStore.edit { it[Keys.SELECTED_LLM_MODEL] = model.name } }
        }

        private fun updateLlmState(
            model: LocalLlmModel,
            state: LocalModelState,
        ) {
            _llmStates.value = _llmStates.value + (model to state)
        }
    }
