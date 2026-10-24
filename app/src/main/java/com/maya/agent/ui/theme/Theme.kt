package com.maya.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class MayaThemeMode { DARK, LIGHT, SYSTEM }

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = GraphiteBg,
    secondary = EmeraldDeep,
    background = GraphiteBg,
    onBackground = TextPrimaryDark,
    surface = GraphiteSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = GraphiteSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    outline = GlassBorder,
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = LightSurface,
    secondary = Emerald,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightBg,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightGlassBorder,
    error = Danger,
)

@Composable
fun MayaTheme(
    mode: MayaThemeMode = MayaThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        MayaThemeMode.DARK -> true
        MayaThemeMode.LIGHT -> false
        MayaThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MayaTypography,
        content = content,
    )
}
