package com.telecomprofi.kickrhiit.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telecomprofi.kickrhiit.core.StepMode
import com.telecomprofi.kickrhiit.core.TimeMath
import com.telecomprofi.kickrhiit.music.MusicStatus
import com.telecomprofi.kickrhiit.ride.RideService
import com.telecomprofi.kickrhiit.ui.components.DataTile
import com.telecomprofi.kickrhiit.ui.components.GlossyButton
import com.telecomprofi.kickrhiit.ui.components.HrBand
import com.telecomprofi.kickrhiit.ui.components.MetalSurface
import com.telecomprofi.kickrhiit.ui.components.PowerHrArc
import com.telecomprofi.kickrhiit.ui.components.StitchedHeader
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import com.telecomprofi.kickrhiit.ui.vm.RideViewModel

/**
 * The numbers, all of them, on one face.
 *
 * The Ride tab is for the second you are in: needle, step, time left. This one is
 * for the questions you ask at a red light or in a recovery block — how much work
 * so far, what is the normalised power doing, is my heart rate coming down. The
 * arc pairs the two signals that answer most of them: power outside, heart rate
 * inside, on the same sweep.
 */
@Composable
fun DataScreen(vm: RideViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.ride.collectAsState()
    val settings by vm.settings.collectAsState()
    val rider = settings.rider
    val ftp = rider.ftpWatts

    val reserve = if (state.hr > 0) rider.hrPercent(state.hr) else 0.0
    val band = HrBand.of(reserve)
    val onResistance = state.step?.mode == StepMode.RESISTANCE
    val pctFtp = if (ftp <= 0) 0.0 else state.powerW.toDouble() / ftp

    MetalSurface {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 14.dp),
        ) {
            StitchedHeader(
                if (state.isActive) state.workoutName.ifBlank { "Ride" } else "Data",
                trailing = if (state.isActive) TimeMath.mmss(state.elapsedSec) else "not riding",
            )

            Spacer(Modifier.height(6.dp))
            PowerHrArc(
                powerW = state.powerW,
                targetW = state.targetW,
                ftpWatts = ftp,
                hr = state.hr,
                hrReserveFraction = reserve,
                resistancePercent = if (onResistance) state.resistanceTarget else null,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(
                        if (onResistance) "${state.resistanceTarget}%" else "${state.powerW}",
                        style = Skeuo.lcd(Skeuo.LcdAmber, 44),
                    )
                    Text(
                        if (onResistance) "brake level" else "watts",
                        style = Skeuo.lcd(Skeuo.LcdDim, 11),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.hr > 0) "${state.hr} bpm · ${band.label}" else "No strap",
                        style = Skeuo.lcd(if (state.hr > 0) band.colour else Skeuo.LcdDim, 15),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                if (onResistance) {
                    "Brake held at ${state.resistanceTarget}% — the watts are yours"
                } else {
                    "Target ${state.targetW} W  ·  ${(pctFtp * 100).toInt()}% of FTP"
                },
                style = Skeuo.engraved.copy(fontSize = 12.sp, color = Skeuo.Rivet),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            MusicLine(
                title = state.nowPlaying?.let { np ->
                    buildString {
                        append(np.title)
                        np.artist?.let { append("  —  $it") }
                        append("   ·   ${np.effectiveBpm.toInt()} bpm")
                    }
                },
                status = state.musicStatus,
                onMute = { RideService.send(context, RideService.ACTION_TOGGLE_MUSIC) },
                onSkip = { RideService.send(context, RideService.ACTION_NEXT_TRACK) },
            )

            Spacer(Modifier.height(10.dp))
            TileRow(
                DataTileSpec("power", "${state.powerW}", "W", Skeuo.LcdAmber, "target ${state.targetW}"),
                DataTileSpec("cadence", "${state.cadenceRpm}", "rpm", Skeuo.LcdGreen,
                    state.step?.let { "aim ${it.cadenceRpm}" }),
                DataTileSpec("heart", if (state.hr > 0) "${state.hr}" else "--", "bpm",
                    if (state.hr > 0) band.colour else Skeuo.LcdDim, band.label.lowercase()),
            )
            Spacer(Modifier.height(6.dp))
            TileRow(
                DataTileSpec("resistance", "${state.resistanceTarget}", "%", Skeuo.LcdBlue,
                    if (onResistance) "held" else "erg"),
                DataTileSpec("step left", TimeMath.mmss(state.stepRemainingSec), "", Skeuo.LcdAmber,
                    state.step?.label),
                DataTileSpec("ride left", TimeMath.mmss(state.remainingSec), "", Skeuo.LcdGreen,
                    "step ${state.stepIndex + 1}/${state.stepCount}"),
            )
            Spacer(Modifier.height(6.dp))
            TileRow(
                DataTileSpec("average", "${state.avgPowerW}", "W", Skeuo.LcdAmber, "power"),
                DataTileSpec("normalised", "${state.normalisedPowerW}", "W", Skeuo.LcdGreen,
                    "IF %.2f".format(state.intensityFactor)),
                DataTileSpec("best 30s", "${state.bestThirtyW}", "W", Skeuo.LcdRed, "sprint"),
            )
            Spacer(Modifier.height(6.dp))
            TileRow(
                DataTileSpec("work", "%.0f".format(state.workKj), "kJ", Skeuo.LcdGreen, "${state.kcal} kcal"),
                DataTileSpec("avg cadence", "${state.avgCadence}", "rpm", Skeuo.LcdBlue, null),
                DataTileSpec("avg heart", if (state.avgHr > 0) "${state.avgHr}" else "--", "bpm",
                    Skeuo.LcdRed, "max ${state.maxPowerW} W"),
            )

            if (!state.isActive) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nothing running. These are the last ride's numbers until you start " +
                        "another — the arc comes alive as soon as the trainer reports power.",
                    style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
                )
            }
        }
    }
}

/**
 * The music, on one line: what is playing, where it has got to, and the two
 * buttons worth having mid-interval. The same shape as the Ride tab's, so the
 * hand learns one place to reach — and small, because the numbers below it are
 * why this screen exists.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicLine(
    title: String?,
    status: MusicStatus,
    onMute: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title ?: "Nothing queued",
                style = Skeuo.engraved.copy(
                    fontSize = 12.sp,
                    color = when {
                        title == null || status.muted || !status.playing -> Skeuo.Rivet
                        else -> Skeuo.Brass
                    },
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
            )
            Text(
                buildString {
                    if (status.hasDuration) {
                        append(TimeMath.mmss((status.positionMs / 1000).toInt()))
                        append(" / ")
                        append(TimeMath.mmss((status.durationMs / 1000).toInt()))
                    } else {
                        append("--:-- / --:--")
                    }
                    append("   ·   ")
                    append(
                        when {
                            status.muted -> "muted"
                            !status.playing -> "paused"
                            status.ducked -> "ducked ${status.volumePercent}%"
                            else -> "${status.volumePercent}%"
                        }
                    )
                },
                style = Skeuo.lcd(
                    if (status.playing && !status.muted && !status.ducked) Skeuo.LcdGreen else Skeuo.LcdDim,
                    12,
                ),
                maxLines = 1,
            )
        }
        GlossyButton(
            text = if (status.muted) "♪" else "Mute",
            onClick = onMute,
            enabled = title != null,
            modifier = Modifier.width(62.dp).padding(end = 6.dp),
            height = 38.dp,
            corner = 9.dp,
            textSize = 12,
        )
        GlossyButton(
            text = "▶▶",
            onClick = onSkip,
            enabled = title != null,
            modifier = Modifier.width(62.dp),
            height = 38.dp,
            corner = 9.dp,
            textSize = 13,
        )
    }
}

private data class DataTileSpec(
    val caption: String,
    val value: String,
    val unit: String,
    val colour: androidx.compose.ui.graphics.Color,
    val hint: String?,
)

@Composable
private fun TileRow(vararg tiles: DataTileSpec) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.forEach { tile ->
            DataTile(
                caption = tile.caption,
                value = tile.value,
                unit = tile.unit,
                color = tile.colour,
                hint = tile.hint,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
