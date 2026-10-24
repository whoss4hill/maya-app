package com.maya.agent.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.maya.agent.data.MemoryRepository
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.OrbMood
import com.maya.agent.ui.components.StatusChip
import com.maya.agent.ui.components.VoiceOrb
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.voice.VoiceController
import kotlinx.coroutines.launch

/** Screen 05 - Home: the orb, status, quick actions, active task, input. */
@Composable
fun HomeScreen(onNavigateToChat: (String) -> Unit) {
    val context = LocalContext.current
    val app = MayaApp.from(context)
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("Maya is here") }
    var lastReply by remember { mutableStateOf<String?>(null) }

    val voice = remember {
        VoiceController(
            context,
            onListeningChanged = { listening = it },
            onResult = { heard ->
                statusLine = "Thinking..."
                scope.launch {
                    when (val r = app.memories.chat(heard)) {
                        is MemoryRepository.Reply.Ok -> {
                            lastReply = r.text
                            statusLine = "Maya is here"
                            app.voice.speak(r.text)
                        }
                        is MemoryRepository.Reply.Failed -> {
                            lastReply = r.reason
                            statusLine = "Server dikkat"
                            app.voice.speak(r.reason)
                        }
                        MemoryRepository.Reply.SignedOut -> {
                            lastReply = "Sign in first so memories can work."
                            statusLine = "Sign in pending"
                        }
                    }
                }
            },
            onError = { statusLine = it },
        )
    }
    DisposableEffect(Unit) {
        voice.initTts()
        onDispose { voice.shutdown() }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) voice.startListening() }

    fun tapOrb() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else if (listening) voice.stopListening() else voice.startListening()
    }

    fun sendText() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        onNavigateToChat(text)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MAYA", style = MaterialTheme.typography.headlineMedium)
            StatusChip(if (listening) "Mic: On" else "Mic: Standby", active = listening)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusChip(statusLine, active = true)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "What can I do for you?",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            VoiceOrb(
                mood = if (listening) OrbMood.LISTENING else OrbMood.IDLE,
                size = 230.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(onClick = { onNavigateToChat("Ek reminder laga do") }) {
                Icon(Icons.Filled.Alarm, null, tint = Emerald)
                Spacer(Modifier.width(6.dp))
                Text("Set reminder")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { onNavigateToChat("Kuch dhoondo") }) {
                Icon(Icons.Filled.Search, null, tint = Emerald)
                Spacer(Modifier.width(6.dp))
                Text("Search")
            }
        }
        lastReply?.let {
            Spacer(Modifier.height(16.dp))
            GlassCard { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type or speak...") },
            singleLine = true,
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = {
                    if (input.isBlank()) tapOrb() else sendText()
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Mic", tint = Emerald)
                }
            },
        )
        Spacer(Modifier.height(4.dp))
    }
}
