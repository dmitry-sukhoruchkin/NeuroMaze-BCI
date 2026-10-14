package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberBg,
    primaryContainer = CyberSurfaceVariant,
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = TextPrimary,
    secondaryContainer = CyberSurfaceVariant,
    onSecondaryContainer = NeonPurple,
    tertiary = NeonPink,
    onTertiary = TextPrimary,
    background = CyberBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
