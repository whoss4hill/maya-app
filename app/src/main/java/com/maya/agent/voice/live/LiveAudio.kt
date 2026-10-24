package com.maya.agent.voice.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/** Mic capture: 16 kHz mono PCM16 -> base64 chunks to the Live session. */
class LiveMicCapture(private val onChunk: (String) -> Unit) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is gated by the calling screen
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_BYTES * 2),
        )
        record.startRecording()
        thread = Thread {
            val buf = ByteArray(CHUNK_BYTES)
            while (running.get()) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) onChunk(Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
            }
            runCatching { record.stop() }
            record.release()
        }.apply { name = "maya-live-mic"; start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(500)
        thread = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_BYTES = 3200 // 100 ms of 16 kHz PCM16
    }
}

/** Playback: streams 24 kHz mono PCM16 chunks to the speaker; flush() on barge-in. */
class LiveAudioPlayer {
    private var track: AudioTrack? = null

    private fun ensureTrack(): AudioTrack {
        track?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play(); track = it }
    }

    fun play(pcm: ByteArray) {
        ensureTrack().write(pcm, 0, pcm.size)
    }

    /** Barge-in: drop everything buffered so the model's interrupted speech stops now. */
    fun flush() {
        track?.let {
            it.pause()
            it.flush()
            it.play()
        }
    }

    fun release() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    companion object {
        const val SAMPLE_RATE = 24000
    }
}
