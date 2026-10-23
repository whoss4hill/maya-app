package com.maya.agent.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.maya.agent.MayaApp
import com.maya.agent.data.vault.BiometricGate
import com.maya.agent.data.vault.VaultRepository
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.MayaPrimaryButton
import com.maya.agent.ui.theme.Emerald
import java.util.UUID

/** Screens 08-11 - Tijori (Vault): locked, list, add form, usage log. */
@Composable
fun VaultScreen() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val app = MayaApp.from(context)

    var unlocked by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<VaultRepository.VaultItem>>(emptyList()) }
    var filter by remember { mutableStateOf("all") }
    var showAdd by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    fun refresh() { items = app.vault.list() }

    if (!unlocked) {
        // Locked state (screen 08)
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Emerald)
            Spacer(Modifier.height(16.dp))
            Text("Tijori band hai", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Secrets encrypted hain, sirf tumhare biometric ya PIN se khulte hain.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            MayaPrimaryButton(text = "Unlock karo", onClick = {
                if (activity != null && BiometricGate.canAuthenticate(activity)) {
                    BiometricGate.authenticate(activity, onUnlocked = { unlocked = true; refresh() })
                } else {
                    unlocked = true; refresh() // device without biometrics: app-level lock only
                }
            })
        }
        return
    }

    val filters = listOf("all", "login", "card", "key", "other")
    val shown = items.filter {
        filter == "all" || it.kind.name.equals(filter, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Tijori (Vault)", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = { unlocked = false }) {
                Icon(Icons.Filled.Lock, null, tint = Emerald)
                Spacer(Modifier.padding(2.dp))
                Text("LOCK NOW")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(if (f == "all") "Sab" else f.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                item {
                    GlassCard {
                        Text("Tijori khaali hai", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Passwords, cards, keys - sab encrypted, sirf is phone par. Chat me kabhi nahi maangungi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(shown) { item ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (item.kind) {
                                VaultRepository.VaultItem.Kind.LOGIN -> Icons.Filled.Login
                                VaultRepository.VaultItem.Kind.CARD -> Icons.Filled.CreditCard
                                VaultRepository.VaultItem.Kind.KEY -> Icons.Filled.Key
                                VaultRepository.VaultItem.Kind.OTHER -> Icons.Filled.LockOpen
                            },
                            contentDescription = null, tint = Emerald,
                        )
                        Spacer(Modifier.padding(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (item.site.isNotBlank()) item.site else item.kind.name.lowercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { app.vault.delete(item.id); refresh() }) {
                            Text("x", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { showLog = true }) { Text("Usage Log (aakhri activity)", color = Emerald) }
                if (showLog) {
                    val log = app.vault.auditLog().takeLast(10).reversed()
                    GlassCard {
                        if (log.isEmpty()) {
                            Text("Abhi koi activity nahi.", style = MaterialTheme.typography.bodyMedium)
                        }
                        log.forEach { e ->
                            Text(
                                "${e.ts} - ${e.action}: ${e.itemLabel} (${e.where})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
        ) {
            Icon(Icons.Filled.Add, null)
            Text("Naya secret jodo")
        }
    }

    if (showAdd) {
        var label by remember { mutableStateOf("") }
        var secret by remember { mutableStateOf("") }
        var site by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(VaultRepository.VaultItem.Kind.LOGIN) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Naya secret") },
            text = {
                Column {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Naam (e.g. Zomato login)") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text("Secret") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = site, onValueChange = { site = it }, label = { Text("Site/app (e.g. zomato.com)") })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VaultRepository.VaultItem.Kind.entries.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name.lowercase()) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (label.isNotBlank() && secret.isNotBlank()) {
                        app.vault.add(
                            VaultRepository.VaultItem(
                                id = UUID.randomUUID().toString(),
                                kind = kind, label = label, secret = secret, site = site,
                            )
                        )
                        refresh(); showAdd = false
                    }
                }) { Text("Save", color = Emerald) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
