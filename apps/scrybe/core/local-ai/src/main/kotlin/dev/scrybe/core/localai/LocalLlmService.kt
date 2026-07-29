package dev.scrybe.core.localai

import android.content.Context
import com.twobits.localai.LiteRtLmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.transforms.ClusterSuggestion
import dev.scrybe.core.transforms.SessionSummary
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val modelManager: LocalModelManager,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) {
        private suspend fun generate(prompt: String): String {
            val model = preferencesDataStore.localLlmModel.first()
            val modelFile =
                modelManager.llmModelFile(model)
                    ?: modelManager.anyLlmReady()?.let { modelManager.llmModelFile(it) }
                    ?: error("No local model downloaded. Go to Settings → Provider → Local to download one.")
            return LiteRtLmEngine(context, modelFile).use { engine ->
                engine.generate(prompt)
            }
        }

        suspend fun suggestTitle(
            transcriptText: String,
            currentTitle: String,
        ): Result<String> =
            runCatching {
                require(transcriptText.isNotBlank()) { "A transcript is required to suggest a title" }
                val prompt =
                    """
                    Suggest a concise title (2–7 words, title case) for an audio recording.
                    Current title: ${currentTitle.ifBlank { "(untitled)" }}
                    Transcript: ${transcriptText.take(600)}
                    Respond with ONLY the title text, nothing else.
                    """.trimIndent()
                generate(prompt).trim().removeSurrounding("\"")
            }

        suspend fun suggestClusters(
            sessions: List<SessionSummary>,
            existingFolderNames: List<String>,
            commonTags: List<String>,
        ): Result<List<ClusterSuggestion>> =
            runCatching {
                require(sessions.isNotEmpty()) { "At least one recording is required for clustering" }
                val sessionLines =
                    sessions.joinToString("\n") { s ->
                        "- id=${s.id} title=\"${s.title}\" tags=${s.tags} preview=${s.transcriptPreview?.take(80)}"
                    }
                val prompt =
                    """
                    Group these recordings into folders. Return ONLY lines in this exact format:
                    folder_name|id1,id2,id3
                    One line per folder. Use existing folder names when appropriate: ${existingFolderNames.joinToString()}.
                    Common tags: ${commonTags.joinToString()}.
                    Recordings:
                    $sessionLines
                    """.trimIndent()
                val raw = generate(prompt)
                raw
                    .lines()
                    .filter { it.contains("|") }
                    .mapNotNull { line ->
                        val parts = line.split("|", limit = 2)
                        if (parts.size == 2) {
                            val ids = parts[1].split(",").map { it.trim() }.filter { it.isNotBlank() }
                            if (ids.isNotEmpty()) ClusterSuggestion(folderName = parts[0].trim(), sessionIds = ids) else null
                        } else {
                            null
                        }
                    }
            }

        suspend fun suggestTags(
            title: String,
            transcriptText: String,
            existingTags: List<String>,
        ): Result<List<String>> =
            runCatching {
                require(title.isNotBlank() || transcriptText.isNotBlank()) {
                    "A title or transcript is required to suggest tags"
                }
                val prompt =
                    """
                    Suggest 3–6 short tags for this recording. Return ONLY a comma-separated list.
                    Title: $title
                    Existing tags: ${existingTags.joinToString()}
                    Transcript: ${transcriptText.take(400)}
                    """.trimIndent()
                generate(prompt)
                    .split(",")
                    .map { it.trim().lowercase().removePrefix("#") }
                    .filter { it.isNotBlank() }
                    .take(6)
            }

        suspend fun identifySpeakerTurns(transcriptText: String): Result<List<String>> =
            runCatching {
                require(transcriptText.isNotBlank()) { "Transcript is required" }
                val prompt =
                    """
                    Identify speaker turns in this transcript. Assign consistent speaker IDs like SPEAKER_1, SPEAKER_2, etc.
                    Return ONLY a comma-separated list of speaker IDs, one per detected turn, in order.
                    Example: SPEAKER_1,SPEAKER_2,SPEAKER_1,SPEAKER_2
                    Transcript: ${transcriptText.take(800)}
                    """.trimIndent()
                generate(prompt)
                    .split(",")
                    .map { it.trim().uppercase() }
                    .filter { it.startsWith("SPEAKER_") }
                    .ifEmpty { listOf("SPEAKER_1") }
            }

        suspend fun analyzeSentiment(
            transcriptText: String,
            durationMs: Long,
        ): Result<String> =
            runCatching {
                require(transcriptText.isNotBlank()) { "Transcript is required" }
                val prompt =
                    """
                    Analyze the sentiment of this transcript. The recording is ${durationMs}ms long.
                    Return ONLY JSON array: [{"startMs":0,"endMs":$durationMs,"sentiment":"NEUTRAL"}]
                    Use POSITIVE, NEGATIVE, or NEUTRAL. Cover the entire duration without gaps.
                    Transcript: ${transcriptText.take(600)}
                    """.trimIndent()
                val raw =
                    generate(prompt)
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                raw.ifBlank { """[{"startMs":0,"endMs":$durationMs,"sentiment":"NEUTRAL"}]""" }
            }

        suspend fun extractTopics(
            transcriptText: String,
            durationMs: Long,
        ): Result<String> =
            runCatching {
                require(transcriptText.isNotBlank()) { "Transcript is required" }
                val prompt =
                    """
                    Extract key topics from this transcript. Estimate when each topic is discussed within ${durationMs}ms.
                    Return ONLY JSON array: [{"timeMs":1000,"label":"topic name"}]
                    Keep labels short (2–4 words). Return 5–15 topics.
                    Transcript: ${transcriptText.take(1200)}
                    """.trimIndent()
                val raw =
                    generate(prompt)
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                raw.ifBlank { "[]" }
            }
    }
