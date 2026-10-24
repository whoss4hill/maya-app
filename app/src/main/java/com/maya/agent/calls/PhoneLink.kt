package com.maya.agent.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import com.maya.agent.BuildConfig
import com.maya.agent.MayaApp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Owner-only push channel from the Maya Worker (Telegram "call ..." commands). One authenticated socket,
 * opened only while the foreground service runs, the owner enabled "phone_link_enabled" (default OFF) and
 * Maya is not paused. Reconnects with backoff (5 s doubling to 5 min). No polling of commands: the server pushes.
 * Every command goes through PhoneCommand (expiry, replay, permission checks) and is acknowledged honestly.
 */
class PhoneLink(private val context: Context) {
    companion object { @Volatile var connected = false }
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    @Volatile private var ws: WebSocket? = null
    @Volatile private var wanted = false
    private var backoffMs = 5_000L

    private val deps = PhoneCommand.Deps(
        hasCallPermission = { granted(Manifest.permission.CALL_PHONE) },
        hasContactsPermission = { granted(Manifest.permission.READ_CONTACTS) },
        lookup = { DialCommand.lookup(context, it) },
        dial = { DialCommand.call(context, it) },
    )

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (wanted) return
        wanted = true; backoffMs = 5_000L; connect()
    }

    fun stop() {
        wanted = false
        ws?.close(1000, "stopped"); ws = null; connected = false
        main.removeCallbacksAndMessages(null)
    }

    private fun connect() {
        if (!wanted) return
        thread(name = "phone-link-connect") {
            val token = try { kotlinx.coroutines.runBlocking { MayaApp.from(context).auth.validAccessToken() } } catch (_: Exception) { null }
            if (token == null) { retry(); return@thread }  // signed out: wait, never connect without the owner's JWT
            val url = BuildConfig.BACKEND_URL.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/phone/ws"
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { backoffMs = 5_000L; connected = true }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val ack = PhoneCommand.handle(text, System.currentTimeMillis() / 1000, deps) ?: return
                    webSocket.send(JsonObject().apply {
                        addProperty("ack", ack.id); addProperty("status", ack.status); addProperty("text", ack.text)
                    }.toString())
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { connected = false; retry() }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { connected = false; retry() }
            })
        }
    }

    private fun retry() {
        if (!wanted) return
        val d = backoffMs; backoffMs = (backoffMs * 2).coerceAtMost(300_000L)
        main.postDelayed({ connect() }, d)
    }
}
