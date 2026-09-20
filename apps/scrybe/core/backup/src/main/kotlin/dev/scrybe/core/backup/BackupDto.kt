package dev.scrybe.core.backup

import dev.scrybe.core.database.CustomRecordingTypeEntity
import dev.scrybe.core.database.FolderEntity
import dev.scrybe.core.database.PersonEntity
import dev.scrybe.core.database.ProviderConfigEntity
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.database.SessionTaskEntity
import dev.scrybe.core.database.SpeakerSegmentEntity
import dev.scrybe.core.database.TranscriptChunkEntity
import dev.scrybe.core.database.TranscriptEntity
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.database.TransformRunEntity
import kotlinx.serialization.Serializable

/*
 * The wire format of a backup.
 *
 * These mirror the Room entities rather than reusing them, and that is a deliberate piece of
 * duplication in a codebase that has spent real effort removing duplication elsewhere. The reason:
 * a backup is a file your *old phone* wrote, read back by a build that may be months newer.
 * Annotating the entities directly would weld the file format to the current schema, so renaming a
 * column would silently stop old backups restoring — with no compile error, because the JSON would
 * simply not match any more.
 *
 * The duplication is kept honest by `EntityFieldCoverageTest`, which reflects over each entity's
 * constructor and fails when an entity gains a field no DTO carries. Drift becomes a red build
 * rather than a backup that quietly loses data.
 *
 * Two rules every DTO here follows:
 *
 * 1. **Every field has a default.** kotlinx.serialization treats a missing key with no default as
 *    fatal, and a single missing key fails the whole document. That exact bug shipped in this repo:
 *    `LegacyCrashLogEntry.message` was nullable with no default, and one crash entry without a
 *    message silently discarded a user's entire pre-merge log. A backup is the last place to repeat
 *    it, so non-null fields get an empty/zero default and decode leniently.
 * 2. **Decoding uses `ignoreUnknownKeys`.** A backup from a newer build carries fields this one has
 *    never heard of; it should restore what it understands rather than refuse the file.
 */

@Serializable
internal data class SessionDto(
    val id: String = "",
    val title: String = "",
    val tags: String = "",
    // Absolute on the device that wrote it, and meaningless anywhere else. Carried for diagnostics
    // only — restore rewrites it to the new device's recordings directory before inserting.
    val audioFilePath: String = "",
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val audioFormat: String = "AAC",
    val sampleRateHz: Int = 0,
    val encodingBitRate: Int = 0,
    val channelCount: Int = 1,
    val waveformSamples: String = "",
    val status: String = "RECORDED",
    val isArchived: Boolean = false,
    val estimatedTranscriptionCostUsd: Double? = null,
    val folderId: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationLabel: String? = null,
    val sentimentJson: String? = null,
    val topicsJson: String? = null,
    val mode: String = "JOURNAL",
    val customTypeId: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
internal data class TranscriptDto(
    val id: String = "",
    val sessionId: String = "",
    val content: String = "",
    val type: String = "RAW",
    val sourceTranscriptId: String? = null,
    val providerType: String? = null,
    val transformProfileId: String? = null,
    val transformRunId: String? = null,
    val createdAt: Long = 0,
)

@Serializable
internal data class TranscriptChunkDto(
    val id: String = "",
    val sessionId: String = "",
    val chunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val status: String = "",
    val text: String? = null,
    val createdAt: Long = 0,
)

@Serializable
internal data class SpeakerSegmentDto(
    val id: String = "",
    val sessionId: String = "",
    val speakerId: String = "",
    val speakerLabel: String? = null,
    val personId: String? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
)

@Serializable
internal data class SessionTaskDto(
    val id: String = "",
    val sessionId: String = "",
    val text: String = "",
    val assignee: String? = null,
    val dueLabel: String? = null,
    val isDone: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
internal data class TransformRunDto(
    val id: String = "",
    val sessionId: String = "",
    val profileId: String = "",
    val inputTranscriptId: String = "",
    val outputTranscriptId: String? = null,
    val status: String = "",
    val errorMessage: String? = null,
    val startedAt: Long = 0,
    val completedAt: Long? = null,
)

@Serializable
internal data class TransformProfileDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val steps: String = "",
    val providerType: String = "",
    val isDefault: Boolean = false,
    val modelName: String? = null,
    val iconName: String = "MIC",
    val colorName: String = "BLUE",
    val mode: String? = null,
    val useCount: Int = 0,
)

@Serializable
internal data class FolderDto(
    val id: String = "",
    val name: String = "",
    val parentFolderId: String? = null,
    val createdAt: Long = 0,
)

@Serializable
internal data class PersonDto(
    val id: String = "",
    val name: String = "",
    val voiceEmbeddingJson: String? = null,
    val createdAt: Long = 0,
)

@Serializable
internal data class CustomRecordingTypeDto(
    val id: String = "",
    val name: String = "",
    val defaultProfileId: String? = null,
    val createdAt: Long = 0,
    val iconName: String? = null,
)

/**
 * Note what is **not** here: `apiKeyAlias`.
 *
 * The alias names a key in the secure store, and the secure store does not travel in a backup — a
 * restored config would point at a key that does not exist on the new device. Carrying the alias
 * would make the provider look configured while silently failing on the first call. It is dropped
 * on purpose, and restore tells the user their API keys need re-entering.
 */
@Serializable
internal data class ProviderConfigDto(
    val id: String = "",
    val providerType: String = "",
    val isEnabled: Boolean = false,
    val modelName: String = "",
)

/** Every table in one document. Each list defaults to empty so a partial backup still decodes. */
@Serializable
internal data class DatabaseDto(
    val sessions: List<SessionDto> = emptyList(),
    val transcripts: List<TranscriptDto> = emptyList(),
    val transcriptChunks: List<TranscriptChunkDto> = emptyList(),
    val speakerSegments: List<SpeakerSegmentDto> = emptyList(),
    val sessionTasks: List<SessionTaskDto> = emptyList(),
    val transformRuns: List<TransformRunDto> = emptyList(),
    val transformProfiles: List<TransformProfileDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val persons: List<PersonDto> = emptyList(),
    val customRecordingTypes: List<CustomRecordingTypeDto> = emptyList(),
    val providerConfigs: List<ProviderConfigDto> = emptyList(),
)

/**
 * Written as `manifest.json`, and read before anything else in the payload.
 *
 * [databaseSchemaVersion] is the guard that matters. `AppDatabase` sets `exportSchema = false` and
 * the repo keeps no schema JSON, so there is no way to validate a foreign schema field by field —
 * the version number is the only signal available, and restore refuses a backup claiming a newer
 * one rather than inserting rows it may not understand.
 */
@Serializable
internal data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersionName: String = "",
    val databaseSchemaVersion: Int = 0,
    val createdAtMs: Long = 0,
    val sessionCount: Int = 0,
    val audioFileCount: Int = 0,
    val totalAudioBytes: Long = 0,
)

// ---------------------------------------------------------------------------
// Entity <-> DTO. Plain functions rather than an interface: the mapping is a flat field copy and a
// generic converter would obscure that every field is accounted for by hand, which is the point.
// ---------------------------------------------------------------------------

internal fun RecordingSessionEntity.toDto() =
    SessionDto(
        id = id,
        title = title,
        tags = tags,
        audioFilePath = audioFilePath,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        audioFormat = audioFormat,
        sampleRateHz = sampleRateHz,
        encodingBitRate = encodingBitRate,
        channelCount = channelCount,
        waveformSamples = waveformSamples,
        status = status,
        isArchived = isArchived,
        estimatedTranscriptionCostUsd = estimatedTranscriptionCostUsd,
        folderId = folderId,
        locationLat = locationLat,
        locationLng = locationLng,
        locationLabel = locationLabel,
        sentimentJson = sentimentJson,
        topicsJson = topicsJson,
        mode = mode,
        customTypeId = customTypeId,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** [audioFilePath] is supplied by the caller: the path inside the backup is not valid here. */
internal fun SessionDto.toEntity(audioFilePath: String) =
    RecordingSessionEntity(
        id = id,
        title = title,
        tags = tags,
        audioFilePath = audioFilePath,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        audioFormat = audioFormat,
        sampleRateHz = sampleRateHz,
        encodingBitRate = encodingBitRate,
        channelCount = channelCount,
        waveformSamples = waveformSamples,
        status = status,
        isArchived = isArchived,
        estimatedTranscriptionCostUsd = estimatedTranscriptionCostUsd,
        folderId = folderId,
        locationLat = locationLat,
        locationLng = locationLng,
        locationLabel = locationLabel,
        sentimentJson = sentimentJson,
        topicsJson = topicsJson,
        mode = mode,
        customTypeId = customTypeId,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun TranscriptEntity.toDto() = TranscriptDto(id, sessionId, content, type, sourceTranscriptId, providerType, transformProfileId, transformRunId, createdAt)

internal fun TranscriptDto.toEntity() = TranscriptEntity(id, sessionId, content, type, sourceTranscriptId, providerType, transformProfileId, transformRunId, createdAt)

internal fun TranscriptChunkEntity.toDto() = TranscriptChunkDto(id, sessionId, chunkIndex, totalChunks, status, text, createdAt)

internal fun TranscriptChunkDto.toEntity() = TranscriptChunkEntity(id, sessionId, chunkIndex, totalChunks, status, text, createdAt)

internal fun SpeakerSegmentEntity.toDto() = SpeakerSegmentDto(id, sessionId, speakerId, speakerLabel, personId, startMs, endMs)

internal fun SpeakerSegmentDto.toEntity() = SpeakerSegmentEntity(id, sessionId, speakerId, speakerLabel, personId, startMs, endMs)

internal fun SessionTaskEntity.toDto() = SessionTaskDto(id, sessionId, text, assignee, dueLabel, isDone, createdAt)

internal fun SessionTaskDto.toEntity() = SessionTaskEntity(id, sessionId, text, assignee, dueLabel, isDone, createdAt)

internal fun TransformRunEntity.toDto() = TransformRunDto(id, sessionId, profileId, inputTranscriptId, outputTranscriptId, status, errorMessage, startedAt, completedAt)

internal fun TransformRunDto.toEntity() = TransformRunEntity(id, sessionId, profileId, inputTranscriptId, outputTranscriptId, status, errorMessage, startedAt, completedAt)

internal fun TransformProfileEntity.toDto() = TransformProfileDto(id, name, description, systemPrompt, steps, providerType, isDefault, modelName, iconName, colorName, mode, useCount)

internal fun TransformProfileDto.toEntity() = TransformProfileEntity(id, name, description, systemPrompt, steps, providerType, isDefault, modelName, iconName, colorName, mode, useCount)

internal fun FolderEntity.toDto() = FolderDto(id, name, parentFolderId, createdAt)

internal fun FolderDto.toEntity() = FolderEntity(id, name, parentFolderId, createdAt)

internal fun PersonEntity.toDto() = PersonDto(id, name, voiceEmbeddingJson, createdAt)

internal fun PersonDto.toEntity() = PersonEntity(id, name, voiceEmbeddingJson, createdAt)

internal fun CustomRecordingTypeEntity.toDto() = CustomRecordingTypeDto(id, name, defaultProfileId, createdAt, iconName)

internal fun CustomRecordingTypeDto.toEntity() = CustomRecordingTypeEntity(id, name, defaultProfileId, createdAt, iconName)

internal fun ProviderConfigEntity.toDto() = ProviderConfigDto(id, providerType, isEnabled, modelName)

/** [apiKeyAlias] is restored empty — see [ProviderConfigDto]. */
internal fun ProviderConfigDto.toEntity() = ProviderConfigEntity(id, providerType, isEnabled, modelName, apiKeyAlias = "")
