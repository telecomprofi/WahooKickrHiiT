package com.telecomprofi.kickrhiit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import kotlin.random.Random

/**
 * The case the instruments are mounted in: brushed aluminium, lit from above,
 * with the faint vertical grain that gives brushed metal its name.
 */
@Composable
fun MetalSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val grain = remember { List(160) { Random(it * 7919).nextFloat() } }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Skeuo.metal(size.height))
                // The brushing: hairlines of light and shade down the panel.
                grain.forEachIndexed { i, r ->
                    val x = size.width * (i / grain.size.toFloat())
                    drawLine(
                        color = if (r > 0.5f) Color(0x0EFFFFFF) else Color(0x12000000),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f + r,
                    )
                }
                // A soft pool of light from the top left, as if under a lamp.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x22FFFFFF), Color.Transparent),
                        center = Offset(size.width * 0.3f, 0f),
                        radius = size.height * 0.8f,
                    )
                )
            },
    ) { content() }
}

/**
 * A recessed panel — anything that should read as set *into* the case: the
 * display, a list, a group of settings.
 */
@Composable
fun InsetPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    fill: Color = Skeuo.LcdBack,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .drawBehind {
                // Inner shadow at the top, bounce light at the bottom: the two
                // cues that say "below the surface" rather than "on it".
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x77000000), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.22f,
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x1AFFFFFF)),
                        startY = size.height * 0.8f,
                        endY = size.height,
                    )
                )
            }
            .border(1.dp, Skeuo.MetalEdgeDark, RoundedCornerShape(corner)),
        content = content,
    )
}

/**
 * A raised, glossy control. Lit from above, darker underneath, and it sinks
 * when pressed — which is the whole reason this idiom survives on machinery.
 */
@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Skeuo.MetalMid,
    textColor: Color = Color(0xFFF2F5F8),
    height: Dp = 46.dp,
    enabled: Boolean = true,
    corner: Dp = 12.dp,
    subtitle: String? = null,
    textSize: Int = 16,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val face = if (enabled) tint else tint.let { Skeuo.run { it.darken(0.35f) } }

    Box(
        modifier
            .height(height)
            .shadow(if (pressed || !enabled) 0.dp else 6.dp, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(Skeuo.button(with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }, face))
            .drawBehind {
                if (pressed) {
                    drawRect(Color(0x55000000))
                } else {
                    // The bright line along the top edge is what sells "raised".
                    drawLine(
                        color = Color(0x99FFFFFF),
                        start = Offset(corner.toPx(), 1f),
                        end = Offset(size.width - corner.toPx(), 1f),
                        strokeWidth = 1.5f,
                    )
                }
            }
            .border(1.dp, Color(0x55000000), RoundedCornerShape(corner))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = Skeuo.engraved.copy(
                    color = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                    fontSize = textSize.sp,
                ),
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = Skeuo.engraved.copy(color = textColor.copy(alpha = 0.7f), fontSize = 11.sp),
                )
            }
        }
    }
}

/**
 * The stitched-leather header of a list — the single most 2010 thing here, and
 * the clearest way to say "this is a section" without a flat grey bar.
 */
@Composable
fun StitchedHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF3A3F46), Color(0xFF23272C)))
            )
            .drawBehind {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawRoundRect(
                    color = Skeuo.Stitch,
                    topLeft = Offset(6f, 6f),
                    size = Size(size.width - 12f, size.height - 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 1.5f, pathEffect = dash),
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = Skeuo.engraved.copy(fontSize = 12.sp, letterSpacing = 0.4.sp))
            if (trailing != null) {
                Text(trailing, style = Skeuo.engraved.copy(fontSize = 12.sp, color = Skeuo.Brass))
            }
        }
    }
}

/** One reading on the LCD: a big glowing number with its unit and caption. */
@Composable
fun LcdReadout(
    value: String,
    unit: String,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = Skeuo.LcdAmber,
    size: Int = 34,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Skeuo.lcd(color, size), maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = Skeuo.lcd(color.copy(alpha = 0.75f), (size * 0.4f).toInt()),
                    modifier = Modifier.padding(start = 2.dp, bottom = (size * 0.12f).dp),
                )
            }
        }
        Text(
            caption,
            style = Skeuo.lcd(Skeuo.LcdDim, (size * 0.28f).coerceAtLeast(9f).toInt()),
            maxLines = 1,
        )
    }
}

/** A labelled value with two machined buttons either side of it. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = Skeuo.engraved.copy(fontSize = 14.sp))
            Text("$value$suffix", style = Skeuo.lcd(Skeuo.LcdAmber, 20))
        }
        // A comfortable target without being a slab: 52 x 44 clears the 48dp
        // guideline on the axis that matters for a row of controls, and the
        // glyphs stay large enough to hit without looking.
        GlossyButton(
            text = "–",
            onClick = { onChange((value - step).coerceIn(range.first, range.last)) },
            enabled = value > range.first,
            modifier = Modifier.width(52.dp).padding(end = 6.dp),
            height = 44.dp,
            corner = 9.dp,
            textSize = 20,
        )
        GlossyButton(
            text = "+",
            onClick = { onChange((value + step).coerceIn(range.first, range.last)) },
            enabled = value < range.last,
            modifier = Modifier.width(52.dp),
            height = 44.dp,
            corner = 9.dp,
            textSize = 20,
        )
    }
}

/** A physical-looking toggle: a knob that slides in a recessed track. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Skeuo.engraved.copy(fontSize = 14.sp))
            if (description != null) {
                Text(
                    description,
                    style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
                )
            }
        }
        Box(
            Modifier
                .height(30.dp)
                .width(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (checked) Skeuo.Go else Skeuo.Bezel)
                .border(1.dp, Skeuo.MetalEdgeDark, RoundedCornerShape(15.dp)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .height(24.dp)
                    .width(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFF2F4F7), Color(0xFFB8BEC6)))
                    )
            )
        }
    }
}

/** A row of a settings list: a label and a value, engraved. */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Skeuo.Brass) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Skeuo.engraved.copy(fontSize = 14.sp))
        Text(value, style = Skeuo.engraved.copy(fontSize = 14.sp, color = accent), maxLines = 1)
    }
}

/**
 * One rectangular readout on the Data screen: a caption, a big glowing number,
 * and a unit. Deliberately square-shouldered rather than round — a grid of these
 * reads as an instrument panel, and a panel is scanned, not read.
 */
@Composable
fun DataTile(
    caption: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    color: Color = Skeuo.LcdAmber,
    hint: String? = null,
) {
    InsetPanel(modifier.height(70.dp), corner = 8.dp) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                caption,
                style = Skeuo.lcd(Skeuo.LcdDim, 10),
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Skeuo.lcd(color, 24), maxLines = 1)
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = Skeuo.lcd(color.copy(alpha = 0.7f), 11),
                        modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                    )
                }
            }
            Text(
                hint.orEmpty(),
                style = Skeuo.lcd(Skeuo.LcdDim, 10),
                maxLines = 1,
            )
        }
    }
}
