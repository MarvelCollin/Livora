package com.example.livora.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class WakeWordListener(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var onWakeWordDetected: (() -> Unit)? = null

    fun start(onDetected: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        onWakeWordDetected = onDetected
        isRunning = true
        startRecognizer()
    }

    private fun startRecognizer() {
        if (!isRunning) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (isRunning) {
                    startRecognizer()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].lowercase()
                    if (text.contains("jarvis")) {
                        LivoraLogger.debug(TAG, "Wake word detected: $text")
                        onWakeWordDetected?.invoke()
                    }
                }
                if (isRunning) {
                    startRecognizer()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    val text = partial[0].lowercase()
                    if (text.contains("jarvis")) {
                        LivoraLogger.debug(TAG, "Wake word partial detected: $text")
                        isRunning = false
                        speechRecognizer?.stopListening()
                        onWakeWordDetected?.invoke()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stop() {
        isRunning = false
        onWakeWordDetected = null
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun destroy() {
        stop()
    }

    companion object {
        private const val TAG = "Livora.WakeWord"
    }
}
