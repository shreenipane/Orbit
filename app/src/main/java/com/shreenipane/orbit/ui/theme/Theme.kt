package com.shreenipane.orbit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calm, grouped-list look: grey canvas, white cards, one indigo accent.
private val Accent = Color(0xFF4F46E5)
private val AccentDark = Color(0xFF8B8CFF)

val Good = Color(0xFF30B35A)
val Warn = Color(0xFFF59E0B)
val Bad = Color(0xFFE5484D)

private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF0FF), onPrimaryContainer = Color(0xFF1E1A6B),
    secondary = Accent, onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF0FF), onSecondaryContainer = Color(0xFF1E1A6B),
    tertiary = Accent, tertiaryContainer = Color(0xFFEEF0FF),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF111114),
    surface = Color.White, onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF6B6B74),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF2F2F7), surfaceContainerHighest = Color(0xFFE9E9EF),
    outline = Color(0xFFD1D1D6), outlineVariant = Color(0xFFE5E5EA),
)

private val Dark = darkColorScheme(
    primary = AccentDark, onPrimary = Color(0xFF14104A),
    primaryContainer = Color(0xFF26245A), onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = AccentDark, onSecondary = Color(0xFF14104A),
    secondaryContainer = Color(0xFF26245A), onSecondaryContainer = Color(0xFFE2E1FF),
    tertiary = AccentDark, tertiaryContainer = Color(0xFF26245A),
    background = Color.Black, onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF48484A), outlineVariant = Color(0xFF38383A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AppType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    )
}

/** Big tabular numbers for stats. */
val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp)

@Composable
fun OrbitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}

/** Green at or above 80%, amber between 75 and 80, red below 75. */
fun attendanceColor(percent: Double): Color = when {
    percent < 75.0 -> Bad
    percent < 80.0 -> Warn
    else -> Good
}
