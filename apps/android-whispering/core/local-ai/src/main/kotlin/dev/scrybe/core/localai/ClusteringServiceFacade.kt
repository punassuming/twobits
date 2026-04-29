package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transforms.ClusterSuggestion
import dev.scrybe.core.transforms.OpenAiClusteringService
import dev.scrybe.core.transforms.SessionSummary
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClusteringServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiClusteringService,
        private val localLlmService: LocalLlmService,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) {
        suspend fun suggestClusters(
            sessions: List<SessionSummary>,
            existingFolderNames: List<String>,
            commonTags: List<String>,
        ): Result<List<ClusterSuggestion>> =
            if (preferencesDataStore.defaultProvider.first() == ProviderType.LOCAL.name) {
                localLlmService.suggestClusters(sessions, existingFolderNames, commonTags)
            } else {
                openAiService.suggestClusters(sessions, existingFolderNames, commonTags)
            }
    }
