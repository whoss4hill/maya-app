package com.maya.agent.voice.live

import com.maya.agent.data.backend.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Orchestrates one Free-talk voice session (engine layer only - no UI):
 *  1. Mint a fresh ephemeral token from the backend (POST /v1/live/token,
 *     Supabase owner JWT; server locks uses:1 + model + AUDIO + resumption,
 *     30-min expiry, 1-min connect window).
 *  2. Open the returned ws_url directly (audio never touches our backend).
 *  3. Run mic in / speaker out; flush playback on barge-in.
 *  4. On GoAway (~10-min connection cap) reconnect with the resumption
 *     handle while the token is still valid; bounded retries, honest stop.
 *
 * Voice identity is deliberately NOT defaulted here (user decision pending):
 * the caller must pass the approved prebuilt voice name.
 */
class LiveSessionManager(
    private val systemInstruction: String,
    private val voiceName: String,
    private val bearerTokenProvider: suspend () -> String?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    enum class State { IDLE, CONNECTING, ACTIVE, ENDED }

    interface Callback {
        fun onState(state: State) {}
        fun onError(message: String) {}
        /** Route to the existing ToolExecutor; return a JSON object string, e.g. {"result":"..."}. */
        suspend fun onToolCall(call: LiveProtocol.ServerEvent.FunctionCall): String
    }

    var state: State = State.IDLE
        private set

    private var session: LiveSession? = null
    private var mic: LiveMicCapture? = null
    private var player: LiveAudioPlayer? = null
    private var wsUrl: String? = null
    private var model: String = "models/gemini-3.8-live"
    private var reconnectsLeft = MAX_RECONNECTS

    fun start(callback: Callback) {
        if (state == State.CONNECTING || state == State.ACTIVE) return
        setState(State.CONNECTING, callback)
        scope.launch {
            val bearer = bearerTokenProvider()
            if (bearer.isNullOrBlank()) {
                fail(callback, "Sign in pending - live voice needs your account")
                return@launch
            }
            val minted = runCatching { BackendClient.api.liveToken("Bearer $bearer") }.getOrElse {
                fail(callback, "Live token mint failed: ${it.message ?: "network error"}")
                return@launch
            }
            wsUrl = minted.ws_url
            model = minted.model ?: model
            if (wsUrl.isNullOrBlank() || minted.token.isNullOrBlank()) {
                fail(callback, "Live token response incomplete")
                return@launch
            }
            openSession(callback)
        }
    }

    private fun openSession(callback: Callback) {
        val url = wsUrl ?: return fail(callback, "Missing Live ws_url")
        reconnectsLeft = MAX_RECONNECTS
        val s = LiveSession()
        session = s
        val setup = LiveProtocol.buildSetup(
            model = model,
            systemInstruction = systemInstruction,
            voiceName = voiceName,
        )
        s.connect(url, setup, object : LiveSession.Listener {
            override fun onSetupComplete() {
                setState(State.ACTIVE, callback)
                player = LiveAudioPlayer()
                val m = LiveMicCapture { chunk -> session?.sendAudio(chunk) }
                mic = m
                m.start()
            }
            override fun onAudio(pcm: ByteArray) { player?.play(pcm) }
            override fun onInterrupted() { player?.flush() }
            override fun onToolCall(calls: List<LiveProtocol.ServerEvent.FunctionCall>) {
                scope.launch {
                    for (call in calls) {
                        val result = runCatching { callback.onToolCall(call) }
                            .getOrElse { "{\"result\":\"Tool failed: ${it.message ?: "error"}\"}" }
                        session?.sendToolResponse(call.id, call.name, result)
                    }
                }
            }
            override fun onGoAway() { reconnect(callback) }
            override fun onClosed(reason: String) {
                if (state == State.ACTIVE) reconnect(callback)
            }
            override fun onFailure(t: Throwable) {
                if (state == State.ACTIVE) reconnect(callback) else fail(callback, t.message ?: "connection failed")
            }
        })
    }

    /** Reconnect with the captured resumption handle while the token is valid (docs: ~2 h handle, same token OK within expireTime). */
    private fun reconnect(callback: Callback) {
        if (state == State.ENDED) return
        if (reconnectsLeft <= 0) {
            fail(callback, "Voice connection dropped - tap to start again")
            return
        }
        reconnectsLeft--
        val handle = session?.resumptionHandle
        val url = wsUrl ?: return fail(callback, "Voice connection dropped - tap to start again")
        session?.close()
        setState(State.CONNECTING, callback)
        val s = LiveSession()
        session = s
        val setup = LiveProtocol.buildSetup(
            model = model,
            systemInstruction = systemInstruction,
            voiceName = voiceName,
            resumptionHandle = handle,
        )
        s.connect(url, setup, object : LiveSession.Listener {
            override fun onSetupComplete() { setState(State.ACTIVE, callback) }
            override fun onAudio(pcm: ByteArray) { player?.play(pcm) }
            override fun onInterrupted() { player?.flush() }
            override fun onToolCall(calls: List<LiveProtocol.ServerEvent.FunctionCall>) {
                scope.launch {
                    for (call in calls) {
                        val result = runCatching { callback.onToolCall(call) }
                            .getOrElse { "{\"result\":\"Tool failed: ${it.message ?: "error"}\"}" }
                        session?.sendToolResponse(call.id, call.name, result)
                    }
                }
            }
            override fun onGoAway() { reconnect(callback) }
            override fun onClosed(reason: String) { if (state == State.ACTIVE) reconnect(callback) }
            override fun onFailure(t: Throwable) { if (state == State.ACTIVE) reconnect(callback) else fail(callback, t.message ?: "connection failed") }
        })
    }

    fun end() {
        setState(State.ENDED, null)
        mic?.stop(); mic = null
        player?.release(); player = null
        session?.close(); session = null
        scope.cancel()
    }

    private fun setState(s: State, callback: Callback?) {
        state = s
        callback?.onState(s)
    }

    private fun fail(callback: Callback, message: String) {
        setState(State.ENDED, callback)
        mic?.stop(); mic = null
        player?.release(); player = null
        session?.close(); session = null
        callback.onError(message)
    }

    companion object {
        const val MAX_RECONNECTS = 2
    }
}
