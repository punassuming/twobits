package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transforms.OpenAiAutoRenameService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRenameServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiAutoRenameService,
        private val localLlmService: LocalLlmService,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) {
        suspend fun suggestTitle(
            transcriptText: String,
            currentTitle: String,
        ): Result<String> =
            if (preferencesDataStore.defaultProvider.first() == ProviderType.LOCAL.name) {
                localLlmService.suggestTitle(transcriptText, currentTitle)
            } else {
                openAiService.suggestTitle(transcriptText, currentTitle)
            }
    }
