package com.maya.agent.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Offline "Maya" listener. Only runs inside the visible foreground service while
 * the owner has it switched on and not paused. Audio never leaves the phone and
 * is not stored: each buffer is fed to the recognizer and discarded.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWake: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning get() = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onFailure("Mic permission nahi hai"); return
        }
        if (!VoskModelStore.isInstalled(context)) { onFailure("Wake word model download nahi hua"); return }
        running = true
        thread = Thread({
            var model: Model? = null
            var rec: Recognizer? = null
            var audio: AudioRecord? = null
            try {
                val rate = 16000
                model = Model(VoskModelStore.modelDir(context).absolutePath)
                rec = Recognizer(model, rate.toFloat())
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
                if (audio.state != AudioRecord.STATE_INITIALIZED) { onFailure("Mic open nahi hua"); return@Thread }
                audio.startRecording()
                val buf = ByteArray(4096)
                while (running) {
                    val n = audio.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    val hit = if (rec.acceptWaveForm(buf, n)) JSONObject(rec.result).optString("text")
                              else JSONObject(rec.partialResult).optString("partial")
                    if (matches(hit)) { rec.reset(); onWake() }
                }
            } catch (e: Throwable) {
                onFailure("Wake word band: ${e.javaClass.simpleName}")
            } finally {
                try { audio?.stop() } catch (_: Exception) {}
                audio?.release(); rec?.close(); model?.close()
                running = false
            }
        }, "maya-wake").also { it.start() }
    }

    fun stop() { running = false; thread?.join(1500); thread = null }

    companion object {
        private val WORDS = listOf("maya", "maia", "mya", "maaya")
        fun matches(text: String): Boolean {
            val t = text.lowercase()
            return WORDS.any { t.split(' ').contains(it) }
        }
    }
}
