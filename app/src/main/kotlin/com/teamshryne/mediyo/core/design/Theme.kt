package com.teamshryne.mediyo.core.design

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Brand palette ────────────────────────────────────────────────────────────
object MediyoColors {
    val Accent = Color(0xFFFF2D63)
    val AccentDim = Color(0xFFC2185B)

    val Black = Color(0xFF000000)
    val Bg0 = Color(0xFF0A0A0C)
    val Bg1 = Color(0xFF121216)
    val Bg2 = Color(0xFF1B1B21)
    val Bg3 = Color(0xFF26262D)

    val TextPrimary = Color(0xFFF7F7F8)
    val TextSecondary = Color(0xFFA6A6AE)
    val TextTertiary = Color(0xFF6E6E78)
}

private val DarkScheme = darkColorScheme(
    primary = MediyoColors.Accent,
    onPrimary = Color.White,
    primaryContainer = MediyoColors.AccentDim,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7DE2FF),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFD166),
    background = MediyoColors.Bg0,
    onBackground = MediyoColors.TextPrimary,
    surface = MediyoColors.Bg0,
    onSurface = MediyoColors.TextPrimary,
    surfaceVariant = MediyoColors.Bg2,
    onSurfaceVariant = MediyoColors.TextSecondary,
    surfaceContainerLowest = MediyoColors.Black,
    surfaceContainerLow = MediyoColors.Bg1,
    surfaceContainer = MediyoColors.Bg1,
    surfaceContainerHigh = MediyoColors.Bg2,
    surfaceContainerHighest = MediyoColors.Bg3,
    outline = MediyoColors.Bg3,
    outlineVariant = MediyoColors.Bg2,
    error = Color(0xFFFF5370),
    errorContainer = Color(0xFF3A1019),
    onErrorContainer = Color(0xFFFFC1CB)
)

// ── Typography: big, bold, tightly tracked (Spotify / Apple Music feel) ──────
val MediyoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp
    )
)

private val MediyoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Mediyo is dark-only by design: the dark scheme is always applied,
// regardless of the system light/dark setting.
@Composable
fun MediyoTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = MediyoTypography,
        shapes = MediyoShapes,
        content = content
    )
}
