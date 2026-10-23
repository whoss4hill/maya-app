package com.maya.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Maya comes back on her own after a restart (if the owner left her running). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("maya_state", Context.MODE_PRIVATE)
            if (prefs.getBoolean("service_enabled", false)) {
                ContextCompat.startForegroundService(
                    context, Intent(context, MayaForegroundService::class.java)
                )
            }
        }
    }
}
