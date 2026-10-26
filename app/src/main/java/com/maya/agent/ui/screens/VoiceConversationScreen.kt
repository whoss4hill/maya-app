package com.maya.agent.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.maya.agent.MayaApp
import com.maya.agent.ui.components.OrbMood
import com.maya.agent.ui.components.StatusChip
import com.maya.agent.ui.components.VoiceOrb
import com.maya.agent.ui.theme.Danger
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.ui.theme.TextSecondaryDark
import com.maya.agent.voice.live.LiveSessionManager
import com.maya.agent.voice.live.LiveVoiceConfig
import com.maya.agent.voice.live.LiveProtocol
import com.maya.agent.voice.live.LiveToolBridge
import kotlinx.coroutines.delay

/**
 * Screen 05b - Free talk: continuous voice conversation (Gemini Live).
 * Approved design 2026-10-24: rotating orb + state + end button only.
 * NO transcript or chat bubbles here - text chats stay on the Chat page.
 * Mic runs only while this screen is visible; ending/closing stops it.
 */
@Composable
fun VoiceConversationScreen(onEnd: () -> Unit) {
    val context = LocalContext.current
    val app = MayaApp.from(context)

    var state by remember { mutableStateOf(LiveSessionManager.State.CONNECTING) }
    var errorLine by remember { mutableStateOf<String?>(null) }
    var speaking by remember { mutableStateOf(false) }

    val manager = remember {
        LiveSessionManager(
            systemInstruction = LiveVoiceConfig.SYSTEM_INSTRUCTION,
            voiceName = LiveVoiceConfig.VOICE_NAME,
            bearerTokenProvider = { app.auth.validAccessToken() },
            toolsJson = LiveToolBridge.DECLARATIONS_JSON,
        )
    }

    val callback = remember {
        object : LiveSessionManager.Callback {
            override fun onState(s: LiveSessionManager.State) { state = s }
            override fun onError(message: String) { errorLine = message }
            override suspend fun onToolCall(call: LiveProtocol.ServerEvent.FunctionCall): String =
                LiveToolBridge.execute(context, call)
            override fun onOwnerTranscript(text: String) { com.maya.agent.calls.VoiceCall.confirmation.onOwnerTranscript(text, System.currentTimeMillis()) }
            override fun onSessionReset() { com.maya.agent.calls.VoiceCall.confirmation.reset() }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) manager.start(callback) else errorLine = "Mic permission chahiye" }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.start(callback) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) {
        onDispose { manager.end(); com.maya.agent.calls.VoiceCall.confirmation.reset(); com.maya.agent.voice.live.ReadbackSpeaker.stop() }
    }

    // Speaking indicator: any model audio flips the orb to SPEAKING briefly.
    androidx.compose.runtime.LaunchedEffect(state) {
        while (state == LiveSessionManager.State.ACTIVE) {
            speaking = manager.isModelSpeaking()
            delay(150)
        }
        speaking = false
    }

    val rotation by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (speaking) 1400 else 3200, easing = LinearEasing)),
        label = "spin",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxSize().weight(1f)) {
            Text(
                "Maya",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.TopStart),
            )
            StatusChip("Free talk", active = true, modifier = Modifier.align(Alignment.TopEnd))
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Rotating ring (the approved "ghoomta hua orb")
                    Canvas(Modifier.size(290.dp)) {
                        drawArc(
                            color = Emerald.copy(alpha = 0.18f),
                            startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            style = Stroke(width = 4f),
                        )
                        drawArc(
                            color = Emerald,
                            startAngle = rotation, sweepAngle = 90f, useCenter = false,
                            style = Stroke(width = 7f, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = Emerald.copy(alpha = 0.5f),
                            startAngle = rotation + 180f, sweepAngle = 40f, useCenter = false,
                            style = Stroke(width = 5f, cap = StrokeCap.Round),
                        )
                    }
                    VoiceOrb(
                        mood = when {
                            state != LiveSessionManager.State.ACTIVE -> OrbMood.IDLE
                            speaking -> OrbMood.SPEAKING
                            else -> OrbMood.LISTENING
                        },
                        size = 230.dp,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    when (state) {
                        LiveSessionManager.State.CONNECTING -> "Connecting..."
                        LiveSessionManager.State.ACTIVE -> if (speaking) "Speaking..." else "Listening..."
                        LiveSessionManager.State.ENDED -> "Ended"
                        LiveSessionManager.State.IDLE -> "Starting..."
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    errorLine ?: "Talk naturally - interrupt any time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark,
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Danger,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            IconButton(onClick = { manager.end(); onEnd() }, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "End voice chat", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
