package com.twobits.pricedrop.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.pricedrop.data.remote.PriceDropApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AskUiState())
        val uiState: StateFlow<AskUiState> = _uiState

        val input = MutableStateFlow("")

        fun onInputChange(text: String) {
            input.value = text
        }

        fun send() {
            val text = input.value.trim()
            if (text.isBlank() || _uiState.value.isLoading) return
            input.value = ""
            val userMsg = ChatMessage("user", text)
            _uiState.value =
                _uiState.value.copy(
                    messages = _uiState.value.messages + userMsg,
                    isLoading = true,
                )
            viewModelScope.launch {
                val reply =
                    runCatching { api.chat(SYSTEM_PROMPT, text) }
                        .getOrElse { e ->
                            "Sorry — I couldn't reach the shopping assistant. ${e.message.orEmpty()}".trim()
                        }
                _uiState.value =
                    _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage("assistant", reply),
                        isLoading = false,
                    )
            }
        }

        private companion object {
            const val SYSTEM_PROMPT =
                "You are PriceDrop's shopping assistant. Help the user find products, compare prices, " +
                    "judge whether a deal is good, and decide what to track. Be concise and factual. " +
                    "Never claim a coupon works unless it is verified. Indicate when provider coverage is " +
                    "limited and prefer actionable product suggestions over long prose."
        }
    }
