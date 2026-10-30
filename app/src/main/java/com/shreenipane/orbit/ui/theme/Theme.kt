package com.shreenipane.orbit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenipane.orbit.R

/**
 * Neo-brutalist pastel look: a soft teal page, pastel cards with thick black
 * outlines and hard offset shadows, a hand-drawn heading font, and Poppins for
 * everything else. Colour palette after the FFCS-inator planner (MIT, CodeChef-VIT).
 */
object Pastel {
    val Page = Color(0xFFCEE4E5)
    val Card = Color(0xFFE6F3F3)
    val CardTeal = Color(0xFFA7D5D7)
    val CardLight = Color(0xFFC9E5E6)
    val Yellow = Color(0xFFFFEA79)
    val Green = Color(0xFFC1FF83)
    val Salmon = Color(0xFFFFAD93)
    val Sky = Color(0xFF75E5EA)
    val Blue = Color(0xFF86D7FF)
    val Mint = Color(0xFF96FFCA)
    val Lavender = Color(0xFFC4AFF3)
    val Periwinkle = Color(0xFF90BDFF)
    val Ink = Color(0xFF111111)
    val InkMuted = Color(0xB3000000)
    val Hairline = Color(0x26000000)
}

// Status colours dark enough to read on pastel.
val Good = Color(0xFF1E8A3A)
val Warn = Color(0xFFB07800)
val Bad = Color(0xFFCC3312)

val Pangolin = FontFamily(Font(R.font.pangolin_regular, FontWeight.Normal))
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val Scheme = lightColorScheme(
    primary = Pastel.Ink, onPrimary = Color.White,
    primaryContainer = Pastel.Yellow, onPrimaryContainer = Pastel.Ink,
    secondary = Pastel.Ink, onSecondary = Color.White,
    secondaryContainer = Pastel.Sky, onSecondaryContainer = Pastel.Ink,
    tertiary = Pastel.Ink, tertiaryContainer = Pastel.Green, onTertiaryContainer = Pastel.Ink,
    background = Pastel.Page, onBackground = Pastel.Ink,
    surface = Pastel.Card, onSurface = Pastel.Ink,
    surfaceVariant = Pastel.CardLight, onSurfaceVariant = Pastel.InkMuted,
    surfaceContainerLowest = Pastel.Card, surfaceContainerLow = Pastel.Card,
    surfaceContainer = Pastel.Card, surfaceContainerHigh = Pastel.CardLight,
    surfaceContainerHighest = Pastel.CardTeal,
    outline = Pastel.Ink, outlineVariant = Pastel.Hairline,
    error = Bad, onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private fun TextStyle.p(size: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    copy(fontFamily = Poppins, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp)

private val AppType = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Pangolin),
        displayMedium = displayMedium.copy(fontFamily = Pangolin),
        displaySmall = displaySmall.copy(fontFamily = Pangolin, fontSize = 40.sp),
        headlineLarge = headlineLarge.copy(fontFamily = Pangolin, fontSize = 36.sp, lineHeight = 42.sp),
        headlineMedium = headlineMedium.copy(fontFamily = Pangolin, fontSize = 28.sp),
        headlineSmall = headlineSmall.copy(fontFamily = Pangolin, fontSize = 24.sp),
        titleLarge = titleLarge.p(19, FontWeight.SemiBold),
        titleMedium = titleMedium.p(15, FontWeight.SemiBold),
        titleSmall = titleSmall.p(14, FontWeight.Medium),
        bodyLarge = bodyLarge.p(15, FontWeight.Medium),
        bodyMedium = bodyMedium.p(14),
        bodySmall = bodySmall.p(12),
        labelLarge = labelLarge.p(13, FontWeight.SemiBold),
        labelMedium = labelMedium.p(12, FontWeight.SemiBold, 0.3f),
        labelSmall = labelSmall.p(10, FontWeight.Medium),
    )
}

/** Big numbers on stat tiles. */
val NumberStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp)

/** Always the pastel theme: the look depends on light, saturated colour. */
@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppType, shapes = AppShapes, content = content)
}

/** Green from 80%, amber 75–80, red below 75. */
fun attendanceColor(percent: Double): Color = when {
    percent < 75.0 -> Bad
    percent < 80.0 -> Warn
    else -> Good
}

/** Pastel fill matching a status colour, for tags and tiles. */
fun pastelFor(c: Color): Color = when (c) {
    Good -> Pastel.Green
    Warn -> Pastel.Yellow
    Bad -> Pastel.Salmon
    else -> Pastel.CardLight
}
