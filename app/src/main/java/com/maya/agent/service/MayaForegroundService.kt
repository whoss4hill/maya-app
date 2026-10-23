package com.maya.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maya.agent.MainActivity
import com.maya.agent.R

/**
 * Keeps Maya within reach: persistent notification, wake-word listener seam,
 * start-on-boot. The notification always shows a clear Pause / Stop.
 *
 * NOTE: the always-on low-power wake-word engine itself (on-device hotword
 * model) is a deliberate seam here - WakeWordEngine.start/stop - because it
 * needs on-device tuning. The mic is only opened when the owner summons Maya
 * or the wake word fires; never silently.
 */
class MayaForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "maya_background"
        const val NOTIFICATION_ID = 42
        const val ACTION_PAUSE = "com.maya.agent.action.PAUSE"
        const val ACTION_STOP = "com.maya.agent.action.STOP"
        var running = false
            private set
        var paused = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_maya), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { paused = true; updateNotification(); return START_STICKY }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        running = true
        startForegroundWithType()
        // TODO(seam): WakeWordEngine.start(context) when paused == false
        return START_STICKY
    }

    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            // Mic type is declared; the mic itself only opens on an explicit
            // owner summon or wake-word hit.
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } catch (e: SecurityException) {
                // Mic permission not granted yet - run without the mic type.
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
            this, 1, Intent(this, MayaForegroundService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, MayaForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (paused) "Maya pause pe hai" else getString(R.string.notification_maya_running)
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
        // TODO(seam): WakeWordEngine.stop()
        super.onDestroy()
    }
}
