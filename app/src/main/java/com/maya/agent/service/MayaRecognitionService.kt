package com.maya.agent.service

import android.content.Intent
import android.speech.RecognitionService
import android.speech.RecognitionService.Callback
import android.speech.SpeechRecognizer

/**
 * Required stub: a VoiceInteractionService must name a RecognitionService to be
 * listed as a digital-assistant choice. Maya never performs hotword/audio
 * capture through this path (wake word is the offline Vosk engine in the
 * foreground service), so any attempt to use it is declined immediately.
 */
class MayaRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try { listener?.error(SpeechRecognizer.ERROR_CLIENT) } catch (_: Exception) {}
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
