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
import com.maya.agent.MayaApp
import com.maya.agent.service.FloatingOrbService
import com.maya.agent.service.MayaForegroundService
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.SectionLabel
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.ui.theme.MayaThemeMode
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
    var confirmWipe by remember { mutableStateOf(false) }

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
