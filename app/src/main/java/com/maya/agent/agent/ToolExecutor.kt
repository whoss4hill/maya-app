package com.maya.agent.agent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.util.Log
import com.maya.agent.MainActivity
import com.maya.agent.MayaApp
import com.maya.agent.accessibility.MayaAccessibilityService
import com.maya.agent.data.CartStore
import com.maya.agent.data.ChatStore
import com.maya.agent.data.backend.BackendClient
import com.maya.agent.data.backend.ChatRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Executes routed intents. [speak] receives the line Maya says back.
 * Safety: cart/orders ALWAYS stop at the confirm dialog (CartStore) - this
 * executor never completes a payment or booking by itself.
 */
class ToolExecutor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val accessibilityOffMsg =
        "Accessibility service off hai - Maya settings me on karo, phir ye kaam kar paungi"

    fun execute(intent: MayaIntent, speak: (String) -> Unit) {
        when (intent) {
            is MayaIntent.SetTimer -> {
                val ok = startClock(AlarmClock.ACTION_SET_TIMER) {
                    putExtra(AlarmClock.EXTRA_LENGTH, intent.seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, intent.label ?: "Maya timer")
                }
                speak(
                    if (ok) {
                        val min = intent.seconds / 60
                        if (min >= 1) "$min minute ka timer laga diya"
                        else "${intent.seconds} second ka timer laga diya"
                    } else "Timer set nahi ho paya - clock app nahi mili"
                )
            }
            is MayaIntent.SetAlarm -> {
                val ok = startClock(AlarmClock.ACTION_SET_ALARM) {
                    putExtra(AlarmClock.EXTRA_HOUR, intent.hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, intent.minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, intent.label ?: "Maya alarm")
                }
                speak(if (ok) "Alarm ${intent.hour}:${"%02d".format(intent.minute)} ka set kar diya"
                      else "Alarm set nahi ho paya - clock app nahi mili")
            }
            is MayaIntent.CartAdd -> {
                CartStore.request(intent.item)
                bringAppForward(MainActivity.ACTION_SHOW_CART)
                speak("${intent.item} cart me daal rahi hoon - app me confirm kar do")
            }
            is MayaIntent.OpenApp ->
                speak(if (openApp(intent.query)) "${intent.query} khol rahi hoon"
                      else "${intent.query} app nahi mili is phone pe")
            MayaIntent.GoBack -> {
                val svc = MayaAccessibilityService.instance
                speak(if (svc != null && svc.globalBack()) "Back kar diya" else accessibilityOffMsg)
            }
            MayaIntent.GoHome -> {
                val svc = MayaAccessibilityService.instance
                speak(if (svc != null && svc.globalHome()) "Home screen khol di" else accessibilityOffMsg)
            }
            is MayaIntent.TapText -> {
                val svc = MayaAccessibilityService.instance
                speak(
                    when {
                        svc == null -> accessibilityOffMsg
                        svc.tapText(intent.text) -> "'${intent.text}' daba diya"
                        else -> "Screen pe '${intent.text}' nahi mila"
                    }
                )
            }
            MayaIntent.ReadScreen -> {
                val svc = MayaAccessibilityService.instance
                speak(
                    when {
                        svc == null -> accessibilityOffMsg
                        else -> {
                            val text = svc.readScreenText()
                            if (text.isBlank()) "Screen pe kuch padhne layak nahi mila"
                            else "Screen pe ye hai: ${text.take(400)}"
                        }
                    }
                )
            }
            is MayaIntent.Chat -> chatFallback(intent.text, speak)
        }
    }

    private fun startClock(action: String, extras: Intent.() -> Unit): Boolean = try {
        val i = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            extras()
        }
        context.startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w("ToolExecutor", "no clock app", e); false
    }

    private fun openApp(query: String): Boolean {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val q = query.lowercase()
        val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(q) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(q) }
        val launch = match?.let { pm.getLaunchIntentForPackage(it.packageName) } ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(launch); true } catch (e: Exception) { false }
    }

    private fun chatFallback(text: String, speak: (String) -> Unit) {
        val token = MayaApp.from(context).auth.accessToken
        if (token == null) {
            speak("Is sawaal ke liye mujhe backend se baat karni hogi - pehle app me sign in kar do")
            return
        }
        scope.launch {
            val reply = try {
                BackendClient.api.chat("Bearer $token", ChatRequest(text)).reply
            } catch (e: Exception) {
                null
            }
            val out = reply?.takeIf { it.isNotBlank() }
                ?: "Abhi backend se baat nahi ho pa rahi - thodi der me phir poochna"
            ChatStore.addUser(text)
            ChatStore.addMaya(out)
            speak(out.take(600))
        }
    }

    private fun bringAppForward(action: String) {
        val i = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            this.action = action
        }
        context.startActivity(i)
    }
}
