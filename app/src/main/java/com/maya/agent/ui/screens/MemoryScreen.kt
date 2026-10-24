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

/** Screen 07 - "Your memories." */
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
        Text("YOUR MEMORY", style = MaterialTheme.typography.labelSmall, color = Emerald)
        Text("Your memories.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Everything Maya has learned is here. You can edit or delete it any time.",
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
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            "Not signed in. Memory needs an account.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else if (shown.isEmpty()) {
                item {
                    GlassCard {
                        Text("Nothing remembered yet.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Say in chat: 'remember that I like tea without sugar' and I will save it.",
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
                        "Memories sync with your account. Keep passwords and secrets in the Vault, not in memories.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
