package com.maya.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maya.agent.MainActivity
import com.maya.agent.R
import com.maya.agent.agent.IntentRouter
import com.maya.agent.agent.ToolExecutor
import com.maya.agent.data.ChatStore
import com.maya.agent.voice.VoiceController
import com.maya.agent.voice.WakeWordEngine

/**
 * Keeps Maya within reach: persistent notification, on-device wake word,
 * phone link, start-on-boot. Pause/Stop in the notification fully releases
 * the mic.
 *
 * Wake flow (merged v3 + model-download engine): engine hears "maya" on its
 * audio thread -> posted to main -> spotting stops (mic handoff) -> orb goes
 * LISTENING -> app comes forward -> spoken ack -> one SpeechRecognizer
 * command turn -> IntentRouter -> ToolExecutor -> spoken ack -> spotting
 * resumes. The engine only runs when the owner enabled it in Settings (model
 * downloaded after consent) and the service is not paused.
 */
class MayaForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "maya_background"
        const val NOTIFICATION_ID = 42
        const val ACTION_PAUSE = "com.maya.agent.action.PAUSE"
        const val ACTION_RESUME = "com.maya.agent.action.RESUME"
        const val ACTION_STOP = "com.maya.agent.action.STOP"
        var running = false
            private set
        var paused = false
            private set
    }

    private var wake: WakeWordEngine? = null
    private var phoneLink: com.maya.agent.calls.PhoneLink? = null
    private var voice: VoiceController? = null
    private var executor: ToolExecutor? = null
    @Volatile private var commandTurnActive = false
    private val mainHandler by lazy { Handler(mainLooper) }

    private fun syncPhoneLink() {
        val want = !paused && getSharedPreferences("maya_state", MODE_PRIVATE).getBoolean("phone_link_enabled", false)
        if (want) { if (phoneLink == null) phoneLink = com.maya.agent.calls.PhoneLink(this); phoneLink?.start() }
        else { phoneLink?.stop(); phoneLink = null }
    }

    private fun wakeWordWanted() =
        !paused && !commandTurnActive && getSharedPreferences("maya_state", MODE_PRIVATE).getBoolean("wakeword_enabled", false)

    private fun syncWakeWord() {
        if (wakeWordWanted()) {
            if (wake == null) {
                wake = WakeWordEngine(
                    this,
                    onWake = { mainHandler.post { onWakeWord() } },
                    onFailure = { msg ->
                        getSharedPreferences("maya_state", MODE_PRIVATE).edit().putBoolean("wakeword_enabled", false).apply()
                        getSystemService(NotificationManager::class.java).notify(45,
                            NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentTitle("Maya wake word stopped").setContentText(msg).build())
                    },
                )
            }
            wake?.start()
        } else {
            wake?.stop(); wake = null
        }
    }

    private fun onWakeWord() {
        if (commandTurnActive || paused) return
        commandTurnActive = true
        // Mic handoff: stop spotting before the recognizer opens the mic.
        wake?.stop()
        OrbState.setListening(true)
        // Bring the app forward over the lockscreen/other apps (overlay
        // permission, granted in onboarding, is what allows this from a
        // background service on Android 10+).
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .setAction(MainActivity.ACTION_WAKE)
            )
        } catch (_: Exception) { /* orb state still shows listening */ }
        voice?.speak("Haan, bolo")
        voice?.startListening()
    }

    private fun handleCommand(text: String) {
        val routed = IntentRouter.route(text)
        ChatStore.addUser(text)
        executor?.execute(routed) { ack ->
            ChatStore.addMaya(ack)
            voice?.speak(ack)
            // Give TTS a moment, then resume spotting.
            mainHandler.postDelayed(
                { endCommandTurn(resumeSpotting = true) },
                (ack.length * 60L).coerceIn(1500, 6000),
            )
        }
    }

    private fun endCommandTurn(resumeSpotting: Boolean) {
        commandTurnActive = false
        OrbState.setListening(false)
        if (resumeSpotting && running && !paused) syncWakeWord()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_maya), NotificationManager.IMPORTANCE_LOW)
        )
        executor = ToolExecutor(applicationContext)
        voice = VoiceController(
            applicationContext,
            onListeningChanged = { listening -> OrbState.setListening(listening) },
            onResult = { text -> handleCommand(text) },
            onError = { endCommandTurn(resumeSpotting = true) },
        )
        voice?.initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { paused = true; updateNotification(); syncWakeWord(); syncPhoneLink(); OrbState.setListening(false); return START_STICKY }
            ACTION_RESUME -> { paused = false; updateNotification(); syncWakeWord(); syncPhoneLink(); return START_STICKY }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        running = true
        startForegroundWithType()
        if (running) { syncWakeWord(); syncPhoneLink() }
        return START_STICKY
    }

    private fun startForegroundWithType() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android refused a microphone foreground service (no mic permission, or
            // not started from a visible app). Stop cleanly instead of crashing.
            running = false
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseIntent = PendingIntent.getService(
            this, 1, Intent(this, MayaForegroundService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, MayaForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            paused -> "Maya is paused"
            wake?.isRunning == true -> "Maya is listening - say 'Maya'"
            else -> getString(R.string.notification_maya_running)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Maya")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, if (paused) "Resume" else "Pause", pauseIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        running = false
        wake?.stop(); wake = null
        phoneLink?.stop(); phoneLink = null
        voice?.shutdown(); voice = null
        OrbState.setListening(false)
        super.onDestroy()
    }
}
