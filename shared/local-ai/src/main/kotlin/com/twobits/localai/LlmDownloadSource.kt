package com.twobits.localai

import com.twobits.core.localmodels.LocalLlmModel
import com.twobits.core.localmodels.LocalModelState
import kotlinx.coroutines.flow.StateFlow

/**
 * What a [androidx.work.CoroutineWorker] needs from an app's `LocalModelManager` to drive a
 * download and observe its progress — just this, not the app's full manager (DataStore-backed
 * selected-model tracking, Whisper handling, etc.). Each app's `LocalModelManager` already
 * implements this shape via [LlmModelDownloadCoordinator]; this interface just names it so
 * [runLlmDownload] can depend on it without depending on any one app's manager type.
 */
interface LlmDownloadSource {
    val llmStates: StateFlow<Map<LocalLlmModel, LocalModelState>>

    suspend fun downloadLlm(model: LocalLlmModel)
}
