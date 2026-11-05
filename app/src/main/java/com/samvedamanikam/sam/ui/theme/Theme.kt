package com.samvedamanikam.sam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SamAccent = Color(0xFF26C6DA)
val SamNegative = Color(0xFFEF5350)
val SamPositive = Color(0xFF66BB6A)
val SamWarning = Color(0xFFFFB74D)

private val SamColors = darkColorScheme(
    primary = SamAccent,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = SamWarning,
    onSecondary = Color(0xFF3E2500),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE3E5EA),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFE3E5EA),
    surfaceVariant = Color(0xFF222630),
    onSurfaceVariant = Color(0xFFB8BEC9),
    surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF1E222B),
    surfaceContainerHighest = Color(0xFF262B35),
    outline = Color(0xFF3A404C),
    outlineVariant = Color(0xFF2A2F39),
    error = SamNegative
)

/** Dark theme only (by design). */
@Composable
fun SamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SamColors, typography = Typography(), content = content)
}
