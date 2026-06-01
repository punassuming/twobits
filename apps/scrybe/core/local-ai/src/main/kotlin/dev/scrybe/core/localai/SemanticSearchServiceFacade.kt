package dev.scrybe.core.localai

import dev.scrybe.core.transforms.OpenAiSemanticSearchService
import dev.scrybe.core.transforms.SessionSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticSearchServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiSemanticSearchService,
    ) {
        suspend fun rankByRelevance(
            query: String,
            sessions: List<SessionSummary>,
        ): Result<List<String>> = openAiService.rankByRelevance(query, sessions)
    }
