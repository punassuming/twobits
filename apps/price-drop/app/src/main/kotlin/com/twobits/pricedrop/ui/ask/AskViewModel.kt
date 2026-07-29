package com.twobits.pricedrop.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.local.ChatMessageDao
import com.twobits.pricedrop.data.local.LocalModelManager
import com.twobits.pricedrop.data.model.ChatMessageEntity
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val role: String,
    val content: String,
)

data class AskUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class AskViewModel
    @Inject
    constructor(
        private val api: PriceDropApiClient,
        private val chatMessageDao: ChatMessageDao,
        private val providerSettings: ProviderSettingsStore,
        private val localModelManager: LocalModelManager,
        private val localAskSession: LocalAskSession,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AskUiState())
        val uiState: StateFlow<AskUiState> = _uiState

        val input = MutableStateFlow("")

        init {
            chatMessageDao
                .observeAll()
                .onEach { entities ->
                    _uiState.value =
                        _uiState.value.copy(
                            messages = entities.map { ChatMessage(it.role, it.content) },
                        )
                }.launchIn(viewModelScope)
        }

        fun onInputChange(text: String) {
            input.value = text
        }

        fun send() {
            val text = input.value.trim()
            if (text.isBlank() || _uiState.value.isLoading) return
            input.value = ""
            _uiState.value = _uiState.value.copy(isLoading = true)
            viewModelScope.launch {
                chatMessageDao.insert(ChatMessageEntity(role = "user", content = text))
                val reply =
                    runCatching {
                        if (providerSettings.getFeatureSource(AiFeature.ASK) == ProviderMode.LOCAL) {
                            sendLocal(text)
                        } else {
                            api.chat(SYSTEM_PROMPT, _uiState.value.messages)
                        }
                    }.getOrElse { e ->
                        "Sorry — I couldn't reach the shopping assistant. ${e.message.orEmpty()}".trim()
                    }
                chatMessageDao.insert(ChatMessageEntity(role = "assistant", content = reply))
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }

        private suspend fun sendLocal(text: String): String {
            val modelFile =
                localModelManager.selectedLlm.value?.let { localModelManager.llmFile(it) }
                    ?: localModelManager.anyLlmReady()?.let { localModelManager.llmFile(it) }
                    ?: error("No local model downloaded. Go to Settings → AI → Ask to download one.")
            return localAskSession.send(text, modelFile, SYSTEM_PROMPT)
        }

        fun clearHistory() {
            localAskSession.close()
            viewModelScope.launch {
                chatMessageDao.deleteAll()
            }
        }

        override fun onCleared() {
            localAskSession.close()
            super.onCleared()
        }

        private companion object {
            const val SYSTEM_PROMPT =
                "You are PriceDrop's shopping assistant. Help the user find products, compare prices, " +
                    "judge whether a deal is good, and decide what to track. Be concise and factual. " +
                    "Never claim a coupon works unless it is verified. Indicate when provider coverage is " +
                    "limited and prefer actionable product suggestions over long prose."
        }
    }
