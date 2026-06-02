package com.shelfsnap.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.remote.DraftItemResult
import com.shelfsnap.app.data.repository.ItemRepository
import com.shelfsnap.app.util.ApiKeyValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val capturedPaths: List<String> = emptyList(),
    val isAnalysing: Boolean = false,
    val draftResult: DraftItemResult? = null,
    val savedItemId: Long? = null,
    val error: String? = null,
    val showApiKeyPrompt: Boolean = false,
    val flashOn: Boolean = false,
    /** When true, analysis is the single default action after capture (no Save/Analyze choice). */
    val autoAnalyze: Boolean = false,
    /** When set (> 0) the camera appends captured photos to this existing item. */
    val appendToItemId: Long? = null
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(autoAnalyze = repository.getAutoAnalyze()) }
        }
    }

    /** Sets the target item when launched to append photos (itemId <= 0 = new item). */
    fun initFor(itemId: Long) {
        _uiState.update { it.copy(appendToItemId = itemId.takeIf { id -> id > 0 }) }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(flashOn = !it.flashOn) }
    }

    /** Called each time the user successfully captures a photo. */
    fun onPhotoCaptured(path: String) {
        _uiState.update { it.copy(capturedPaths = it.capturedPaths + path) }
    }

    /**
     * Append mode: add the captured photos to the existing item and navigate back to it.
     * Fields aren't re-analysed here — the user can re-analyse from the detail screen.
     */
    fun commitAppend() {
        val state = _uiState.value
        val target = state.appendToItemId ?: return
        val paths = state.capturedPaths
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val existing = repository.getById(target)
            if (existing == null) {
                _uiState.update { it.copy(error = "Item not found") }
                return@launch
            }
            repository.update(
                existing.copy(
                    photoPaths = existing.photoPaths + paths,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(savedItemId = target) }
        }
    }

    fun removePhoto(path: String) {
        _uiState.update { it.copy(capturedPaths = it.capturedPaths - path) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Surfaces a user-facing error message (e.g. a failed photo capture). */
    fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun dismissApiKeyPrompt() {
        _uiState.update { it.copy(showApiKeyPrompt = false) }
    }

    /**
     * Entry point for the "Analyze" button. Verifies an API key is configured
     * before spending a network call; if none, prompts the user to either open
     * Settings or save the item without analysis.
     */
    fun onAnalyseClicked() {
        val paths = _uiState.value.capturedPaths
        if (paths.isEmpty()) return
        viewModelScope.launch {
            if (ApiKeyValidator.isValid(repository.getApiKey())) {
                analyseAndSave()
            } else {
                _uiState.update { it.copy(showApiKeyPrompt = true) }
            }
        }
    }

    /**
     * Story 2 – Draft Item Extraction.
     * Sends all captured photos to the vision API, creates a draft item record,
     * then signals the UI to navigate to the detail/review screen.
     */
    fun analyseAndSave() {
        val paths = _uiState.value.capturedPaths
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, error = null) }

            val result = repository.analysePhotos(paths)

            if (result.error != null) {
                _uiState.update { it.copy(isAnalysing = false, error = result.error) }
                return@launch
            }

            // Persist draft item so the user can review/edit all AI-proposed fields.
            val draft = Item(
                photoPaths = paths,
                category = result.category,
                description = result.description,
                condition = result.condition,
                estimatedValue = result.estimatedValue,
                confidencePercent = result.confidencePercent,
                isDraft = true
            )
            val itemId = repository.save(draft)
            _uiState.update {
                it.copy(isAnalysing = false, draftResult = result, savedItemId = itemId)
            }
        }
    }

    /** Saves a draft directly without API analysis (no API key configured). */
    fun saveWithoutAnalysis() {
        val paths = _uiState.value.capturedPaths
        _uiState.update { it.copy(showApiKeyPrompt = false) }
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val draft = Item(
                photoPaths = paths,
                category = "",
                description = "",
                condition = Condition.GOOD,
                estimatedValue = 0.0,
                confidencePercent = 0,
                isDraft = true
            )
            val itemId = repository.save(draft)
            _uiState.update { it.copy(savedItemId = itemId) }
        }
    }
}
