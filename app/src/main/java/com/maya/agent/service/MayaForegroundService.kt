package com.maya.agent.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.maya.agent.MainActivity
import com.maya.agent.R
import com.maya.agent.agent.IntentRouter
import com.maya.agent.agent.ToolExecutor
import com.maya.agent.data.ChatStore
import com.maya.agent.voice.VoiceController
import com.maya.agent.voice.WakeWordEngine

/**
 * Keeps Maya within reach: persistent notification, always-on on-device
 * "Hey Maya" spotting (WakeWordEngine), start-on-boot. Pause/Stop in the
 * notification fully releases the mic.
 *
 * Wake flow: engine hears "hey maya" -> orb goes to LISTENING -> Maya says
 * "Haan, bolo" -> one SpeechRecognizer command turn -> IntentRouter ->
 * ToolExecutor -> spoken ack -> spotting resumes.
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

    private var wakeEngine: WakeWordEngine? = null
    private var voice: VoiceController? = null
    private var executor: ToolExecutor? = null
    @Volatile private var commandTurnActive = false

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
            onListeningChanged = { listening ->
                OrbState.setListening(listening)
            },
            onResult = { text -> handleCommand(text) },
            onError = { endCommandTurn(resumeSpotting = true) },
        )
        voice?.initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                paused = true
                wakeEngine?.stop()
                OrbState.setListening(false)
                updateNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                updateNotification()
                startWakeEngineIfAllowed()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        running = true
        startForegroundWithType()
        startWakeEngineIfAllowed()
        return START_STICKY
    }

    private fun micGranted(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun startWakeEngineIfAllowed() {
        if (paused || !micGranted()) return
        if (wakeEngine == null) {
            wakeEngine = WakeWordEngine(applicationContext) { onWakeWord() }
        }
        wakeEngine?.start()
    }

    private fun onWakeWord() {
        if (commandTurnActive) return
        commandTurnActive = true
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
            android.os.Handler(mainLooper).postDelayed(
                { endCommandTurn(resumeSpotting = true) },
                (ack.length * 60L).coerceIn(1500, 6000),
            )
        }
    }

    private fun endCommandTurn(resumeSpotting: Boolean) {
        commandTurnActive = false
        OrbState.setListening(false)
        if (resumeSpotting && running && !paused) wakeEngine?.resume()
    }

    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } catch (e: SecurityException) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseIntent = PendingIntent.getService(
            this, 1, Intent(this, MayaForegroundService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, MayaForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            paused -> "Maya pause pe hai - mic band"
            WakeWordEngine.active -> "Maya sun rahi hai - 'Hey Maya' bolo"
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
        wakeEngine?.stop(); wakeEngine = null
        voice?.shutdown(); voice = null
        OrbState.setListening(false)
        super.onDestroy()
    }
}
