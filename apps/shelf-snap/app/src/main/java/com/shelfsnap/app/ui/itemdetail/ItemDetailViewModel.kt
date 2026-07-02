package com.shelfsnap.app.ui.itemdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfsnap.app.data.listing.ListingCopy
import com.shelfsnap.app.data.listing.ListingCopyGenerator
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.ListingStatus
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.model.PlatformListing
import com.shelfsnap.app.data.model.VisionModel
import com.shelfsnap.app.data.model.displayTitle
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
    val navigateToListingSummary: Boolean = false,
    val refiningPlatforms: Set<String> = emptySet(),
    val isRefiningAll: Boolean = false,
    // Editable field mirrors
    val editTitle: String = "",
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
    val editPrimaryPhotoIndex: Int = 0,
    val visionSource: String = "byok",
    val overrideVisionModel: VisionModel? = null,
    val lastAnalysisModel: VisionModel? = null,
)

@HiltViewModel
class ItemDetailViewModel
    @Inject
    constructor(
        private val repository: ItemRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ItemDetailUiState())
        val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

        fun load(itemId: Long) {
            viewModelScope.launch {
                val item = repository.getById(itemId)
                if (item != null) {
                    val source = repository.getVisionSource()
                    _uiState.update { it.copyFromItem(item).copy(visionSource = source) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Item not found") }
                }
            }
        }

        private fun ItemDetailUiState.copyFromItem(item: Item) =
            copy(
                item = item,
                isLoading = false,
                // Pre-migration rows have a blank persisted title; displayTitle falls back to
                // brand+model/category so the field isn't empty on first edit.
                editTitle = item.displayTitle,
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
                editPrimaryPhotoIndex = item.primaryPhotoIndex,
            )

        fun selectTab(tab: DetailTab) = _uiState.update { it.copy(tab = tab) }

        fun onOverrideVisionModelChange(model: VisionModel) = _uiState.update { it.copy(overrideVisionModel = model) }

        fun reanalyse() {
            val item = _uiState.value.item ?: return
            val modelOverride = _uiState.value.overrideVisionModel
            viewModelScope.launch {
                _uiState.update { it.copy(isAnalysing = true, error = null) }
                val result = repository.analysePhotos(item.photoPaths, modelOverride)
                if (result.error != null) {
                    _uiState.update { it.copy(isAnalysing = false, error = result.error) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isAnalysing = false,
                        editCategory = result.category,
                        editBrand = result.brand,
                        editModel = result.model,
                        editDescription = result.description,
                        editTags = result.tags,
                        editCondition = result.condition,
                        editEstimatedValue = "%.2f".format(result.estimatedValue),
                        item = item.copy(confidencePercent = result.confidencePercent),
                        lastAnalysisModel = modelOverride ?: VisionModel.default,
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
                val updated =
                    current.copy(
                        marketResearch = result.research,
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updated)
                _uiState.update {
                    it.copy(
                        isResearching = false,
                        item = updated,
                        // If the model proposed an overall price and the user hasn't typed one, surface it.
                        editEstimatedValue =
                            result.suggestedValue
                                ?.let { v -> "%.2f".format(v) } ?: it.editEstimatedValue,
                    )
                }
            }
        }

        /** Applies a platform's suggested price to the editable asking-price field. */
        fun applySuggestedPrice(price: Double) = _uiState.update { it.copy(editEstimatedValue = "%.2f".format(price), tab = DetailTab.DETAILS) }

        fun onTitleChange(value: String) = _uiState.update { it.copy(editTitle = value) }

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
                if (it.editTags.contains(clean)) {
                    it
                } else {
                    it.copy(editTags = it.editTags + clean)
                }
            }
        }

        fun removeTag(tag: String) = _uiState.update { it.copy(editTags = it.editTags - tag) }

        /** Builds an Item from the current item + edited fields (without persisting). */
        private fun currentEditedItem(): Item? {
            val state = _uiState.value
            val item = state.item ?: return null
            return item.copy(
                title = state.editTitle,
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
                primaryPhotoIndex = state.editPrimaryPhotoIndex,
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

        /** Cross-lists the item on the given platforms, generating DRAFT listings with copy. */
        fun crossList(platforms: Set<Platform>) {
            val item = _uiState.value.item ?: return
            if (platforms.isEmpty()) return
            viewModelScope.launch {
                _uiState.update { it.copy(isCrossListing = true) }
                val asking = _uiState.value.editEstimatedValue.toDoubleOrNull() ?: item.estimatedValue
                val newListings =
                    platforms
                        .filter { p -> item.listings.none { it.platformKey == p.key && it.status != ListingStatus.UNLISTED } }
                        .map { p ->
                            val copy = ListingCopyGenerator.generate(item, p)
                            PlatformListing(
                                platformKey = p.key,
                                status = ListingStatus.DRAFT,
                                price = item.marketResearch.suggestedPrices[p.key] ?: asking,
                                title = copy.title,
                                description = copy.description,
                                condition = copy.condition,
                                shipping = copy.shipping,
                            )
                        }
                val updated =
                    item.copy(
                        listings = item.listings.filter { it.status != ListingStatus.UNLISTED } + newListings,
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updated)
                _uiState.update { it.copy(item = updated, isCrossListing = false, navigateToListingSummary = true) }
            }
        }

        fun clearNavigateToListingSummary() = _uiState.update { it.copy(navigateToListingSummary = false) }

        /** Flips a DRAFT or ACTIVE listing to UNLISTED. */
        fun unlistPlatform(platformKey: String) {
            val item = _uiState.value.item ?: return
            viewModelScope.launch {
                val updated =
                    item.copy(
                        listings =
                            item.listings.map { l ->
                                if (l.platformKey == platformKey) l.copy(status = ListingStatus.UNLISTED) else l
                            },
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updated)
                _uiState.update { it.copy(item = updated) }
            }
        }

        /** Promotes a DRAFT listing to ACTIVE (user has published it on the platform). */
        fun markListingActive(platformKey: String) {
            val item = _uiState.value.item ?: return
            viewModelScope.launch {
                val updated =
                    item.copy(
                        listings =
                            item.listings.map { l ->
                                if (l.platformKey == platformKey) l.copy(status = ListingStatus.ACTIVE) else l
                            },
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updated)
                _uiState.update { it.copy(item = updated) }
            }
        }

        /** AI-refines the listing copy for one platform using the current item fields. */
        fun refineListing(platformKey: String) {
            val item = _uiState.value.item ?: return
            val platform = Platform.fromKey(platformKey) ?: return
            val listing = item.listings.firstOrNull { it.platformKey == platformKey } ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(refiningPlatforms = it.refiningPlatforms + platformKey) }
                val current =
                    ListingCopy(
                        title = listing.title ?: "",
                        description = listing.description ?: "",
                        condition = listing.condition ?: "",
                        shipping = listing.shipping ?: "",
                    )
                val refined = repository.refineListing(item, platform, current)
                val updatedItem =
                    item.copy(
                        listings =
                            item.listings.map { l ->
                                if (l.platformKey == platformKey) {
                                    l.copy(title = refined.title, description = refined.description, condition = refined.condition, shipping = refined.shipping)
                                } else {
                                    l
                                }
                            },
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updatedItem)
                _uiState.update { it.copy(item = updatedItem, refiningPlatforms = it.refiningPlatforms - platformKey) }
            }
        }

        /** AI-refines listing copy for all DRAFT listings sequentially. */
        fun refineAllListings() {
            val item = _uiState.value.item ?: return
            val draftKeys = item.listings.filter { it.status == ListingStatus.DRAFT }.map { it.platformKey }
            if (draftKeys.isEmpty()) return
            viewModelScope.launch {
                _uiState.update { it.copy(isRefiningAll = true) }
                draftKeys.forEach { key -> refineListing(key) }
                _uiState.update { it.copy(isRefiningAll = false) }
            }
        }

        /** Flips a platform listing from ACTIVE → SOLD and persists. */
        fun markSold(platformKey: String) {
            val item = _uiState.value.item ?: return
            viewModelScope.launch {
                val updated =
                    item.copy(
                        listings =
                            item.listings.map { l ->
                                if (l.platformKey == platformKey) l.copy(status = ListingStatus.SOLD) else l
                            },
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updated)
                _uiState.update { it.copy(item = updated) }
            }
        }

        fun setListingUrl(
            platformKey: String,
            url: String,
        ) {
            val item = _uiState.value.item ?: return
            viewModelScope.launch {
                val updated =
                    item.copy(
                        listings =
                            item.listings.map { l ->
                                if (l.platformKey == platformKey) {
                                    l.copy(listingUrl = url.trim().takeIf { it.isNotBlank() })
                                } else {
                                    l
                                }
                            },
                        updatedAt = System.currentTimeMillis(),
                    )
                repository.update(updated)
                _uiState.update { it.copy(item = updated) }
            }
        }

        fun delete() {
            val item = _uiState.value.item ?: return
            viewModelScope.launch {
                repository.delete(item.id)
                _uiState.update { it.copy(isDeleted = true) }
            }
        }

        fun setPrimaryPhoto(index: Int) = _uiState.update { it.copy(editPrimaryPhotoIndex = index) }

        fun openPhotoViewer(index: Int) = _uiState.update { it.copy(showPhotoViewer = true, viewerPhotoIndex = index) }

        fun closePhotoViewer() = _uiState.update { it.copy(showPhotoViewer = false) }

        fun clearError() = _uiState.update { it.copy(error = null) }

        fun clearMessage() = _uiState.update { it.copy(message = null) }
    }
