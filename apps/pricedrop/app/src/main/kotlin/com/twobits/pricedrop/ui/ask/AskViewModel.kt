package com.twobits.pricedrop.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val role: String, val content: String)

data class AskUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class AskViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = _uiState

    val input = MutableStateFlow("")

    fun onInputChange(text: String) { input.value = text }

    fun send() {
        val text = input.value.trim()
        if (text.isBlank() || _uiState.value.isLoading) return
        input.value = ""
        val userMsg = ChatMessage("user", text)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            isLoading = true,
        )
        viewModelScope.launch {
            // Real implementation calls /v1/chat/completions via the Worker
            kotlinx.coroutines.delay(600)
            val reply = ChatMessage(
                "assistant",
                "I can help you track prices and find deals. Try asking me to 'watch Sony WH-1000XM5' or 'find the best price for an iPhone 15'.",
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + reply,
                isLoading = false,
            )
        }
    }
}
