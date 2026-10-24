package com.maya.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maya.agent.ui.components.MayaPrimaryButton
import com.maya.agent.ui.components.OrbMood
import com.maya.agent.ui.components.VoiceOrb

/** Error states (screen 14): offline, backend waking, quota. Honest + one retry. */
@Composable
fun ErrorState(
    kind: ErrorKind,
    onRetry: () -> Unit = {},
) {
    val (title, body, cta) = when (kind) {
        ErrorKind.OFFLINE -> Triple(
            "No internet",
            "Alarms, timers and other on-device tasks keep working offline. Memories sync later.",
            "Try again",
        )
        ErrorKind.BACKEND_WAKING -> Triple(
            "Server is waking up",
            "The first request can take about a minute. Maya is still here.",
            "One more try",
        )
        ErrorKind.QUOTA -> Triple(
            "Daily quota used up",
            "AI answers are unavailable right now. On-device tasks keep working.",
            "Try later",
        )
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(mood = OrbMood.IDLE, size = 140.dp, animate = false)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        MayaPrimaryButton(text = cta, onClick = onRetry)
    }
}

enum class ErrorKind { OFFLINE, BACKEND_WAKING, QUOTA }

/** Empty states (screen 15). */
@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
