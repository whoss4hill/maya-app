package com.maya.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maya.agent.ui.theme.GlassBorder
import com.maya.agent.ui.theme.GlassFill
import com.maya.agent.ui.theme.LightGlassBorder
import com.maya.agent.ui.theme.LightGlassFill
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * Subtle frosted-glass card: translucent fill + hairline border, soft corners.
 * Used for chat bubbles, cards and panels where glass earns its place.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    corner: Int = 20,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dark) GlassFill else LightGlassFill,
        ),
        border = BorderStroke(1.dp, if (dark) GlassBorder else LightGlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
