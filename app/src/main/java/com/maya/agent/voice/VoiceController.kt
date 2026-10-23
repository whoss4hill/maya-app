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
    private val onListeningChanged: (Boolean) -> Unit = {},
    private val onResult: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
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

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Is phone pe speech recognition available nahi hai")
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
                        if (text != null) onResult(text) else onError("Kuch sunai nahi diya, phir bolo?")
                    }
                    override fun onError(error: Int) {
                        listeningNow = false; onListeningChanged(false)
                        onError("Awaaz saaf nahi aayi - ek baar phir bolo")
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
