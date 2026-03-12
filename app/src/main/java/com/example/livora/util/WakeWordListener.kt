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

    private val exactWakeWords = listOf(
        "jarvis", "jarvas", "jarves", "jarves", "jarvice", "jarvies",
        "jervis", "jervas", "jervice",
        "travis", "trevis",
        "service", "surfaces",
        "jarves", "charvez", "charvis", "chavez",
        "java", "javas",
        "jar vis", "jar vice",
        "jovis", "jovas",
        "garvis", "garvas",
        "harvest", "harvis",
        "nervous", "jarness",
        "jars", "jar",
        "darvish", "darvis",
        "device", "chassis",
        "carvis", "karvis"
    )

    private val wakeWordPrefixes = listOf(
        "jarv", "jerv", "jarb", "jerb",
        "trav", "trev",
        "garv", "garb",
        "charv", "harv", "darv", "carv", "karv"
    )

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
                    for (candidate in matches) {
                        val text = candidate.lowercase()
                        if (matchesWakeWord(text)) {
                            LivoraLogger.debug(TAG, "Wake word detected: $text")
                            onWakeWordDetected?.invoke()
                            return
                        }
                    }
                }
                if (isRunning) {
                    startRecognizer()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    for (candidate in partial) {
                        val text = candidate.lowercase()
                        if (matchesWakeWord(text)) {
                            LivoraLogger.debug(TAG, "Wake word partial detected: $text")
                            isRunning = false
                            speechRecognizer?.stopListening()
                            onWakeWordDetected?.invoke()
                            return
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
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

    private fun matchesWakeWord(text: String): Boolean {
        if (exactWakeWords.any { text.contains(it) }) return true
        val words = text.split(" ")
        for (word in words) {
            if (wakeWordPrefixes.any { word.startsWith(it) }) return true
            if (word.contains("arvi") || word.contains("arve") || word.contains("arva")) return true
        }
        return false
    }

    companion object {
        private const val TAG = "Livora.WakeWord"
    }
}
