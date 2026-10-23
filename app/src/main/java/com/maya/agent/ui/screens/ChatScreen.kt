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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import com.maya.agent.MayaApp
import com.maya.agent.data.ChatStore
import com.maya.agent.data.MemoryRepository
import com.maya.agent.ui.components.StatusChip
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.voice.VoiceController
import com.maya.agent.calls.DialCommand
import kotlinx.coroutines.launch

/** Screen 06 - Chat with Maya. */
@Composable
fun ChatScreen(initialText: String? = null) {
    val context = LocalContext.current
    val app = MayaApp.from(context)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || thinking) return
        ChatStore.addUser(trimmed)
        DialCommand.parse(trimmed)?.let { num ->
            ChatStore.addMaya(
                if (DialCommand.open(context, num)) "Dialer khol diya $num ke saath. Call tum khud dabaoge - main call nahi lagati."
                else "Is phone par dialer app nahi mila."
            )
            return
        }
        thinking = true
        scope.launch {
            when (val r = app.memories.chat(trimmed)) {
                is MemoryRepository.Reply.Ok -> {
                    ChatStore.addMaya(r.text)
                    app.voice.speak(r.text)
                }
                is MemoryRepository.Reply.Failed -> ChatStore.addMaya(r.reason)
                MemoryRepository.Reply.SignedOut ->
                    ChatStore.addMaya("Sign in karo pehle - bina account ke main yaad nahi rakh paungi.")
            }
            thinking = false
        }
    }

    // A message handed over from Home (typed or quick action).
    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) send(initialText)
    }
    LaunchedEffect(ChatStore.messages.size) {
        if (ChatStore.messages.isNotEmpty()) listState.animateScrollToItem(ChatStore.messages.lastIndex)
    }

    DisposableEffect(Unit) { onDispose { } }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) app.voice.startListening() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Maya", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Hinglish voice & text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(if (thinking) "Soch rahi hu..." else "Online", active = !thinking)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            "Today",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(ChatStore.messages) { msg ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start,
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (msg.fromUser) 18.dp else 4.dp,
                            bottomEnd = if (msg.fromUser) 4.dp else 18.dp,
                        ),
                        color = if (msg.fromUser) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Text(
                            msg.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        msg.time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (thinking) {
                item {
                    Text(
                        "Maya likh rahi hai...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Bolo ya likho...") },
                singleLine = true,
            )
            Spacer(Modifier.widthIn(min = 8.dp))
            FilledIconButton(
                onClick = {
                    if (input.isBlank()) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) app.voice.startListening()
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        send(input); input = ""
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Emerald),
            ) {
                Icon(
                    if (input.isBlank()) Icons.Filled.Mic else Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
