package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transforms.OpenAiTagSuggestionService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagSuggestionServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiTagSuggestionService,
        private val localLlmService: LocalLlmService,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) {
        suspend fun suggestTags(
            title: String,
            transcriptText: String,
            existingTags: List<String>,
        ): Result<List<String>> =
            if (preferencesDataStore.defaultProvider.first() == ProviderType.LOCAL.name) {
                localLlmService.suggestTags(title, transcriptText, existingTags)
            } else {
                openAiService.suggestTags(title, transcriptText, existingTags)
            }
    }
