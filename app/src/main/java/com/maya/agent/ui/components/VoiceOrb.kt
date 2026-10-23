package com.maya.agent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maya.agent.ui.theme.Emerald
import com.maya.agent.ui.theme.GraphiteSurfaceHigh

/** The three approved orb moods: idle (calm), listening (gentle pulse), speaking (soft glow). */
enum class OrbMood { IDLE, LISTENING, SPEAKING }

/**
 * Animated voice orb - the centerpiece. Graphite glass shell with an emerald
 * core. Reduce-motion friendly: pass animate=false to keep it still.
 */
@Composable
fun VoiceOrb(
    mood: OrbMood,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val idleBreath by transition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val listenPulse by transition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val speakGlow by transition.animateFloat(
        initialValue = 0.65f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    val (scale, glowAlpha) = when (mood) {
        OrbMood.IDLE -> (if (animate) idleBreath else 1f) to 0.45f
        OrbMood.LISTENING -> (if (animate) listenPulse else 1f) to 0.8f
        OrbMood.SPEAKING -> 1f to (if (animate) speakGlow else 0.85f)
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Outer glow halo
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .graphicsLayer { alpha = glowAlpha * 0.5f }
                .blur(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Emerald.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
        )
        // Glass shell
        Box(
            Modifier
                .size(size * 0.72f)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(GraphiteSurfaceHigh, Color(0xFF0D1210))
                    )
                )
        )
        // Emerald core
        Box(
            Modifier
                .size(size * 0.30f)
                .scale(scale)
                .graphicsLayer { alpha = 0.75f + glowAlpha * 0.25f }
                .blur(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Emerald, Emerald.copy(alpha = 0.25f))
                    )
                )
        )
    }
}
