package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OpusLightColorScheme = lightColorScheme(
    primary = OpusRed,
    onPrimary = Color.White,
    primaryContainer = OpusRedLight,
    onPrimaryContainer = OpusRedDark,
    secondary = StudioViolet,
    onSecondary = Color.White,
    secondaryContainer = OpusRedSurface,
    onSecondaryContainer = StudioVioletMuted,
    tertiary = StudioCoral,
    onTertiary = Color.White,
    background = OpusWhiteBackground,
    onBackground = OpusLightTextPrimary,
    surface = OpusWhiteSurface,
    onSurface = OpusLightTextPrimary,
    surfaceVariant = OpusWhiteSurfaceVariant,
    onSurfaceVariant = OpusLightTextSecondary,
    outline = OpusLightBorder,
    outlineVariant = OpusLightBorder
)

private val OpusDarkColorScheme = darkColorScheme(
    primary = OpusRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C0519),
    onPrimaryContainer = OpusRedLight,
    secondary = Color(0xFFFB7185),
    onSecondary = Color(0xFF4C0519),
    background = StudioBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = StudioSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = StudioCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // White-mode-first default per OpusVoice Studio specification
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) OpusDarkColorScheme else OpusLightColorScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
