package com.telecomprofi.kickrhiit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telecomprofi.kickrhiit.core.TracePoint
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import kotlin.math.roundToInt

/**
 * Target against actual, on a plotter behind glass.
 *
 * Two series and no axis labels on purpose: mid-interval the question is never
 * "how many watts exactly" — the readouts answer that — it is "am I on the line
 * or under it", which is a shape, not a number. The target is drawn as a hard
 * stepped line because that is what ERG does to you; your own trace is filled,
 * so the gap between them is the thing the eye lands on.
 */
@Composable
fun EffortGraph(
    trace: List<TracePoint>,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    showHeartRate: Boolean = true,
) {
    InsetPanel(modifier = modifier, corner = corner, fill = Skeuo.LcdBack) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Scale: 150% of FTP, or enough for whatever a sprint produced.
            val peak = trace.maxOfOrNull { maxOf(it.powerW, it.targetW) } ?: 0
            val maxW = maxOf((ftpWatts * 1.5).roundToInt(), peak + 20, 150)

            fun y(watts: Int): Float = h - (watts.toFloat() / maxW).coerceIn(0f, 1f) * h
            fun x(i: Int, n: Int): Float = if (n <= 1) 0f else w * (i.toFloat() / (n - 1))

            // --- graticule: a plotter's grid, every 25% of FTP ---
            var line = 0
            while (line * (ftpWatts / 4) <= maxW && ftpWatts > 0) {
                val watts = line * (ftpWatts / 4)
                val yy = y(watts)
                val isFtp = watts == ftpWatts
                drawLine(
                    color = if (isFtp) Skeuo.LcdAmber.copy(alpha = 0.45f) else Skeuo.LcdGrid,
                    start = Offset(0f, yy),
                    end = Offset(w, yy),
                    strokeWidth = if (isFtp) 1.6f else 1f,
                )
                line++
            }
            repeat(7) { i ->
                val xx = w * (i / 6f)
                drawLine(Skeuo.LcdGrid, Offset(xx, 0f), Offset(xx, h), strokeWidth = 1f)
            }

            if (trace.size < 2) return@Canvas
            val n = trace.size

            // --- your power, filled ---
            val area = Path().apply {
                moveTo(0f, h)
                trace.forEachIndexed { i, p -> lineTo(x(i, n), y(p.powerW)) }
                lineTo(x(n - 1, n), h)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    colors = listOf(Skeuo.LcdGreen.copy(alpha = 0.55f), Skeuo.LcdGreen.copy(alpha = 0.05f)),
                    startY = 0f,
                    endY = h,
                ),
            )
            val stroke = Path().apply {
                trace.forEachIndexed { i, p ->
                    if (i == 0) moveTo(x(i, n), y(p.powerW)) else lineTo(x(i, n), y(p.powerW))
                }
            }
            drawPath(stroke, Skeuo.LcdGreen, style = Stroke(width = 2f, cap = StrokeCap.Round))

            // --- the target, stepped ---
            val target = Path()
            var started = false
            trace.forEachIndexed { i, p ->
                if (p.targetW <= 0) {
                    started = false
                    return@forEachIndexed
                }
                val px = x(i, n)
                val py = y(p.targetW)
                if (!started) {
                    target.moveTo(px, py)
                    started = true
                } else {
                    target.lineTo(px, py)
                }
            }
            drawPath(target, Skeuo.LcdAmber, style = Stroke(width = 2.5f, cap = StrokeCap.Butt))

            // --- heart rate, faint, on its own scale ---
            if (showHeartRate && trace.any { it.hr > 0 }) {
                val hrPath = Path()
                var open = false
                trace.forEachIndexed { i, p ->
                    if (p.hr <= 0) {
                        open = false
                        return@forEachIndexed
                    }
                    val py = h - ((p.hr - 40f) / 160f).coerceIn(0f, 1f) * h
                    if (!open) {
                        hrPath.moveTo(x(i, n), py)
                        open = true
                    } else {
                        hrPath.lineTo(x(i, n), py)
                    }
                }
                drawPath(hrPath, Skeuo.LcdRed.copy(alpha = 0.8f), style = Stroke(width = 1.6f))
            }

            // --- the glass over the plotter ---
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color(0x22FFFFFF), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.4f,
                )
            )
        }
    }
}

/**
 * The whole session as a profile, with a cursor showing where you are in it.
 *
 * On the home screen this is how you tell two workouts apart at a glance —
 * six spikes with long flats between them is a different afternoon from a
 * staircase — and mid-ride it answers "how much of this is left".
 */
@Composable
fun ProfileStrip(
    workout: com.telecomprofi.kickrhiit.core.Workout,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    corner: Dp = 8.dp,
) {
    InsetPanel(modifier = modifier, corner = corner, fill = Skeuo.LcdBackWarm) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f || workout.totalSec <= 0) return@Canvas
            val peak = workout.peakPct.coerceAtLeast(1.2)

            var t = 0
            workout.steps.forEach { step ->
                val x0 = w * (t.toFloat() / workout.totalSec)
                val x1 = w * ((t + step.seconds).toFloat() / workout.totalSec)
                val pct = if (step.isSprint) peak else step.peakPct
                val barH = (h * (pct / peak)).toFloat().coerceIn(2f, h)
                val colour = when {
                    step.isSprint -> Skeuo.LcdRed
                    pct >= 1.05 -> Color(0xFFE08A1E)
                    pct >= 0.85 -> Skeuo.LcdAmber
                    pct >= 0.6 -> Skeuo.LcdGreen
                    else -> Skeuo.LcdDim
                }
                drawRect(
                    color = colour.copy(alpha = 0.85f),
                    topLeft = Offset(x0, h - barH),
                    size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(1.5f), barH),
                )
                t += step.seconds
            }

            // The FTP line, so the shape is readable against something.
            val ftpY = h - (h * (1.0 / peak)).toFloat()
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(0f, ftpY),
                end = Offset(w, ftpY),
                strokeWidth = 1f,
            )

            if (progress > 0f) {
                val cx = w * progress.coerceIn(0f, 1f)
                drawRect(Color(0x66000000), size = androidx.compose.ui.geometry.Size(cx, h))
                drawLine(Color.White, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2f)
            }
        }
    }
}
