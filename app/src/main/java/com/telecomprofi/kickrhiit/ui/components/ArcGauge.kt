package com.telecomprofi.kickrhiit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.telecomprofi.kickrhiit.core.PowerZone
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

private const val ARC_START = 160f
private const val ARC_SWEEP = 220f

/**
 * Three heart-rate zones, the same three the walking app uses and the same three
 * a watch shows: below the work zone, in it, above it. Bounds are shares of
 * heart-rate reserve.
 */
enum class HrBand(val label: String, val low: Double, val high: Double, val colour: Color) {
    EASY("Easy", 0.00, 0.45, Color(0xFF30D158)),
    BRISK("Brisk", 0.45, 0.58, Color(0xFFFFD60A)),
    HARD("Hard", 0.58, 1.20, Color(0xFFFF9F0A)),
    ;

    companion object {
        fun of(reserveFraction: Double): HrBand =
            entries.firstOrNull { reserveFraction < it.high } ?: HARD
    }
}

/**
 * Two concentric arcs on one face: power outside, heart rate inside.
 *
 * The pairing is the whole point of the screen. Power is what you are doing this
 * second and it moves instantly; heart rate is what it is costing you and it
 * lags a minute behind. Seen apart they are two numbers; seen on the same face,
 * the gap between them *is* the training — a sprint that spikes the outer arc
 * and barely moves the inner one is a different session from one where the inner
 * arc never comes down between efforts.
 */
@Composable
fun PowerHrArc(
    powerW: Int,
    targetW: Int,
    ftpWatts: Int,
    hr: Int,
    hrReserveFraction: Double,
    modifier: Modifier = Modifier,
    /** Shown instead of watts when the step is holding a brake level. */
    resistancePercent: Int? = null,
    content: @Composable () -> Unit = {},
) {
    val maxW = maxOf(ftpWatts * 2, targetW + 50, 200)
    val powerFraction = (powerW.toFloat() / maxW).coerceIn(0f, 1f)
    val targetFraction = (targetW.toFloat() / maxW).coerceIn(0f, 1f)
    val hrFraction = hrReserveFraction.coerceIn(0.0, 1.0).toFloat()

    val power by animateFloatAsState(powerFraction, tween(320), label = "power")
    val heart by animateFloatAsState(hrFraction, tween(600), label = "hr")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f - dp(10f)
            val centre = Offset(size.width / 2f, size.height * 0.62f)
            val powerStroke = dp(20f)
            val hrStroke = dp(12f)
            val hrRadius = outer - powerStroke - dp(8f)

            // --- the power track, banded by Coggan zone -----------------------
            arcTrack(centre, outer, powerStroke, Skeuo.Bezel)
            PowerZone.entries.forEach { zone ->
                val from = (zone.lowPct * ftpWatts / maxW).coerceIn(0.0, 1.0)
                val to = (zone.highPct * ftpWatts / maxW).coerceIn(0.0, 1.0)
                if (to > from) {
                    drawArc(
                        color = Color(zone.argb).copy(alpha = 0.30f),
                        startAngle = ARC_START + (from * ARC_SWEEP).toFloat(),
                        sweepAngle = ((to - from) * ARC_SWEEP).toFloat(),
                        useCenter = false,
                        topLeft = Offset(centre.x - outer, centre.y - outer),
                        size = Size(outer * 2, outer * 2),
                        style = Stroke(width = powerStroke, cap = StrokeCap.Butt),
                    )
                }
            }
            // What you are actually producing, drawn over the bands.
            val zoneNow = PowerZone.forPct(if (ftpWatts <= 0) 0.0 else powerW.toDouble() / ftpWatts)
            drawArc(
                color = Color(zoneNow.argb),
                startAngle = ARC_START,
                sweepAngle = power * ARC_SWEEP,
                useCenter = false,
                topLeft = Offset(centre.x - outer, centre.y - outer),
                size = Size(outer * 2, outer * 2),
                style = Stroke(width = powerStroke - dp(6f), cap = StrokeCap.Round),
            )
            // The target, as a hairline across the track: the thing to sit on.
            if (targetW > 0) tick(centre, outer, powerStroke, targetFraction, Color.White)

            // --- the heart-rate track, banded by zone -------------------------
            arcTrack(centre, hrRadius, hrStroke, Skeuo.Bezel)
            HrBand.entries.forEach { band ->
                drawArc(
                    color = band.colour.copy(alpha = 0.28f),
                    startAngle = ARC_START + (band.low.coerceIn(0.0, 1.0) * ARC_SWEEP).toFloat(),
                    sweepAngle = ((band.high.coerceAtMost(1.0) - band.low) * ARC_SWEEP)
                        .coerceAtLeast(0.0).toFloat(),
                    useCenter = false,
                    topLeft = Offset(centre.x - hrRadius, centre.y - hrRadius),
                    size = Size(hrRadius * 2, hrRadius * 2),
                    style = Stroke(width = hrStroke, cap = StrokeCap.Butt),
                )
            }
            if (hr > 0) {
                drawArc(
                    color = HrBand.of(hrReserveFraction).colour,
                    startAngle = ARC_START,
                    sweepAngle = heart * ARC_SWEEP,
                    useCenter = false,
                    topLeft = Offset(centre.x - hrRadius, centre.y - hrRadius),
                    size = Size(hrRadius * 2, hrRadius * 2),
                    style = Stroke(width = hrStroke - dp(4f), cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

private fun DrawScope.arcTrack(centre: Offset, radius: Float, stroke: Float, colour: Color) {
    drawArc(
        color = colour,
        startAngle = ARC_START,
        sweepAngle = ARC_SWEEP,
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** A radial hairline across a track — where the trainer is holding you. */
private fun DrawScope.tick(
    centre: Offset,
    radius: Float,
    stroke: Float,
    fraction: Float,
    colour: Color,
) {
    val angle = (ARC_START + fraction * ARC_SWEEP) * PI.toFloat() / 180f
    val inner = radius - stroke / 2f
    val outer = radius + stroke / 2f
    drawLine(
        color = colour,
        start = Offset(centre.x + cos(angle) * inner, centre.y + sin(angle) * inner),
        end = Offset(centre.x + cos(angle) * outer, centre.y + sin(angle) * outer),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
}

/** dp in a Canvas, where everything is pixels. */
private fun DrawScope.dp(value: Float): Float = value * density
