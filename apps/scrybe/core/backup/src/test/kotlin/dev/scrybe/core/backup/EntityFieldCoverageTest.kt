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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Keeps the DTO mirror in [BackupDto] honest.
 *
 * The DTOs duplicate the Room entities on purpose — a backup is a wire format and must not change
 * because a column was renamed. The cost of that decision is drift: someone adds a field to an
 * entity, nothing fails to compile, and from then on backups quietly omit it. Nobody notices until
 * a restore comes back missing data, by which time the backups that matter were already written
 * without it.
 *
 * So the mirror is asserted rather than trusted. Each entity's primary-constructor parameter names
 * must be covered by its DTO. A field may be carried under the same name, or deliberately excluded
 * with a stated reason — but it cannot be forgotten.
 */
class EntityFieldCoverageTest {
    /**
     * Fields intentionally not carried in a backup, with why. Anything added here is a decision;
     * anything missing from both here and the DTO is a bug this test catches.
     */
    private val deliberatelyExcluded: Map<KClass<*>, Set<String>> =
        mapOf(
            // Names a key in the secure store, which does not travel with a backup. Restoring it
            // would make a provider look configured while failing on its first call.
            ProviderConfigEntity::class to setOf("apiKeyAlias"),
        )

    private fun assertCovered(
        entity: KClass<*>,
        dto: KClass<*>,
    ) {
        val entityFields =
            entity.primaryConstructor
                ?.parameters
                ?.mapNotNull { it.name }
                ?.toSet()
                .orEmpty()
        assertTrue("${entity.simpleName} has no primary constructor parameters", entityFields.isNotEmpty())

        val dtoFields =
            dto.primaryConstructor
                ?.parameters
                ?.mapNotNull { it.name }
                ?.toSet()
                .orEmpty()
        val excluded = deliberatelyExcluded[entity].orEmpty()
        val missing = entityFields - dtoFields - excluded

        assertEquals(
            "${entity.simpleName} has field(s) that ${dto.simpleName} does not carry. Either add " +
                "them to the DTO and its mappers, or record the omission in deliberatelyExcluded " +
                "with the reason. A backup that silently drops a column is the failure this test exists to stop.",
            emptySet<String>(),
            missing,
        )
    }

    @Test
    fun `every entity field is carried by its DTO or deliberately excluded`() {
        assertCovered(RecordingSessionEntity::class, SessionDto::class)
        assertCovered(TranscriptEntity::class, TranscriptDto::class)
        assertCovered(TranscriptChunkEntity::class, TranscriptChunkDto::class)
        assertCovered(SpeakerSegmentEntity::class, SpeakerSegmentDto::class)
        assertCovered(SessionTaskEntity::class, SessionTaskDto::class)
        assertCovered(TransformRunEntity::class, TransformRunDto::class)
        assertCovered(TransformProfileEntity::class, TransformProfileDto::class)
        assertCovered(FolderEntity::class, FolderDto::class)
        assertCovered(PersonEntity::class, PersonDto::class)
        assertCovered(CustomRecordingTypeEntity::class, CustomRecordingTypeDto::class)
        assertCovered(ProviderConfigEntity::class, ProviderConfigDto::class)
    }

    /**
     * The other direction. A DTO field with no matching entity field is dead weight in the format —
     * it will be written to every backup forever and never restored anywhere.
     */
    @Test
    fun `no DTO carries a field its entity does not have`() {
        val pairs =
            listOf(
                RecordingSessionEntity::class to SessionDto::class,
                TranscriptEntity::class to TranscriptDto::class,
                TranscriptChunkEntity::class to TranscriptChunkDto::class,
                SpeakerSegmentEntity::class to SpeakerSegmentDto::class,
                SessionTaskEntity::class to SessionTaskDto::class,
                TransformRunEntity::class to TransformRunDto::class,
                TransformProfileEntity::class to TransformProfileDto::class,
                FolderEntity::class to FolderDto::class,
                PersonEntity::class to PersonDto::class,
                CustomRecordingTypeEntity::class to CustomRecordingTypeDto::class,
                ProviderConfigEntity::class to ProviderConfigDto::class,
            )
        pairs.forEach { (entity, dto) ->
            val entityFields =
                entity.primaryConstructor
                    ?.parameters
                    ?.mapNotNull { it.name }
                    ?.toSet()
                    .orEmpty()
            val dtoFields =
                dto.primaryConstructor
                    ?.parameters
                    ?.mapNotNull { it.name }
                    ?.toSet()
                    .orEmpty()
            assertEquals(
                "${dto.simpleName} carries field(s) ${entity.simpleName} does not have",
                emptySet<String>(),
                dtoFields - entityFields,
            )
        }
    }
}
