package com.maya.agent.voice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream

/**
 * Always-on "Hey Maya" spotting, fully on-device via Vosk (open-source,
 * Apache 2.0). Runs inside MayaForegroundService so it keeps working with the
 * screen off and over other apps; the mic is ONLY open while this engine is
 * active, and the persistent notification's Pause stops it immediately.
 *
 * Approach: continuous small-model recognition, scanning partial results for
 * wake-phrase variants ("hey maya", "maya", "may a", ...). Grammar mode was
 * considered but "maya" is not guaranteed in the small model's vocabulary;
 * full-LM partial scanning tolerates phonetic spellings.
 *
 * Honest limits (also in the delivery report): continuous decoding costs
 * battery (roughly a few percent per hour on a modern phone) and English-model
 * spotting of a Hindi name will both miss some real calls (whispered/far mic)
 * and occasionally fire on similar sounds. Pause = mic fully off.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWake: () -> Unit,
) {
    companion object {
        private const val TAG = "WakeWordEngine"
        private const val MODEL_ASSET_DIR = "vosk-model-en"
        private const val SAMPLE_RATE = 16000.0f
        private const val WAKE_DEBOUNCE_MS = 8000L
        private val WAKE_REGEX = Regex(
            "\\b(hey|ok|okay|oye)?\\s*(maya|may\\s*a|my\\s*a|maiya|maia|maya's)\\b"
        )
        @Volatile var lastError: String? = null
            private set
        @Volatile var active: Boolean = false
            private set
    }

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var model: Model? = null
    @Volatile private var lastWakeAt = 0L
    @Volatile private var starting = false

    fun start() {
        if (active || starting) return
        starting = true
        Thread({
            try {
                val modelDir = unpackModelIfNeeded()
                if (modelDir == null) {
                    lastError = "Wake-word model missing (assets/vosk-model-en)"
                    Log.w(TAG, lastError ?: "")
                    starting = false
                    return@Thread
                }
                val m = Model(modelDir.absolutePath)
                model = m
                val rec = Recognizer(m, SAMPLE_RATE)
                recognizer = rec
                val service = SpeechService(rec, SAMPLE_RATE)
                speechService = service
                service.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        hypothesis?.let { if (matchesWake(extractText(it))) fireWake() }
                    }
                    override fun onResult(hypothesis: String?) {
                        hypothesis?.let { if (matchesWake(extractText(it))) fireWake() }
                    }
                    override fun onFinalResult(hypothesis: String?) {
                        hypothesis?.let { if (matchesWake(extractText(it))) fireWake() }
                    }
                    override fun onError(exception: Exception?) {
                        lastError = exception?.message
                    }
                    override fun onTimeout() {
                        // SpeechService auto-restarts; nothing to do.
                    }
                })
                active = true
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "start failed", e)
            } finally {
                starting = false
            }
        }, "maya-wakeword-init").start()
    }

    private fun fireWake() {
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < WAKE_DEBOUNCE_MS) return
        lastWakeAt = now
        // Pause spotting while the command turn runs so Maya doesn't hear herself.
        speechService?.setPause(true)
        onWake()
    }

    /** Resume spotting after a command turn finishes. */
    fun resume() {
        speechService?.setPause(false)
    }

    fun stop() {
        active = false
        try {
            speechService?.apply { setPause(true); stop(); shutdown() }
        } catch (_: Exception) {}
        speechService = null
        recognizer?.close(); recognizer = null
        model?.close(); model = null
    }

    private fun extractText(json: String): String = try {
        val obj = JSONObject(json)
        obj.optString("partial").ifBlank { obj.optString("text") }.lowercase()
    } catch (_: Exception) { "" }

    private fun matchesWake(text: String): Boolean =
        text.isNotBlank() && WAKE_REGEX.containsMatchIn(text)

    /** Copy the bundled model out of assets once (assets are read-only). */
    private fun unpackModelIfNeeded(): File? {
        val target = File(context.filesDir, MODEL_ASSET_DIR)
        val marker = File(target, ".unpacked-v1")
        if (marker.exists()) return target
        return try {
            copyAssetDir(MODEL_ASSET_DIR, target)
            marker.writeText("ok")
            target
        } catch (e: Exception) {
            Log.e(TAG, "model unpack failed", e)
            target.deleteRecursively()
            null
        }
    }

    private fun copyAssetDir(assetPath: String, outDir: File) {
        val entries = context.assets.list(assetPath) ?: return
        if (entries.isEmpty()) {
            outDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outDir).use { output -> input.copyTo(output) }
            }
        } else {
            outDir.mkdirs()
            for (entry in entries) copyAssetDir("$assetPath/$entry", File(outDir, entry))
        }
    }
}
