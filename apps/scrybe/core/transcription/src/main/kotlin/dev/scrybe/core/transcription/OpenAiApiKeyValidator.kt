package dev.scrybe.core.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiApiKeyValidator
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        suspend fun validate(apiKey: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    require(apiKey.isNotBlank()) { "Enter an API key first" }

                    val request =
                        Request.Builder()
                            .url("https://api.openai.com/v1/models")
                            .header("Authorization", "Bearer ${apiKey.trim()}")
                            .header("Accept", "application/json")
                            .get()
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        when {
                            response.isSuccessful -> Unit
                            response.code == 401 -> throw IllegalStateException("OpenAI rejected this API key")
                            response.code == 429 -> throw IllegalStateException("OpenAI rate-limited validation. Try again shortly.")
                            else -> {
                                val errorBody = response.body?.string().orEmpty().replace("\n", " ").take(300)
                                throw IllegalStateException(
                                    "OpenAI validation failed: ${response.code} ${response.message}" +
                                        if (errorBody.isNotBlank()) " - $errorBody" else "",
                                )
                            }
                        }
                    }
                }
            }
    }
