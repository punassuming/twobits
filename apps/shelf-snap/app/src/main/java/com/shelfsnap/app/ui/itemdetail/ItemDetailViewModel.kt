package com.shelfsnap.app.ui.itemdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.ListingStatus
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.PlatformListing
import com.shelfsnap.app.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tabs in the v2 item-detail screen. */
enum class DetailTab { DETAILS, MARKET, LIST }

data class ItemDetailUiState(
    val item: Item? = null,
    val tab: DetailTab = DetailTab.DETAILS,
    val isLoading: Boolean = true,
    val isAnalysing: Boolean = false,
    val isResearching: Boolean = false,
    val isCrossListing: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    // Editable field mirrors
    val editCategory: String = "",
    val editBrand: String = "",
    val editModel: String = "",
    val editDescription: String = "",
    val editCondition: Condition = Condition.GOOD,
    val editSize: String = "",
    val editColor: String = "",
    val editQuantity: String = "1",
    val editOriginalPrice: String = "",
    val editTags: List<String> = emptyList(),
    val editEstimatedValue: String = "0.00",
    val showPhotoViewer: Boolean = false,
    val viewerPhotoIndex: Int = 0,
    val editPrimaryPhotoIndex: Int = 0
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    fun load(itemId: Long) {
        viewModelScope.launch {
            val item = repository.getById(itemId)
            if (item != null) {
                _uiState.update { it.copyFromItem(item) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Item not found") }
            }
        }
    }

    private fun ItemDetailUiState.copyFromItem(item: Item) = copy(
        item = item,
        isLoading = false,
        editCategory = item.category,
        editBrand = item.brand,
        editModel = item.model,
        editDescription = item.description,
        editCondition = item.condition,
        editSize = item.size,
        editColor = item.color,
        editQuantity = item.quantity.toString(),
        editOriginalPrice = if (item.originalPrice > 0) "%.2f".format(item.originalPrice) else "",
        editTags = item.tags,
        editEstimatedValue = "%.2f".format(item.estimatedValue),
        editPrimaryPhotoIndex = item.primaryPhotoIndex
    )

    fun selectTab(tab: DetailTab) = _uiState.update { it.copy(tab = tab) }

    fun reanalyse() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, error = null) }
            val result = repository.analysePhotos(item.photoPaths)
            if (result.error != null) {
                _uiState.update { it.copy(isAnalysing = false, error = result.error) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isAnalysing = false,
                    editCategory = result.category,
                    editDescription = result.description,
                    editCondition = result.condition,
                    editEstimatedValue = "%.2f".format(result.estimatedValue),
                    item = item.copy(confidencePercent = result.confidencePercent)
                )
            }
        }
    }

    /** Runs price research (OpenAI + web search) and persists the result on the item. */
    fun researchPrice() {
        val current = currentEditedItem() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isResearching = true, error = null) }
            val result = repository.researchPrice(current)
            if (result.error != null) {
                _uiState.update { it.copy(isResearching = false, error = result.error) }
                return@launch
            }
            val updated = current.copy(
                marketResearch = result.research,
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updated)
            _uiState.update {
                it.copy(
                    isResearching = false,
                    item = updated,
                    // If the model proposed an overall price and the user hasn't typed one, surface it.
                    editEstimatedValue = result.suggestedValue
                        ?.let { v -> "%.2f".format(v) } ?: it.editEstimatedValue
                )
            }
        }
    }

    /** Applies a platform's suggested price to the editable asking-price field. */
    fun applySuggestedPrice(price: Double) =
        _uiState.update { it.copy(editEstimatedValue = "%.2f".format(price), tab = DetailTab.DETAILS) }

    fun onCategoryChange(value: String) = _uiState.update { it.copy(editCategory = value) }
    fun onBrandChange(value: String) = _uiState.update { it.copy(editBrand = value) }
    fun onModelChange(value: String) = _uiState.update { it.copy(editModel = value) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(editDescription = value) }
    fun onConditionChange(value: Condition) = _uiState.update { it.copy(editCondition = value) }
    fun onSizeChange(value: String) = _uiState.update { it.copy(editSize = value) }
    fun onColorChange(value: String) = _uiState.update { it.copy(editColor = value) }
    fun onQuantityChange(value: String) = _uiState.update { it.copy(editQuantity = value) }
    fun onOriginalPriceChange(value: String) = _uiState.update { it.copy(editOriginalPrice = value) }
    fun onEstimatedValueChange(value: String) = _uiState.update { it.copy(editEstimatedValue = value) }

    fun addTag(tag: String) {
        val clean = tag.trim().lowercase()
        if (clean.isBlank()) return
        _uiState.update {
            if (it.editTags.contains(clean)) it
            else it.copy(editTags = it.editTags + clean)
        }
    }

    fun removeTag(tag: String) =
        _uiState.update { it.copy(editTags = it.editTags - tag) }

    /** Builds an Item from the current item + edited fields (without persisting). */
    private fun currentEditedItem(): Item? {
        val state = _uiState.value
        val item = state.item ?: return null
        return item.copy(
            category = state.editCategory,
            brand = state.editBrand,
            model = state.editModel,
            description = state.editDescription,
            condition = state.editCondition,
            size = state.editSize,
            color = state.editColor,
            quantity = state.editQuantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            originalPrice = state.editOriginalPrice.toDoubleOrNull() ?: 0.0,
            tags = state.editTags,
            estimatedValue = state.editEstimatedValue.toDoubleOrNull() ?: 0.0,
            primaryPhotoIndex = state.editPrimaryPhotoIndex
        )
    }

    /** Saves edits and marks the item as confirmed (no longer a draft). */
    fun confirm() = persist(asDraft = false)

    /** Saves edits without confirming (stays as draft). */
    fun saveDraft() = persist(asDraft = true)

    private fun persist(asDraft: Boolean) {
        val edited = currentEditedItem() ?: return
        viewModelScope.launch {
            val updated = edited.copy(isDraft = asDraft, updatedAt = System.currentTimeMillis())
            repository.update(updated)
            _uiState.update { it.copy(item = updated, isSaved = true) }
        }
    }

    /** Cross-lists the item on the given platforms at their suggested (or asking) prices. */
    fun crossList(platforms: Set<Platform>) {
        val item = _uiState.value.item ?: return
        if (platforms.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCrossListing = true) }
            val asking = _uiState.value.editEstimatedValue.toDoubleOrNull() ?: item.estimatedValue
            val newListings = platforms
                .filter { p -> item.listings.none { it.platformKey == p.key } }
                .map { p ->
                    PlatformListing(
                        platformKey = p.key,
                        status = ListingStatus.ACTIVE,
                        price = item.marketResearch.suggestedPrices[p.key] ?: asking
                    )
                }
            val updated = item.copy(
                listings = item.listings + newListings,
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updated)
            _uiState.update { it.copy(item = updated, isCrossListing = false, message = "listed") }
        }
    }

    fun delete() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.delete(item.id)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun setPrimaryPhoto(index: Int) =
        _uiState.update { it.copy(editPrimaryPhotoIndex = index) }

    fun openPhotoViewer(index: Int) =
        _uiState.update { it.copy(showPhotoViewer = true, viewerPhotoIndex = index) }

    fun closePhotoViewer() = _uiState.update { it.copy(showPhotoViewer = false) }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
