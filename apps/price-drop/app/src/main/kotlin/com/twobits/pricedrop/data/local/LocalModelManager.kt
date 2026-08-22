package com.twobits.pricedrop.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import com.twobits.localai.LlmDownloadSource
import com.twobits.localai.LlmModelDownloadCoordinator
import com.twobits.localai.ModelDownloadDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks PriceDrop's on-device Gemma model, used only by the Ask assistant's Local mode
 * (see `LocalAskSession`). Simpler than Scrybe's/Shelf Snap's equivalents — PriceDrop tracks
 * only [LocalLlmModel], no Whisper/vision model family.
 *
 * The actual download/state-tracking logic lives in [LlmModelDownloadCoordinator], shared with
 * Scrybe and Shelf Snap's equivalents — this class only owns what's genuinely app-specific:
 * which model is *selected*.
 */
@Singleton
class LocalModelManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        private val dataStore: DataStore<Preferences>,
        private val debugLogStore: DebugLogStore,
    ) : LlmDownloadSource {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val coordinator =
            LlmModelDownloadCoordinator(
                modelsDir = File(context.getExternalFilesDir(null), "local_models").also { it.mkdirs() },
                okHttpClient = okHttpClient,
                diagnostics =
                    ModelDownloadDiagnostics { model, op, success, message, stackTraceText, durationMs ->
                        debugLogStore.record(
                            DebugLogEntry(
                                timestampMs = System.currentTimeMillis(),
                                type = DebugLogEntryType.SERVICE_CALL,
                                op = op,
                                endpoint = model.downloadUrl,
                                model = model.fileName,
                                success = success,
                                responseSnippet = message,
                                durationMs = durationMs,
                                stackTrace = stackTraceText,
                            ),
                        )
                    },
            )

        private object Keys {
            val SELECTED_LLM_MODEL = stringPreferencesKey("local_llm_model")
        }

        override val llmStates: StateFlow<Map<LocalLlmModel, LocalModelState>> = coordinator.states

        private val _selectedLlm = MutableStateFlow<LocalLlmModel?>(null)
        val selectedLlm: StateFlow<LocalLlmModel?> = _selectedLlm.asStateFlow()

        init {
            scope.launch {
                dataStore.data.map { it[Keys.SELECTED_LLM_MODEL] }.collect { name ->
                    _selectedLlm.value = name?.let { LocalLlmModel.fromName(it) }
                }
            }
        }

        fun llmFile(model: LocalLlmModel): File? = coordinator.file(model)

        fun anyLlmReady(): LocalLlmModel? = coordinator.anyReady()

        override suspend fun downloadLlm(model: LocalLlmModel) = coordinator.download(model)

        suspend fun importLlm(
            model: LocalLlmModel,
            source: InputStream,
        ): Result<Unit> = coordinator.importFrom(model, source)

        fun deleteLlm(model: LocalLlmModel) {
            coordinator.delete(model)
            if (_selectedLlm.value == model) {
                scope.launch { dataStore.edit { it.remove(Keys.SELECTED_LLM_MODEL) } }
            }
        }

        fun selectLlm(model: LocalLlmModel) {
            scope.launch { dataStore.edit { it[Keys.SELECTED_LLM_MODEL] = model.name } }
        }

        fun orphanedFileDetails(): List<Pair<String, Long>> = coordinator.orphanedFileDetails()

        fun deleteOrphanedFiles(): Long = coordinator.deleteOrphanedFiles()

        fun installedFileDetails(): List<Pair<String, Long>> = coordinator.installedFileDetails()

        fun storageDirPath(): String = coordinator.storageDirPath()
    }
