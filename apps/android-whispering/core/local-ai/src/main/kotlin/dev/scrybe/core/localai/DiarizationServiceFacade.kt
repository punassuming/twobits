package dev.scrybe.core.localai

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SpeakerSegment
import dev.scrybe.core.transcription.DiarizationService
import dev.scrybe.core.transcription.OpenAiDiarizationService
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiarizationServiceFacade
    @Inject
    constructor(
        private val openAiService: OpenAiDiarizationService,
        private val localService: LocalDiarizationService,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : DiarizationService {
        override suspend fun diarize(
            sessionId: String,
            audioFile: File,
            transcriptText: String,
            providerType: ProviderType,
        ): Result<List<SpeakerSegment>> =
            if (preferencesDataStore.aiFeaturesProvider.first() == ProviderType.LOCAL.name) {
                localService.diarize(sessionId, audioFile, transcriptText, providerType)
            } else {
                openAiService.diarize(sessionId, audioFile, transcriptText, providerType)
            }
    }
