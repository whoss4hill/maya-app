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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maya.agent.MayaApp
import com.maya.agent.data.backend.MemoryDto
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.theme.Emerald
import kotlinx.coroutines.launch

/** Screen 07 - "Tumhari baatein, yaad hain." */
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val app = MayaApp.from(context)
    val scope = rememberCoroutineScope()

    var memories by remember { mutableStateOf<List<MemoryDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var signedOut by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            signedOut = !app.auth.isSignedIn
            if (!signedOut) memories = app.memories.memories()
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val kinds = listOf("all", "preference", "person", "routine", "fact", "mood", "relationship")
    val shown = memories.filter {
        (filter == "all" || it.kind == filter) &&
            (query.isBlank() || it.content.contains(query, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("MAYA", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("TUMHARI MEMORY", style = MaterialTheme.typography.labelSmall, color = Emerald)
        Text("Tumhari baatein, yaad hain.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Maya jo baatein seekhti hai, sab yahan hai. Tum jab chaaho badal ya mita sakte ho.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search memories...") },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            kinds.forEach { k ->
                FilterChip(
                    selected = filter == k,
                    onClick = { filter = k },
                    label = { Text(if (k == "all") "All" else k.replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Emerald,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (signedOut) {
                item {
                    GlassCard {
                        Text(
                            "Sign in nahi hai - memory ke liye account chahiye.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else if (shown.isEmpty()) {
                item {
                    GlassCard {
                        Text("Abhi kuch yaad nahi.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Chat me bolo - 'yaad rakhna, mujhe chai bina cheeni pasand hai' - aur main save kar dungi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(shown) { m ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.kind.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald,
                            )
                            Text(m.content, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = {
                            scope.launch { app.memories.deleteMemory(m.id); refresh() }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Mita do",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                GlassCard {
                    Text(
                        "Koi bhi secret ya password memory mein store nahi hota. Woh Vault mein safe rehte hain.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
