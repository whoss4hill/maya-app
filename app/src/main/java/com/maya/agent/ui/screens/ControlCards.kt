package com.maya.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.maya.agent.data.telegram.TelegramUi
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.SectionLabel
import com.maya.agent.ui.theme.Emerald

/** Extends a child past the parent's horizontal padding so the card runs edge to edge (rounded corners kept). */
fun Modifier.fullBleed(inset: Int = 24) = layout { m, c ->
    val px = inset.dp.roundToPx()
    val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * px, maxWidth = c.maxWidth + 2 * px))
    layout(c.maxWidth, p.height) { p.place(-px, 0) }
}

data class TelegramCardState(
    val ui: TelegramUi?, val loading: Boolean, val chatId: String, val code: String?, val message: String?,
)
data class PhoneCardState(val enabled: Boolean, val statusLabel: String, val statusIsProblem: Boolean)

/** Stateless Telegram, Phone and AI model cards (state and actions live in SettingsScreen). */
@Composable
fun ControlCards(
    tg: TelegramCardState,
    phone: PhoneCardState,
    onTgToggle: (Boolean) -> Unit,
    onOpenBot: () -> Unit,
    onChatId: (String) -> Unit,
    onGetCode: () -> Unit,
    onUnpair: () -> Unit,
    onPhoneToggle: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    SectionLabel("Telegram")
    GlassCard(Modifier.fullBleed()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Telegram control", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = tg.ui?.enabled ?: false,
                    enabled = tg.ui?.bound == true && !tg.loading,
                    onCheckedChange = onTgToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                )
            }
            Text(
                if (tg.ui == null) "Checking..." else if (tg.ui.bound) "Paired chat: ${tg.ui.maskedChat ?: ""}" else "No chat paired",
                style = MaterialTheme.typography.bodyMedium, color = sub,
            )
            TextButton(onClick = onOpenBot) { Text("Open bot", color = Emerald) }
            Text("Pair a different chat", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tg.chatId, onValueChange = onChatId, placeholder = { Text("Chat ID") },
                    modifier = Modifier.weight(1f), singleLine = true,
                )
                OutlinedButton(onClick = onGetCode, enabled = !tg.loading) { Text("Get code", color = Emerald) }
            }
            tg.code?.let { Text("Code: $it", style = MaterialTheme.typography.titleMedium, color = Emerald) }
            Text("Send the code to the bot from that chat. The ID alone never pairs it.", style = MaterialTheme.typography.bodyMedium, color = sub)
            tg.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (tg.ui?.bound == true) TextButton(onClick = onUnpair) { Text("Unpair", color = Emerald) }
        }
    }
    SectionLabel("Phone")
    GlassCard(Modifier.fullBleed()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Let Telegram start calls", style = MaterialTheme.typography.titleMedium)
                Switch(checked = phone.enabled, onCheckedChange = onPhoneToggle, colors = SwitchDefaults.colors(checkedTrackColor = Emerald))
            }
            Text(phone.statusLabel, style = MaterialTheme.typography.bodyMedium, color = if (phone.statusIsProblem) MaterialTheme.colorScheme.error else sub)
            Text("Needs Phone permission. Contacts permission only for calling by name.", style = MaterialTheme.typography.bodyMedium, color = sub)
            TextButton(onClick = onOpenPermissions) { Text("Open permissions", color = Emerald) }
        }
    }
    SectionLabel("AI model")
    GlassCard(Modifier.fullBleed()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gemini (automatic)", style = MaterialTheme.typography.titleMedium)
            Text("Switches to the next model if one hits its limit. Web search is off.", style = MaterialTheme.typography.bodyMedium, color = sub)
        }
    }
}
