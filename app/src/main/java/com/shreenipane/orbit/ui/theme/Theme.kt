package com.shreenipane.orbit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
/** One full set of colours. Light is the FFCS-style pastel set; dark keeps the same hues, deepened. */
data class PastelPalette(
    val page: Color, val card: Color, val cardTeal: Color, val cardLight: Color,
    val yellow: Color, val green: Color, val salmon: Color, val sky: Color, val blue: Color,
    val mint: Color, val lavender: Color, val periwinkle: Color,
    val ink: Color, val inkMuted: Color, val hairline: Color, val line: Color,
    val good: Color, val warn: Color, val bad: Color,
)

val LightPalette = PastelPalette(
    page = Color(0xFFCEE4E5), card = Color(0xFFE6F3F3), cardTeal = Color(0xFFA7D5D7), cardLight = Color(0xFFC9E5E6),
    yellow = Color(0xFFFFEA79), green = Color(0xFFC1FF83), salmon = Color(0xFFFFAD93), sky = Color(0xFF75E5EA),
    blue = Color(0xFF86D7FF), mint = Color(0xFF96FFCA), lavender = Color(0xFFC4AFF3), periwinkle = Color(0xFF90BDFF),
    ink = Color(0xFF111111), inkMuted = Color(0xB3000000), hairline = Color(0x26000000), line = Color(0xFF111111),
    good = Color(0xFF1E8A3A), warn = Color(0xFFB07800), bad = Color(0xFFCC3312),
)

/**
 * Dark: a neutral near-black page with soft grey outlines instead of bright
 * ones, and the pastels as deep but clearly hued tints that keep light text
 * readable. Status colours are bright so rings and tags stand out.
 */
val DarkPalette = PastelPalette(
    page = Color(0xFF0E1116), card = Color(0xFF171B22), cardTeal = Color(0xFF13171D), cardLight = Color(0xFF1F242C),
    yellow = Color(0xFF4A4012), green = Color(0xFF1E4A2C), salmon = Color(0xFF5C2A22), sky = Color(0xFF16404B),
    blue = Color(0xFF1A3A5C), mint = Color(0xFF15463A), lavender = Color(0xFF362C58), periwinkle = Color(0xFF243A66),
    ink = Color(0xFFE8EDF2), inkMuted = Color(0xFF9AA4AF), hairline = Color(0xFF2A3038), line = Color(0xFF39414B),
    good = Color(0xFF4CC46A), warn = Color(0xFFE3A72F), bad = Color(0xFFFF6B5B),
)

/**
 * The active palette. Backed by Compose state, so switching light/dark
 * redraws everything, including code that reads it outside composition
 * (draw modifiers, notifications of colour in tags).
 */
object Pastel {
    var palette by mutableStateOf(LightPalette)
    /** Your accent colour (Settings → Appearance): selected tabs, buttons and toggles. Index into [ACCENTS]. */
    var accentIndex by mutableIntStateOf(0)
    val ACCENTS = listOf("Yellow", "Mint", "Sky", "Lavender", "Salmon", "Green")
    fun accentAt(i: Int): Color = when (i) {
        1 -> palette.mint; 2 -> palette.sky; 3 -> palette.lavender; 4 -> palette.salmon; 5 -> palette.green
        else -> palette.yellow
    }
    val Accent get() = accentAt(accentIndex)
    val Page get() = palette.page
    val Card get() = palette.card
    val CardTeal get() = palette.cardTeal
    val CardLight get() = palette.cardLight
    val Yellow get() = palette.yellow
    val Green get() = palette.green
    val Salmon get() = palette.salmon
    val Sky get() = palette.sky
    val Blue get() = palette.blue
    val Mint get() = palette.mint
    val Lavender get() = palette.lavender
    val Periwinkle get() = palette.periwinkle
    /** Text. */
    val Ink get() = palette.ink
    val InkMuted get() = palette.inkMuted
    val Hairline get() = palette.hairline
    /** Outlines and hard shadows. */
    val Line get() = palette.line
}

// Status colours dark enough to read on the light pastels (lighter on dark).
val Good: Color get() = Pastel.palette.good
val Warn: Color get() = Pastel.palette.warn
val Bad: Color get() = Pastel.palette.bad

val Pangolin = FontFamily(Font(R.font.pangolin_regular, FontWeight.Normal))
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun scheme(p: PastelPalette, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.ink, onPrimary = p.page,
    primaryContainer = p.yellow, onPrimaryContainer = p.ink,
    secondary = p.ink, onSecondary = p.page,
    secondaryContainer = p.sky, onSecondaryContainer = p.ink,
    tertiary = p.ink, tertiaryContainer = p.green, onTertiaryContainer = p.ink,
    background = p.page, onBackground = p.ink,
    surface = p.card, onSurface = p.ink,
    surfaceVariant = p.cardLight, onSurfaceVariant = p.inkMuted,
    surfaceContainerLowest = p.card, surfaceContainerLow = p.card,
    surfaceContainer = p.card, surfaceContainerHigh = p.cardLight,
    surfaceContainerHighest = p.cardTeal,
    outline = p.line, outlineVariant = p.hairline,
    error = p.bad, onError = p.page,
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

/** [dark] picks the deep pastel palette; the light one is the FFCS-style default. */
@Composable
fun OrbitTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    if (Pastel.palette != p) Pastel.palette = p
    MaterialTheme(colorScheme = scheme(p, dark), typography = AppType, shapes = AppShapes, content = content)
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
