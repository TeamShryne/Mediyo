package com.teamshryne.mediyo.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE91E63), secondary = Color(0xFF7C4DFF), tertiary = Color(0xFF00BCD4),
    background = Color(0xFFFCFCFC), surface = Color(0xFFFFFFFF)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF5A8A), secondary = Color(0xFFB388FF), tertiary = Color(0xFF4DD0E1),
    background = Color(0xFF121212), surface = Color(0xFF1E1E1E)
)

@Composable
fun MediyoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
