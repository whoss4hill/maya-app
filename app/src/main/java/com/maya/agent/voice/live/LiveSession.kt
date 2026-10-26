package com.maya.agent.voice.live

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * One Gemini Live session over an ephemeral token (BidiGenerateContentConstrained).
 * The token comes from the backend mint endpoint (POST /v1/live/token); the
 * long-lived Gemini key never touches the app. Audio never passes through our
 * backend - the app connects to Google directly.
 *
 * Verified against the real free-tier project on 2026-10-24: mint -> connect
 * -> setupComplete -> audio chunks -> turnComplete.
 */
class LiveSession(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // no read timeout: long-lived socket
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
) {
    interface Listener {
        fun onSetupComplete() {}
        /** Raw PCM16 audio at 24 kHz from the model. */
        fun onAudio(pcm: ByteArray) {}
        fun onOutputTranscript(text: String) {}
        /** User barged in: flush playback immediately. */
        fun onInterrupted() {}
        fun onTurnComplete() {}
        fun onInputTranscript(text: String) {}
        fun onToolCall(calls: List<LiveProtocol.ServerEvent.FunctionCall>) {}
        /** Server is about to close (~10 min connection cap): reconnect with the resumption handle. */
        fun onGoAway() {}
        fun onResumptionHandle(handle: String) {}
        fun onClosed(reason: String) {}
        fun onFailure(t: Throwable) {}
    }

    private var ws: WebSocket? = null
    var resumptionHandle: String? = null
        private set

    fun connect(wsUrl: String, setupJson: String, listener: Listener) {
        ws = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send(setupJson)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                for (event in LiveProtocol.parseServerMessage(text)) {
                    when (event) {
                        is LiveProtocol.ServerEvent.SetupComplete -> listener.onSetupComplete()
                        is LiveProtocol.ServerEvent.ModelTurn -> event.audioChunks.forEach { listener.onAudio(it) }
                        is LiveProtocol.ServerEvent.OutputTranscript -> listener.onOutputTranscript(event.text)
                        is LiveProtocol.ServerEvent.InputTranscript -> listener.onInputTranscript(event.text)
                        is LiveProtocol.ServerEvent.Interrupted -> listener.onInterrupted()
                        is LiveProtocol.ServerEvent.TurnComplete -> listener.onTurnComplete()
                        is LiveProtocol.ServerEvent.ToolCall -> listener.onToolCall(event.calls)
                        is LiveProtocol.ServerEvent.GoAway -> listener.onGoAway()
                        is LiveProtocol.ServerEvent.ResumptionHandle -> {
                            resumptionHandle = event.handle
                            listener.onResumptionHandle(event.handle)
                        }
                        else -> Unit
                    }
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed("$code $reason")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                listener.onFailure(t)
            }
        })
    }

    fun sendAudio(base64Pcm16: String): Boolean = ws?.send(LiveProtocol.buildAudioChunk(base64Pcm16)) == true

    fun sendText(text: String): Boolean = ws?.send(LiveProtocol.buildClientText(text)) == true

    fun sendToolResponse(callId: String, name: String, resultJson: String): Boolean =
        ws?.send(LiveProtocol.buildToolResponse(callId, name, resultJson)) == true

    fun close() {
        ws?.close(1000, "done")
        ws = null
    }
}
