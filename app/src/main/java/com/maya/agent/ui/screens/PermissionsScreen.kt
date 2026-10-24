package com.maya.agent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maya.agent.permissions.PermissionCatalog
import com.maya.agent.permissions.PermissionItem
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.MayaPrimaryButton
import com.maya.agent.ui.components.StatusChip
import com.maya.agent.ui.theme.Emerald

/** Screens 02-03 - guided permission setup, two pages, 12 groups. */
@Composable
fun PermissionsScreen(page: Int, onNext: () -> Unit, onSkipAll: () -> Unit) {
    val items = if (page == 1) PermissionCatalog.page1 else PermissionCatalog.page2
    val decisions = remember { mutableStateMapOf<String, Boolean>() } // id -> allowed?
    val context = LocalContext.current

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Mark the group answered; the flow keeps moving either way.
        items.forEach { item ->
            if (item.manifestPerms.isNotEmpty() &&
                item.manifestPerms.all { result.containsKey(it) }
            ) {
                decisions[item.id] = item.manifestPerms.all { result[it] == true }
            }
        }
    }

    fun request(item: PermissionItem) {
        when (item.special) {
            PermissionItem.Special.NOTIFICATION_LISTENER ->
                context.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            PermissionItem.Special.ACCESSIBILITY ->
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            PermissionItem.Special.OVERLAY ->
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            PermissionItem.Special.BATTERY ->
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            null -> if (item.manifestPerms.isNotEmpty()) {
                runtimeLauncher.launch(item.manifestPerms.toTypedArray())
            }
        }
        if (item.special != null) decisions[item.id] = true // sent to the system screen
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("M A Y A", style = MaterialTheme.typography.titleLarge)
            StatusChip("Setup $page/2", active = true)
        }
        Spacer(Modifier.height(20.dp))
        Text("First, your permission.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Every permission is your choice. If you skip one, I will ask again only when that feature is needed.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        GlassCard(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (decisions[item.id] == true) {
                            Text("Done", style = MaterialTheme.typography.labelSmall, color = Emerald)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { request(item) }) {
                            Text("Allow", color = Emerald)
                        }
                        TextButton(onClick = { decisions[item.id] = false }) {
                            Text("Skip", color = Emerald)
                        }
                    }
                }
                if (index < items.lastIndex) HorizontalDivider()
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Having a permission is not the same as using it. Camera and location are used only when you ask. The microphone listens in the background for the wake word only if you turn that on, and you can pause it any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        MayaPrimaryButton(text = "Continue", onClick = onNext)
        TextButton(onClick = onSkipAll, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}
