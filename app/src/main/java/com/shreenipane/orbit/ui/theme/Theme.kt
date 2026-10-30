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

// A night-sky palette with warm accents: deep indigo, sunny yellow, coral.
private val Indigo = Color(0xFF3D348B)
private val IndigoLight = Color(0xFFC5BFFF)
private val Sun = Color(0xFFFFD166)
private val Coral = Color(0xFFFF7B6B)
private val Mint = Color(0xFF2EC4B6)

val Good = Color(0xFF2EAF7D)
val Warn = Color(0xFFF2A541)
val Bad = Color(0xFFE5484D)

private val Light = lightColorScheme(
    primary = Indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF140C4D),
    secondary = Coral, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1DC), onSecondaryContainer = Color(0xFF3E0702),
    tertiary = Mint, tertiaryContainer = Color(0xFFCFF5F0),
    background = Color(0xFFFBF8FF), surface = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFF1EDF9), surfaceContainerHigh = Color(0xFFEAE6F3),
)

private val Dark = darkColorScheme(
    primary = IndigoLight, onPrimary = Color(0xFF241A73),
    primaryContainer = Color(0xFF3D348B), onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFFFB4A8), secondaryContainer = Color(0xFF7A2A20),
    tertiary = Color(0xFF7BDFF2), tertiaryContainer = Color(0xFF00504A),
    background = Color(0xFF13111F), surface = Color(0xFF13111F),
    surfaceContainer = Color(0xFF1F1C2E), surfaceContainerHigh = Color(0xFF2A2639),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

private val AppType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val Accent = Sun
val NumberStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-0.5).sp)

@Composable
fun OrbitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}

/** Green at or above 75%, amber just above the line, red below. */
fun attendanceColor(percent: Double): Color = when {
    percent < 75.0 -> Bad
    percent < 80.0 -> Warn
    else -> Good
}
