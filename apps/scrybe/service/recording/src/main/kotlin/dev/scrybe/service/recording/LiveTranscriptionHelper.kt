package dev.scrybe.service.recording

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiveTranscriptionHelper(
    private val context: Context,
    private val onTextUpdate: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val accumulatedText = StringBuilder()
    private var active = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d(TAG, "Speech recognition not available on this device")
            return
        }
        active = true
        scope.launch { startSession() }
    }

    fun stop() {
        active = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        scope.cancel()
    }

    private fun startSession() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (!text.isNullOrBlank()) {
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                        onTextUpdate(accumulatedText.toString())
                    }
                    if (active) scope.launch { startSession() }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()
                    if (!partial.isNullOrBlank()) {
                        val preview = buildString {
                            if (accumulatedText.isNotEmpty()) {
                                append(accumulatedText)
                                append(" ")
                            }
                            append(partial)
                        }
                        onTextUpdate(preview)
                    }
                }

                override fun onError(error: Int) {
                    Log.d(TAG, "SpeechRecognizer error $error — restarting if still active")
                    if (active) scope.launch { startSession() }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                },
            )
        }
    }

    private companion object {
        const val TAG = "LiveTranscription"
    }
}
