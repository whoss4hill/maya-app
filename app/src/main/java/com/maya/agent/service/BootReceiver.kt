package com.maya.agent.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.maya.agent.MainActivity
import com.maya.agent.R

/**
 * After a restart Maya never opens the microphone on her own. Android 14+ forbids
 * starting a microphone foreground service from BOOT_COMPLETED, so there we only
 * post a tap-to-resume notification; the owner reopens Maya to start listening.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("maya_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", false)) return
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(MayaForegroundService.CHANNEL_ID, context.getString(R.string.notification_channel_maya), NotificationManager.IMPORTANCE_LOW)
            )
            val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            try {
                nm.notify(
                    43,
                    NotificationCompat.Builder(context, MayaForegroundService.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Maya")
                        .setContentText("Phone restarted. Tap to start Maya")
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: SecurityException) { /* notifications not allowed */ }
        } else {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MayaForegroundService::class.java))
            } catch (_: Exception) { /* OS refused background start; owner reopens the app */ }
        }
    }
}
