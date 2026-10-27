package com.telecomprofi.kickrhiit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.telecomprofi.kickrhiit.core.PowerZone
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 150f
private const val SWEEP = 240f

/**
 * The power dial: a real needle on a printed face, under glass.
 *
 * A needle is the right instrument here and a bar is not. In ERG mode the number
 * you care about is not the wattage but the *error* — am I above or below the
 * target the trainer is holding — and a needle against a marked target sector
 * answers that in peripheral vision, without reading anything.
 */
@Composable
fun PowerDial(
    powerW: Int,
    targetW: Int,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    freeRide: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    // Scale ends at 200% of FTP, or above the target if the target is higher.
    val maxW = maxOf((ftpWatts * 2.0).roundToInt(), targetW + 50, 200)
    val fraction = (powerW.toFloat() / maxW).coerceIn(0f, 1f)
    // A needle has mass: it should overshoot slightly and settle, not teleport.
    val needle by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "needle",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)

            // --- the bezel: a machined ring around the face ---
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Skeuo.MetalTop, Skeuo.MetalBottom),
                    start = Offset(centre.x, centre.y - radius),
                    end = Offset(centre.x, centre.y + radius),
                ),
                radius = radius,
                center = centre,
            )
            drawCircle(Color(0x88000000), radius = radius * 0.93f, center = centre, style = Stroke(2f))
            // --- the face ---
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF23292E), Color(0xFF12161A)),
                    center = Offset(centre.x, centre.y - radius * 0.3f),
                    radius = radius,
                ),
                radius = radius * 0.9f,
                center = centre,
            )

            val dialRadius = radius * 0.78f
            val stroke = radius * 0.075f

            // --- the coloured zone arcs, painted onto the face ---
            PowerZone.entries.forEach { zone ->
                val lo = (zone.lowPct * ftpWatts / maxW).coerceIn(0.0, 1.0)
                val hi = (zone.highPct * ftpWatts / maxW).coerceIn(0.0, 1.0)
                if (hi <= lo) return@forEach
                drawArc(
                    color = Color(zone.argb).copy(alpha = 0.75f),
                    startAngle = START_ANGLE + SWEEP * lo.toFloat(),
                    sweepAngle = SWEEP * (hi - lo).toFloat(),
                    useCenter = false,
                    topLeft = Offset(centre.x - dialRadius, centre.y - dialRadius),
                    size = Size(dialRadius * 2, dialRadius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }

            // --- the target sector: where the trainer is holding you ---
            if (!freeRide && targetW > 0) {
                val t = (targetW.toFloat() / maxW).coerceIn(0f, 1f)
                val band = 0.03f
                drawArc(
                    color = Color.White.copy(alpha = 0.9f),
                    startAngle = START_ANGLE + SWEEP * (t - band).coerceAtLeast(0f),
                    sweepAngle = SWEEP * (band * 2),
                    useCenter = false,
                    topLeft = Offset(centre.x - dialRadius, centre.y - dialRadius),
                    size = Size(dialRadius * 2, dialRadius * 2),
                    style = Stroke(width = stroke * 1.35f, cap = StrokeCap.Butt),
                )
            }

            // --- tick marks every 10% of the scale, longer every 50 W ---
            val ticks = 20
            for (i in 0..ticks) {
                val f = i / ticks.toFloat()
                val angle = Math.toRadians((START_ANGLE + SWEEP * f).toDouble())
                val major = i % 5 == 0
                val outer = dialRadius - stroke * 0.75f
                val inner = outer - if (major) radius * 0.11f else radius * 0.055f
                drawLine(
                    color = if (major) Color(0xFFE6EAEF) else Color(0x88C9D0D8),
                    start = Offset(
                        centre.x + (outer * cos(angle)).toFloat(),
                        centre.y + (outer * sin(angle)).toFloat(),
                    ),
                    end = Offset(
                        centre.x + (inner * cos(angle)).toFloat(),
                        centre.y + (inner * sin(angle)).toFloat(),
                    ),
                    strokeWidth = if (major) 3f else 1.5f,
                    cap = StrokeCap.Round,
                )
                if (major) {
                    drawDialNumber(
                        text = (maxW * f).roundToInt().toString(),
                        centre = centre,
                        radius = inner - radius * 0.10f,
                        angleDeg = START_ANGLE + SWEEP * f,
                        sizePx = radius * 0.11f,
                    )
                }
            }

            // --- the needle ---
            val needleAngle = START_ANGLE + SWEEP * needle
            rotate(degrees = needleAngle, pivot = centre) {
                val len = dialRadius - stroke
                val path = Path().apply {
                    moveTo(centre.x - radius * 0.05f, centre.y - radius * 0.018f)
                    lineTo(centre.x + len, centre.y - radius * 0.006f)
                    lineTo(centre.x + len, centre.y + radius * 0.006f)
                    lineTo(centre.x - radius * 0.05f, centre.y + radius * 0.018f)
                    close()
                }
                drawPath(path, Color(0x66000000), style = Stroke(width = 3f))
                drawPath(path, if (freeRide) Skeuo.LcdAmber else Color(0xFFF04A32))
            }
            // --- the hub, screwed down over the needle's tail ---
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Skeuo.MetalTop, Skeuo.MetalBottom),
                    start = Offset(centre.x, centre.y - radius * 0.1f),
                    end = Offset(centre.x, centre.y + radius * 0.1f),
                ),
                radius = radius * 0.09f,
                center = centre,
            )
            drawCircle(Color(0x99000000), radius = radius * 0.09f, center = centre, style = Stroke(1.5f))

            // --- the glass: one sweep of light across the upper left ---
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x33FFFFFF), Color(0x05FFFFFF)),
                    start = Offset(centre.x - radius, centre.y - radius),
                    end = Offset(centre.x, centre.y + radius * 0.2f),
                ),
                startAngle = 160f,
                sweepAngle = 130f,
                useCenter = true,
                topLeft = Offset(centre.x - radius * 0.9f, centre.y - radius * 0.9f),
                size = Size(radius * 1.8f, radius * 1.8f),
            )
        }
        content()
    }
}

/** Numerals printed on the dial face, upright rather than radial. */
private fun DrawScope.drawDialNumber(
    text: String,
    centre: Offset,
    radius: Float,
    angleDeg: Float,
    sizePx: Float,
) {
    val angle = Math.toRadians(angleDeg.toDouble())
    val x = centre.x + (radius * cos(angle)).toFloat()
    val y = centre.y + (radius * sin(angle)).toFloat()
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(200, 208, 216)
            textSize = sizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawText(text, x, y + sizePx * 0.35f, paint)
    }
}

/** Kept for the unit tests: where the needle sits, 0..1, for a given power. */
fun needleFraction(powerW: Int, ftpWatts: Int, targetW: Int): Float {
    val maxW = maxOf((ftpWatts * 2.0).roundToInt(), targetW + 50, 200)
    return (powerW.toFloat() / maxW).coerceIn(0f, 1f)
}
