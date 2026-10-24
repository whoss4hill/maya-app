package com.maya.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voice in/out: SpeechRecognizer for commands, TTS for Maya's replies.
 * A dedicated low-power wake-word engine (Porcupine-class) slots in later;
 * for now the foreground service keeps the listener reachable and the
 * orb/button starts a listening turn.
 */
class VoiceController(
    context: Context,
    // Settable: the Chat screen wires its own handlers while visible and
    // restores no-ops on dispose, so a stale screen can never receive a turn.
    var onListeningChanged: (Boolean) -> Unit = {},
    var onResult: (String) -> Unit = {},
    var onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                // Hinglish default: Hindi (India) handles Hindi + most English words naturally.
                tts?.setSpeechRate(1.05f)
                tts?.language = Locale("hi", "IN")
            }
        }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maya-tts")
    }

    val isListening: Boolean get() = listeningNow
    private var listeningNow = false

    /**
     * "Available" must mean a recognizer other than our own: the app declares a
     * stub RecognitionService (it exists so Android can list Maya as a default
     * assistant choice), and isRecognitionAvailable would count it.
     */
    private fun recognitionAvailable(): Boolean {
        return try {
            val intent = Intent(android.speech.RecognitionService.SERVICE_INTERFACE)
            appContext.packageManager.queryIntentServices(intent, 0)
                .any { it.serviceInfo?.packageName != appContext.packageName }
        } catch (_: Exception) {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        }
    }

    fun startListening() {
        if (listeningNow) return // one turn at a time; a re-start would busy-error the recognizer
        if (!recognitionAvailable()) {
            onError("Speech recognition is not available on this phone")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listeningNow = true; onListeningChanged(true)
                    }
                    override fun onResults(results: Bundle?) {
                        listeningNow = false; onListeningChanged(false)
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (text != null) onResult(text) else onError("I did not hear anything. Please say it again.")
                    }
                    override fun onError(error: Int) {
                        listeningNow = false; onListeningChanged(false)
                        onError(
                            when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission not granted. Allow it in Settings."
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet. Speech recognition needs a connection."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The mic is busy. Try again in a moment."
                                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything. Please say it again."
                                else -> "I could not make out the speech. Please say it again."
                            }
                        )
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Hinglish: allow English alongside Hindi (recognizer support varies by device).
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        listeningNow = false
        onListeningChanged(false)
    }

    fun shutdown() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
