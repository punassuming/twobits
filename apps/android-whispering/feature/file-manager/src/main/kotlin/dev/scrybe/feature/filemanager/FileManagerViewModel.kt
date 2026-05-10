package dev.scrybe.feature.filemanager

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.common.TagsCodec
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.common.sanitizeFileName
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.export.MarkdownExporter
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val markdownExporter: MarkdownExporter,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FileManagerUiState>(FileManagerUiState.Loading)
        val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<String>()
        val events: SharedFlow<String> = _events.asSharedFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _uiState.value = FileManagerUiState.Loading
                runCatching { buildState() }
                    .onSuccess { _uiState.value = it }
                    .onFailure { _uiState.value = FileManagerUiState.Error(it.message ?: "Scan failed") }
            }
        }

        private suspend fun buildState(): FileManagerUiState.Success {
            val recordings = scanRecordingEntries()
            val outputs = scanOutputFiles()
            return FileManagerUiState.Success(recordings, outputs)
        }

        private suspend fun scanRecordingEntries(): List<RecordingFileEntry> {
            val sessions = sessionDao.getAllSessionsOnce()
            val knownPaths = sessions.map { it.audioFilePath }.toSet()
            val transcriptSessions =
                transcriptDao
                    .getAllTranscriptsOnce()
                    .map { it.sessionId }
                    .toSet()

            val entries =
                sessions
                    .map { entity ->
                        RecordingFileEntry(
                            absolutePath = entity.audioFilePath,
                            displayName = entity.title,
                            sizeBytes = File(entity.audioFilePath).length(),
                            lastModifiedMs = File(entity.audioFilePath).lastModified(),
                            sessionId = entity.id,
                            hasTranscript = entity.id in transcriptSessions,
                        )
                    }.toMutableList()

            val recordingsDir = context.filesDir.resolve("recordings")
            if (recordingsDir.exists()) {
                val audioExts = setOf("m4a", "mp4", "ogg", "webm")
                recordingsDir
                    .listFiles()
                    ?.filter { it.extension.lowercase() in audioExts && it.absolutePath !in knownPaths }
                    ?.forEach { orphan ->
                        entries.add(
                            RecordingFileEntry(
                                absolutePath = orphan.absolutePath,
                                displayName = orphan.nameWithoutExtension,
                                sizeBytes = orphan.length(),
                                lastModifiedMs = orphan.lastModified(),
                                sessionId = null,
                                hasTranscript = false,
                            ),
                        )
                    }
            }
            return entries.sortedByDescending { it.lastModifiedMs }
        }

        private fun scanOutputFiles(): List<OutputFileEntry> {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val result = mutableListOf<OutputFileEntry>()
            listDir(docsDir?.resolve("saved-recordings"), "Saved Copy", result)
            listDir(docsDir?.resolve("exports"), "Export", result)
            return result.sortedByDescending { it.lastModifiedMs }
        }

        private fun listDir(
            dir: File?,
            category: String,
            into: MutableList<OutputFileEntry>,
        ) {
            dir?.listFiles()?.forEach { f ->
                into.add(OutputFileEntry(f.absolutePath, f.name, f.length(), f.lastModified(), category))
            }
        }

        fun importOrphan(absolutePath: String) {
            viewModelScope.launch {
                runCatching { createSession(File(absolutePath)) }
                    .onSuccess {
                        refresh()
                        _events.emit("Recording imported")
                    }.onFailure { _events.emit(it.message ?: "Import failed") }
            }
        }

        private suspend fun createSession(file: File) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
            val entity =
                RecordingSessionEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Recording $timestamp",
                    tags = "",
                    audioFilePath = file.absolutePath,
                    durationMs = 0L,
                    fileSizeBytes = file.length(),
                    audioFormat = audioFormatFromExt(file.extension),
                    sampleRateHz = 0,
                    encodingBitRate = 0,
                    channelCount = 0,
                    waveformSamples = "",
                    status = "RECORDED",
                    isArchived = false,
                    estimatedTranscriptionCostUsd = null,
                    folderId = null,
                    createdAt = file.lastModified(),
                    updatedAt = System.currentTimeMillis(),
                )
            sessionDao.insertSession(entity)
        }

        private fun audioFormatFromExt(ext: String) =
            when (ext.lowercase()) {
                "m4a" -> "AAC"
                "mp4" -> "MP4"
                "ogg" -> "OGG"
                "webm" -> "WEBM"
                else -> "AAC"
            }

        fun deleteFile(absolutePath: String) {
            viewModelScope.launch {
                File(absolutePath).delete()
                refresh()
            }
        }

        fun exportBundle(sessionId: String) {
            viewModelScope.launch {
                runCatching { buildBundle(sessionId) }
                    .onSuccess { _events.emit("Bundle exported to ${it.absolutePath}") }
                    .onFailure { _events.emit(it.message ?: "Export failed") }
            }
        }

        private suspend fun buildBundle(sessionId: String): File {
            val entity = sessionDao.getSessionByIdOnce(sessionId) ?: error("Session not found")
            val transcriptEntities = transcriptDao.getTranscriptsForSession(sessionId).first()
            val outputDir = (
                context
                    .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?.resolve("exports") ?: context.filesDir.resolve("exports")
            )
            outputDir.mkdirs()

            val session = entity.toDomainModel()
            val transcripts = transcriptEntities.map { it.toDomainModel() }
            markdownExporter.export(session, transcripts, outputDir).getOrThrow()

            val audioSrc = File(entity.audioFilePath)
            if (audioSrc.exists()) {
                audioSrc.copyTo(
                    File(outputDir, "${sanitizeFileName(entity.title)}.${audioSrc.extension}"),
                    overwrite = true,
                )
            }
            return outputDir
        }

        private fun RecordingSessionEntity.toDomainModel() =
            RecordingSession(
                id = id,
                title = title,
                tags = TagsCodec.decode(tags),
                audioFilePath = audioFilePath,
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
                audioFormat = runCatching { AudioFormat.valueOf(audioFormat) }.getOrElse { AudioFormat.AAC },
                sampleRateHz = sampleRateHz,
                encodingBitRate = encodingBitRate,
                channelCount = channelCount,
                waveformSamples = WaveformCodec.decode(waveformSamples),
                status = runCatching { SessionStatus.valueOf(status) }.getOrElse { SessionStatus.RECORDED },
                isArchived = isArchived,
                estimatedTranscriptionCostUsd = estimatedTranscriptionCostUsd,
                folderId = folderId,
                locationLat = locationLat,
                locationLng = locationLng,
                locationLabel = locationLabel,
                sentimentJson = sentimentJson,
                topicsJson = topicsJson,
                createdAt = Instant.ofEpochMilli(createdAt),
                updatedAt = Instant.ofEpochMilli(updatedAt),
            )

        private fun dev.scrybe.core.database.TranscriptEntity.toDomainModel() =
            Transcript(
                id = id,
                sessionId = sessionId,
                content = content,
                type = runCatching { TranscriptType.valueOf(type) }.getOrElse { TranscriptType.RAW },
                sourceTranscriptId = sourceTranscriptId,
                providerType = providerType?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() },
                transformProfileId = transformProfileId,
                transformRunId = transformRunId,
                createdAt = Instant.ofEpochMilli(createdAt),
            )
    }
