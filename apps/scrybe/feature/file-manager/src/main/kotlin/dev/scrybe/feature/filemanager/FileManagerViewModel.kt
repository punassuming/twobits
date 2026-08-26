package dev.scrybe.feature.filemanager

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.audio.WaveformExtractor
import dev.scrybe.core.common.TagsCodec
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.common.sanitizeFileName
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.export.MarkdownExporter
import dev.scrybe.core.localai.LocalModelManager
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
        private val waveformExtractor: WaveformExtractor,
        private val localModelManager: LocalModelManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FileManagerUiState>(FileManagerUiState.Loading)
        val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<String>()
        val events: SharedFlow<String> = _events.asSharedFlow()

        private val _pendingImport = MutableStateFlow<PendingImport?>(null)
        val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

        private val recordingsDir: File by lazy { context.filesDir.resolve("recordings") }

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
            val models = scanModelFiles()
            return FileManagerUiState.Success(recordings, outputs, models)
        }

        private fun scanModelFiles(): List<ModelFileEntry> {
            val storageDir = localModelManager.storageDirPath()

            fun List<Pair<String, Long>>.toEntries(isOrphaned: Boolean) =
                map { (name, sizeBytes) ->
                    ModelFileEntry(
                        absolutePath = File(storageDir, name).absolutePath,
                        displayName = name,
                        sizeBytes = sizeBytes,
                        isOrphaned = isOrphaned,
                    )
                }
            return localModelManager.installedFileDetails().toEntries(isOrphaned = false) +
                localModelManager.orphanedFileDetails().toEntries(isOrphaned = true)
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

            if (recordingsDir.exists()) {
                val audioExts = setOf("m4a", "mp3", "mp4", "ogg", "wav", "webm")
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
            val file = File(absolutePath)
            _pendingImport.value = PendingImport(file, file.lastModified(), deleteOnCancel = false)
        }

        fun importExternalFile(uri: Uri) {
            viewModelScope.launch {
                runCatching {
                    val ext = mimeExtension(context.contentResolver.getType(uri))
                    val dest = File(recordingsDir.also { it.mkdirs() }, "recording_${UUID.randomUUID()}.$ext")
                    context.contentResolver.openInputStream(uri)!!.use { src ->
                        dest.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    dest
                }.onSuccess { file ->
                    _pendingImport.value = PendingImport(file, file.lastModified(), deleteOnCancel = true)
                }.onFailure { _events.emit(it.message ?: "Import failed") }
            }
        }

        fun confirmImport(timestampMs: Long) {
            val pending = _pendingImport.value ?: return
            _pendingImport.value = null
            viewModelScope.launch {
                runCatching { createSession(pending.file, timestampMs) }
                    .onSuccess {
                        refresh()
                        _events.emit("Recording imported")
                    }.onFailure { _events.emit(it.message ?: "Import failed") }
            }
        }

        fun dismissImport() {
            val pending = _pendingImport.value ?: return
            _pendingImport.value = null
            if (pending.deleteOnCancel) pending.file.delete()
        }

        private fun mimeExtension(mimeType: String?): String =
            when (mimeType?.lowercase()) {
                "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a"
                "audio/mpeg" -> "mp3"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg" -> "ogg"
                "audio/webm" -> "webm"
                else -> "m4a"
            }

        private suspend fun createSession(
            file: File,
            createdAtMs: Long = file.lastModified(),
        ) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(createdAtMs))
            val retriever = MediaMetadataRetriever()
            var durationMs: Long = 0L
            var sampleRateHz: Int = DEFAULT_SAMPLE_RATE
            var encodingBitRate: Int = DEFAULT_BIT_RATE
            var channelCount: Int = 1
            try {
                // Use FileDescriptor instead of path string — more reliable across Android
                // versions and audio codecs (MP3 in particular can fail with the path overload).
                val metadataOk =
                    runCatching {
                        FileInputStream(file).use { fis -> retriever.setDataSource(fis.fd) }
                    }.isSuccess
                if (metadataOk) {
                    durationMs =
                        retriever
                            .extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION,
                            )?.toLongOrNull() ?: 0L
                    sampleRateHz =
                        retriever
                            .extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_SAMPLERATE,
                            )?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
                    encodingBitRate =
                        retriever
                            .extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_BITRATE,
                            )?.toIntOrNull() ?: DEFAULT_BIT_RATE
                    channelCount =
                        runCatching {
                            val extractor = MediaExtractor()
                            try {
                                FileInputStream(file).use { fis -> extractor.setDataSource(fis.fd) }
                                val audioTrackIndex =
                                    (0 until extractor.trackCount).firstOrNull { trackIndex ->
                                        extractor
                                            .getTrackFormat(trackIndex)
                                            .getString(MediaFormat.KEY_MIME)
                                            ?.startsWith("audio/") == true
                                    }
                                if (audioTrackIndex != null) {
                                    extractor
                                        .getTrackFormat(audioTrackIndex)
                                        .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                } else {
                                    1
                                }
                            } finally {
                                extractor.release()
                            }
                        }.getOrDefault(1)
                }
            } finally {
                retriever.release()
            }
            val entity =
                RecordingSessionEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Recording $timestamp",
                    tags = "",
                    audioFilePath = file.absolutePath,
                    durationMs = durationMs,
                    fileSizeBytes = file.length(),
                    audioFormat = audioFormatFromExt(file.extension),
                    sampleRateHz = sampleRateHz,
                    encodingBitRate = encodingBitRate,
                    channelCount = channelCount,
                    waveformSamples =
                        withContext(Dispatchers.IO) {
                            WaveformCodec.encode(waveformExtractor.extract(file))
                        },
                    status = "RECORDED",
                    isArchived = false,
                    estimatedTranscriptionCostUsd = null,
                    folderId = null,
                    createdAt = createdAtMs,
                    updatedAt = System.currentTimeMillis(),
                )
            sessionDao.insertSession(entity)
        }

        private fun audioFormatFromExt(ext: String) =
            when (ext.lowercase()) {
                "m4a" -> "AAC"
                "mp3" -> "MP3"
                "mp4" -> "MP4"
                "ogg" -> "OGG"
                "wav" -> "WAV"
                "webm" -> "WEBM"
                else -> "AAC"
            }

        companion object {
            private const val DEFAULT_SAMPLE_RATE = 48_000
            private const val DEFAULT_BIT_RATE = 128_000
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
                mode = runCatching { RecordingMode.valueOf(mode) }.getOrDefault(RecordingMode.JOURNAL),
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
