package com.maya.agent.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.maya.agent.MayaApp
import com.maya.agent.voice.VoskModelStore
import com.maya.agent.service.FloatingOrbService
import com.maya.agent.service.MayaForegroundService
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.SectionLabel
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.ui.theme.MayaThemeMode
import com.maya.agent.calls.PhoneLink
import com.maya.agent.calls.PhoneLinkSettings
import com.maya.agent.data.telegram.ChatIdInput
import com.maya.agent.data.telegram.TelegramControl
import com.maya.agent.data.telegram.TelegramUi
import com.maya.agent.data.telegram.TgResult
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

/** Screen 12 - Settings. */
@Composable
fun SettingsScreen(
    themeMode: MayaThemeMode,
    onThemeMode: (MayaThemeMode) -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val app = MayaApp.from(context)
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("maya_state", android.content.Context.MODE_PRIVATE) }

    var serviceOn by remember { mutableStateOf(prefs.getBoolean("service_enabled", false)) }
    var orbOn by remember { mutableStateOf(prefs.getBoolean("orb_enabled", false)) }
    var wakeOn by remember { mutableStateOf(prefs.getBoolean("wakeword_enabled", false)) }
    var showWakeConsent by remember { mutableStateOf(false) }
    var wakeProgress by remember { mutableStateOf<Int?>(null) }
    var wakeMessage by remember { mutableStateOf<String?>(null) }
    val applyWake = { on: Boolean ->
        wakeOn = on
        prefs.edit().putBoolean("wakeword_enabled", on).apply()
        if (serviceOn) ContextCompat.startForegroundService(context, Intent(context, MayaForegroundService::class.java))
    }
    var confirmWipe by remember { mutableStateOf(false) }

    val tgControl = remember { TelegramControl(app.auth) }
    var tgUi by remember { mutableStateOf<TelegramUi?>(null) }
    var tgBusy by remember { mutableStateOf(false) }
    var tgChatId by remember { mutableStateOf("") }
    var tgCode by remember { mutableStateOf<String?>(null) }
    var tgMsg by remember { mutableStateOf<String?>(null) }
    var phoneOn by remember { mutableStateOf(prefs.getBoolean("phone_link_enabled", false)) }
    var phoneMsg by remember { mutableStateOf<String?>(null) }
    val callGranted = { ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED }
    fun <T> handle(r: TgResult<T>, ok: (T) -> Unit) { when (r) { is TgResult.Ok -> ok(r.value); is TgResult.Err -> tgMsg = r.message } }
    fun refreshTg() { scope.launch { tgBusy = true; handle(tgControl.status()) { tgUi = it }; tgBusy = false } }
    LaunchedEffect(Unit) { if (app.auth.isSignedIn) refreshTg() else tgUi = TelegramUi(false, false, null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        Text(
            "Awaaz, privacy aur phone ke controls.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Bhasha aur Awaaz")
        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Background mein Maya", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "\"Hey Maya\" wake word + persistent notification",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = serviceOn,
                    onCheckedChange = { on ->
                        serviceOn = on
                        prefs.edit().putBoolean("service_enabled", on).apply()
                        val intent = Intent(context, MayaForegroundService::class.java)
                        if (on) ContextCompat.startForegroundService(context, intent)
                        else context.stopService(intent)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                )
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("\"Maya\" bolke jagao (offline)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Default OFF. Sirf upar wala Background switch ON ho tab sunti hai, notification mein Pause/Stop dikhta hai. Awaaz phone se bahar nahi jaati, save nahi hoti. Experimental: battery aur accuracy test baaki.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wakeOn,
                        enabled = wakeProgress == null,
                        onCheckedChange = { on ->
                            wakeMessage = null
                            if (!on) applyWake(false)
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                                wakeMessage = "Pehle Permission Manager se Microphone allow karo"
                            else if (!serviceOn) wakeMessage = "Pehle \"Background mein Maya\" ON karo"
                            else if (VoskModelStore.isInstalled(context)) applyWake(true)
                            else showWakeConsent = true
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                    )
                }
                wakeProgress?.let { Text("Model download: $it%", color = Emerald) }
                wakeMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (VoskModelStore.isInstalled(context) && wakeProgress == null) {
                    TextButton(onClick = { applyWake(false); VoskModelStore.delete(context); wakeMessage = "Model hata diya" }) {
                        Text("Downloaded model delete karo", color = Emerald)
                    }
                }
            }
        }
        if (showWakeConsent) {
            AlertDialog(
                onDismissRequest = { showWakeConsent = false },
                title = { Text("Offline model download?") },
                text = { Text("Wake word ke liye ~${VoskModelStore.SIZE_MB} MB ka free open-source (Apache-2.0) speech model download hoga, alphacephei.com se. Mobile data lag sakta hai. Mic sirf tab sunega jab Background switch ON ho aur Pause nahi ho. Kabhi bhi OFF ya delete kar sakte ho.") },
                confirmButton = {
                    TextButton(onClick = {
                        showWakeConsent = false; wakeProgress = 0
                        Thread {
                            val err = VoskModelStore.download(context) { pct -> wakeProgress = pct }
                            wakeProgress = null
                            if (err == null) applyWake(true) else wakeMessage = err
                        }.start()
                    }) { Text("Download karo", color = Emerald) }
                },
                dismissButton = { TextButton(onClick = { showWakeConsent = false }) { Text("Nahi") } },
            )
        }

        ControlCards(
            tg = TelegramCardState(tgUi, tgBusy, tgChatId, tgCode, tgMsg),
            phone = run {
                val st = PhoneLinkSettings.status(phoneOn, callGranted(), PhoneLink.connected)
                val label = phoneMsg ?: if (phoneOn && !serviceOn) "Turn on \"Background mein Maya\" to connect" else PhoneLinkSettings.label(st)
                PhoneCardState(phoneOn, label, st == PhoneLinkSettings.Status.NEEDS_PERMISSION || phoneMsg != null)
            },
            onTgToggle = { on -> scope.launch { tgBusy = true; tgMsg = null; handle(tgControl.setEnabled(on)) { refreshTg() }; tgBusy = false } },
            onOpenBot = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/maya_whoss4hill_assistant_bot"))) },
            onChatId = { tgChatId = it; tgMsg = null },
            onGetCode = {
                val raw = tgChatId.trim()
                val id = if (raw.isEmpty()) null else ChatIdInput.parse(raw)
                if (raw.isNotEmpty() && id == null) tgMsg = "Chat ID must be digits only"
                else scope.launch { tgBusy = true; tgMsg = null; tgCode = null; handle(tgControl.startPair(id)) { tgCode = it }; tgBusy = false }
            },
            onUnpair = { scope.launch { tgBusy = true; tgMsg = null; tgCode = null; handle(tgControl.unpair()) { refreshTg() }; tgBusy = false } },
            onPhoneToggle = { on ->
                phoneMsg = null
                if (on && !PhoneLinkSettings.canEnable(callGranted())) phoneMsg = "Allow Phone permission first (Open permissions)"
                else {
                    phoneOn = on
                    prefs.edit().putBoolean("phone_link_enabled", on).apply()
                    if (serviceOn) ContextCompat.startForegroundService(context, Intent(context, MayaForegroundService::class.java))
                }
            },
            onOpenPermissions = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:${context.packageName}") })
            },
        )

        SectionLabel("Appearance")
        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MayaThemeMode.entries.forEach { mode ->
                    OutlinedButton(onClick = { onThemeMode(mode) }) {
                        Text(
                            mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (themeMode == mode) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionLabel("Control & Safety")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    })
                }) { Text("Permission Manager (system settings)", color = Emerald) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating orb", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Doosri apps ke upar chhota Maya orb",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = orbOn,
                        onCheckedChange = { on ->
                            orbOn = on
                            prefs.edit().putBoolean("orb_enabled", on).apply()
                            val intent = Intent(context, FloatingOrbService::class.java)
                            if (on && Settings.canDrawOverlays(context)) context.startService(intent)
                            else context.stopService(intent)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                    )
                }
            }
        }

        SectionLabel("Account & Memory")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (app.auth.isSignedIn) "Signed in as ${app.auth.userEmail ?: ""}" else "Signed out",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Yaadein tumhare account se sync hoti hain. Secrets kabhi cloud pe nahi jaate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.auth.isSignedIn) {
                    TextButton(onClick = { app.auth.signOut(); onSignedOut() }) {
                        Text("Sign out", color = Emerald)
                    }
                }
                TextButton(onClick = { confirmWipe = true }) {
                    Text("Clear All Memories", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Saari yaadein mita doon?") },
            text = { Text("Ye wapas nahi aayengi. Pakka?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.memories.memories().forEach { app.memories.deleteMemory(it.id) }
                        confirmWipe = false
                    }
                }) { Text("Haan, mita do", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Rehne do") } },
        )
    }
}
