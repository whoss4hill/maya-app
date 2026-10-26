package com.maya.agent.voice.live

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks the new-recipient number read-back with Android's own TTS, so the app (not the model) knows the exact
 * digits were spoken. onDone fires only when the utterance finished normally; any error/stop calls onFailed.
 */
object ReadbackSpeaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failedInit = false
    private val queue = ArrayDeque<Triple<String, () -> Unit, () -> Unit>>()
    private val active = HashMap<String, Pair<() -> Unit, () -> Unit>>()
    private var seq = 0
    private var languageSet = false

    /** Test hook: forget the engine so each test starts clean. */
    @Synchronized internal fun resetForTest() { tts = null; ready = false; failedInit = false; languageSet = false; queue.clear(); active.clear() }

    @Synchronized fun speak(app: Context, text: String, onDone: () -> Unit, onFailed: () -> Unit): Boolean {
        if (failedInit) return false
        if (tts == null) {
            tts = TextToSpeech(app) { status ->
                synchronized(this) {
                    if (status == TextToSpeech.SUCCESS) {
                        ready = true
                        while (queue.isNotEmpty()) { val (t, d, f) = queue.removeFirst(); start(t, d, f) }
                    } else {
                        failedInit = true
                        while (queue.isNotEmpty()) queue.removeFirst().third()
                    }
                }
            }
        }
        tts?.setOnUtteranceProgressListener(listener)
        if (ready) start(text, onDone, onFailed) else queue.addLast(Triple(text, onDone, onFailed))
        return true
    }

    private fun start(text: String, onDone: () -> Unit, onFailed: () -> Unit) {
        val id = "readback-" + (++seq)
        active.clear() // only the newest read-back may complete
        active[id] = onDone to onFailed
        if (!languageSet) { tts?.setLanguage(Locale("en", "IN")); languageSet = true }
        val r = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (r != TextToSpeech.SUCCESS) { active.remove(id); onFailed() }
    }

    @Synchronized private fun take(id: String?): Pair<() -> Unit, () -> Unit>? = if (id == null) null else active.remove(id)

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) { take(utteranceId)?.first?.invoke() }
        @Deprecated("deprecated") override fun onError(utteranceId: String?) { take(utteranceId)?.second?.invoke() }
        override fun onStop(utteranceId: String?, interrupted: Boolean) { take(utteranceId)?.second?.invoke() }
    }

    @Synchronized fun stop() { active.clear(); queue.clear(); tts?.stop() }
}
