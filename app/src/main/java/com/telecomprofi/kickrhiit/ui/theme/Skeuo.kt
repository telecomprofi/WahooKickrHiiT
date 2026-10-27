package com.telecomprofi.kickrhiit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette of an instrument, not an app.
 *
 * This is deliberately the 2010 idiom: brushed aluminium, bevelled glass, an
 * amber LCD, buttons that look pressable because they are lit from above. The
 * reason it suits a turbo trainer better than a flat design is that you read it
 * at arm's length, sweating, at a glance — and a raised control that catches the
 * light is findable in peripheral vision in a way a flat rectangle is not.
 */
object Skeuo {

    // --- the case ---
    val MetalTop = Color(0xFF6E747C)
    val MetalMid = Color(0xFF4A5058)
    val MetalBottom = Color(0xFF2C3138)
    val MetalEdgeLight = Color(0x66FFFFFF)
    val MetalEdgeDark = Color(0x99000000)
    val Bezel = Color(0xFF1A1D22)

    // --- the glass ---
    val GlassTop = Color(0x38FFFFFF)
    val GlassBottom = Color(0x08FFFFFF)

    // --- the display ---
    val LcdBack = Color(0xFF16201B)
    val LcdBackWarm = Color(0xFF1E1B12)
    val LcdGrid = Color(0x2247D17E)
    val LcdAmber = Color(0xFFFFB33A)
    val LcdGreen = Color(0xFF5CE08A)
    val LcdDim = Color(0xFF3F5B4A)
    val LcdRed = Color(0xFFFF5A44)
    val LcdBlue = Color(0xFF6FC5FF)

    // --- accents ---
    val Brass = Color(0xFFC9A227)
    val Rivet = Color(0xFF9AA3AD)
    val Felt = Color(0xFF23282E)
    val Stitch = Color(0x55D9C79A)

    val Danger = Color(0xFFD9432E)
    val Go = Color(0xFF3FA34D)

    /** Vertical brushed-metal fill for a panel of the given height. */
    fun metal(heightPx: Float): Brush = Brush.linearGradient(
        colors = listOf(MetalTop, MetalMid, MetalBottom),
        start = Offset(0f, 0f),
        end = Offset(0f, heightPx),
    )

    /** The specular sheen that sits over the top half of anything glassy. */
    fun glass(heightPx: Float): Brush = Brush.verticalGradient(
        colors = listOf(GlassTop, Color.Transparent),
        startY = 0f,
        endY = heightPx * 0.55f,
    )

    /** A button face: lit from above, darker where it curves away. */
    fun button(heightPx: Float, tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = 1f).lighten(0.22f),
            tint,
            tint.darken(0.28f),
        ),
        startY = 0f,
        endY = heightPx,
    )

    fun Color.lighten(amount: Float): Color = Color(
        red = (red + (1f - red) * amount).coerceIn(0f, 1f),
        green = (green + (1f - green) * amount).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
        alpha = alpha,
    )

    fun Color.darken(amount: Float): Color = Color(
        red = (red * (1f - amount)).coerceIn(0f, 1f),
        green = (green * (1f - amount)).coerceIn(0f, 1f),
        blue = (blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = alpha,
    )

    /** Engraved text: a dark line below, so it reads as cut into the metal. */
    val engraved = TextStyle(
        color = Color(0xFFD7DCE3),
        shadow = Shadow(Color(0xCC000000), Offset(0f, 1.5f), 1.5f),
        fontWeight = FontWeight.SemiBold,
    )

    /** Raised text on a dark face, lit from above. */
    val embossed = TextStyle(
        color = Color(0xFF20242A),
        shadow = Shadow(Color(0x88FFFFFF), Offset(0f, 1f), 0.5f),
        fontWeight = FontWeight.Bold,
    )

    /** Segment-display numerals, with the glow the glass would give them. */
    fun lcd(color: Color, size: Int) = TextStyle(
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        shadow = Shadow(color.copy(alpha = 0.55f), Offset(0f, 0f), 14f),
    )
}

private val Scheme = darkColorScheme(
    primary = Skeuo.Brass,
    onPrimary = Color.Black,
    background = Skeuo.Bezel,
    onBackground = Color(0xFFD7DCE3),
    surface = Skeuo.MetalMid,
    onSurface = Color(0xFFD7DCE3),
    error = Skeuo.Danger,
)

private val KickrTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 56.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun KickrHiitTheme(content: @Composable () -> Unit) {
    // One look, day or night: an instrument panel does not have a light mode.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = KickrTypography, content = content)
}
