package com.maya.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.ui.theme.GlassBorder
import com.maya.agent.ui.theme.GlassFill
import androidx.compose.foundation.border

/** Small status chip like "Mic: Standby" / "Main yahin hu". */
@Composable
fun StatusChip(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) Emerald else MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Big emerald call-to-action button ("Shuru karein", "Aage chalo"). */
@Composable
fun MayaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Emerald,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            trailing?.invoke()
        }
    }
}

enum class MayaTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CHAT("Chat", Icons.Filled.ChatBubbleOutline),
    MEMORY("Memory", Icons.Filled.Psychology),
    VAULT("Vault", Icons.Filled.Lock),
    SETTINGS("Settings", Icons.Filled.Tune),
}

/** Floating pill bottom nav from the approved renders. */
@Composable
fun MayaBottomNav(
    current: MayaTab,
    onSelect: (MayaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MayaTab.entries.forEach { tab ->
            val selected = tab == current
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** RowScope helper so chips can share width. */
@Composable
fun RowScope.WeightBox(weight: Float, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight)) { content() }
}
