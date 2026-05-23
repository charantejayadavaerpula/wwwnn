package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StudioPrimary,
    secondary = StudioSecondary,
    tertiary = StudioAccent,
    background = StudioBackground,
    surface = StudioSurface,
    surfaceVariant = StudioCard,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = StudioTextPrimary,
    onSurface = StudioTextPrimary,
    onSurfaceVariant = StudioTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark theme for studio vibe
    dynamicColor: Boolean = false, // Disable dynamic colors to keep original branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
