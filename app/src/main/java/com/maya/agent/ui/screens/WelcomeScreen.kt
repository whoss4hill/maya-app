package com.maya.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.MayaPrimaryButton
import com.maya.agent.ui.components.OrbMood
import com.maya.agent.ui.components.StatusChip
import com.maya.agent.ui.components.VoiceOrb
import com.maya.agent.ui.theme.Emerald

/** Screen 01 - Welcome ("Main Maya. Tum bolo, main karti hu.") */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth()) {
            Text(
                "M A Y A",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            StatusChip("Hinglish", active = true, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Spacer(Modifier.height(40.dp))
        VoiceOrb(mood = OrbMood.IDLE, size = 200.dp)
        Spacer(Modifier.height(32.dp))
        Text("Main Maya.", style = MaterialTheme.typography.displaySmall)
        Text(
            "You say it, I do it.",
            style = MaterialTheme.typography.headlineMedium,
            color = Emerald,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Handling your phone or remembering things - just say it. Maya is here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GlassCard {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Emerald)
                Text(
                    "Your choice, your control. Your memories are yours to manage.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        MayaPrimaryButton(
            text = "Get started",
            onClick = onStart,
            trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Replies are in Hinglish",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}
