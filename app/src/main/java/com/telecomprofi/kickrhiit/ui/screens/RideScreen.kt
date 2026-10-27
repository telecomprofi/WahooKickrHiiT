package com.telecomprofi.kickrhiit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telecomprofi.kickrhiit.core.StepKind
import com.telecomprofi.kickrhiit.core.TimeMath
import com.telecomprofi.kickrhiit.core.Workouts
import com.telecomprofi.kickrhiit.ride.RideHub
import com.telecomprofi.kickrhiit.ride.RideService
import com.telecomprofi.kickrhiit.ride.RideStatus
import com.telecomprofi.kickrhiit.ui.components.EffortGraph
import com.telecomprofi.kickrhiit.ui.components.GlossyButton
import com.telecomprofi.kickrhiit.ui.components.InsetPanel
import com.telecomprofi.kickrhiit.ui.components.LcdReadout
import com.telecomprofi.kickrhiit.ui.components.MetalSurface
import com.telecomprofi.kickrhiit.ui.components.PowerDial
import com.telecomprofi.kickrhiit.ui.components.ProfileStrip
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import com.telecomprofi.kickrhiit.ui.vm.RideViewModel

/**
 * The cockpit. Fixed layout, no scrolling: at 110 rpm with your heart at 170
 * you get one glance, and it has to land on the needle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RideScreen(vm: RideViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.ride.collectAsState()
    val settings by vm.settings.collectAsState()
    val ftp = settings.rider.ftpWatts
    val workout = remember(state.workoutId, settings.ride.sprintResistance) {
        Workouts.byId(
            state.workoutId.ifBlank { settings.ride.selectedWorkoutId },
            settings.ride.sprintResistance,
        )
    }

    // Same permission dance as the Home tab: without these the ride starts and
    // then finds neither trainer nor strap.
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { RideService.start(context) }

    fun startRide() {
        val needed = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) RideService.start(context) else permissions.launch(needed.toTypedArray())
    }

    val kindColour = when (state.kind) {
        StepKind.SPRINT -> Skeuo.LcdRed
        StepKind.WORK -> Skeuo.LcdAmber
        StepKind.RECOVERY -> Skeuo.LcdBlue
        StepKind.WARMUP -> Skeuo.LcdGreen
        StepKind.COOLDOWN -> Skeuo.LcdDim
    }

    MetalSurface {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 10.dp),
        ) {
            StepBanner(
                label = state.step?.label ?: workout.name,
                kindColour = kindColour,
                remaining = TimeMath.mmss(state.stepRemainingSec),
                stepOf = "${state.stepIndex + 1} / ${state.stepCount}",
                progress = state.stepProgress,
                next = state.nextStep?.label,
            )

            Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                PowerDial(
                    powerW = state.powerW,
                    targetW = state.targetW,
                    ftpWatts = ftp,
                    freeRide = state.isFreeRide,
                    modifier = Modifier.aspectRatio(1f).fillMaxSize(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 34.dp),
                    ) {
                        Text(
                            state.powerW.toString(),
                            style = Skeuo.lcd(if (state.isFreeRide) Skeuo.LcdAmber else Skeuo.LcdGreen, 52),
                        )
                        Text("watts", style = Skeuo.lcd(Skeuo.LcdDim, 11))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                state.isFreeRide -> "FREE · BRAKE ${state.resistanceTarget}"
                                state.targetW > 0 -> "TARGET ${state.targetW} W"
                                else -> "—"
                            },
                            style = Skeuo.lcd(Skeuo.LcdAmber, 13),
                        )
                    }
                }

                if (state.countdown in 1..5) {
                    CountdownOverlay(state.countdown)
                }
            }

            Spacer(Modifier.height(6.dp))

            InsetPanel(Modifier.fillMaxWidth().height(74.dp)) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LcdReadout(
                        value = if (state.cadenceRpm > 0) "${state.cadenceRpm}" else "--",
                        unit = "",
                        caption = "rpm · ${state.step?.cadenceRpm ?: 0}",
                        color = Skeuo.LcdBlue,
                        size = 26,
                    )
                    LcdReadout(
                        value = if (state.hr > 0) "${state.hr}" else "--",
                        unit = "",
                        caption = "bpm",
                        color = Skeuo.LcdRed,
                        size = 26,
                    )
                    LcdReadout(
                        value = "%.0f".format(state.workKj),
                        unit = "",
                        caption = "kJ",
                        color = Skeuo.LcdGreen,
                        size = 26,
                    )
                    LcdReadout(
                        value = TimeMath.mmss(state.remainingSec),
                        unit = "",
                        caption = "left",
                        color = Skeuo.LcdAmber,
                        size = 26,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            EffortGraph(
                trace = state.trace,
                ftpWatts = ftp,
                modifier = Modifier.fillMaxWidth().height(116.dp),
            )

            Spacer(Modifier.height(6.dp))
            ProfileStrip(
                workout = workout,
                progress = state.sessionProgress,
                modifier = Modifier.fillMaxWidth().height(30.dp),
            )

            Spacer(Modifier.height(6.dp))
            StatusLine(state.trainerConnected, state.trainerControlling, state.hrmConnected, state.simulated)

            state.nowPlaying?.let { np ->
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(np.title)
                            np.artist?.let { append(" — $it") }
                            append("   ·   ${np.effectiveBpm.toInt()} bpm")
                            val ms = state.musicStatus
                            append("   ·   ")
                            if (ms.hasDuration) {
                                append(TimeMath.mmss((ms.positionMs / 1000).toInt()))
                                append("/")
                                append(TimeMath.mmss((ms.durationMs / 1000).toInt()))
                            } else {
                                append("--:--")
                            }
                            // The number that tells silence apart from a pause.
                            append(
                                when {
                                    ms.muted -> "  muted"
                                    !ms.playing -> "  paused"
                                    ms.ducked -> "  ducked ${ms.volumePercent}%"
                                    else -> "  ${ms.volumePercent}%"
                                }
                            )
                        },
                        style = Skeuo.engraved.copy(
                            fontSize = 12.sp,
                            color = if (state.musicMuted) Skeuo.Rivet else Skeuo.Brass,
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(),
                    )
                    GlossyButton(
                        text = if (state.musicMuted) "♪" else "Mute",
                        onClick = { RideService.send(context, RideService.ACTION_TOGGLE_MUSIC) },
                        modifier = Modifier.width(64.dp).padding(end = 6.dp),
                        height = 38.dp,
                        corner = 9.dp,
                        textSize = 12,
                    )
                    GlossyButton(
                        text = "▶▶",
                        onClick = { RideService.send(context, RideService.ACTION_NEXT_TRACK) },
                        modifier = Modifier.width(64.dp),
                        height = 38.dp,
                        corner = 9.dp,
                        textSize = 13,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Idle means there is no engine to resume; the button that said
                // RESUME simply did nothing. Here it starts the ride instead.
                GlossyButton(
                    text = when {
                        !state.isActive -> "Start"
                        state.status == RideStatus.RUNNING -> "Pause"
                        else -> "Resume"
                    },
                    onClick = {
                        if (state.isActive) RideHub.togglePause() else startRide()
                    },
                    tint = if (state.isActive) Skeuo.Brass else Skeuo.Go,
                    textColor = if (state.isActive) Color(0xFF1A1A1A) else Color(0xFFF2F5F8),
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
                GlossyButton(
                    text = "Skip",
                    onClick = { RideHub.skipStep() },
                    enabled = state.isActive,
                    modifier = Modifier.weight(0.7f),
                    height = 44.dp,
                )
                GlossyButton(
                    text = "End",
                    onClick = { RideService.send(context, RideService.ACTION_STOP) },
                    enabled = state.isActive,
                    tint = Skeuo.Danger,
                    modifier = Modifier.weight(0.7f),
                    height = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun StepBanner(
    label: String,
    kindColour: Color,
    remaining: String,
    stepOf: String,
    progress: Float,
    next: String?,
) {
    val animated by animateFloatAsState(progress, tween(600), label = "step-progress")
    InsetPanel(Modifier.fillMaxWidth().height(64.dp), fill = Skeuo.LcdBackWarm) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .fillMaxSize()
                .background(kindColour.copy(alpha = 0.16f)),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = Skeuo.lcd(kindColour, 17), maxLines = 1)
                Text(
                    buildString {
                        append("STEP $stepOf")
                        if (next != null) append("   Next: $next")
                    },
                    style = Skeuo.lcd(Skeuo.LcdDim, 10),
                    maxLines = 1,
                )
            }
            Text(remaining, style = Skeuo.lcd(kindColour, 30))
        }
    }
}

/** Three, two, one. A sprint start has to be exact, so it takes the screen. */
@Composable
private fun CountdownOverlay(seconds: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(500.dp))
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$seconds", style = Skeuo.lcd(Skeuo.LcdRed, 96))
            Text("Get ready", style = Skeuo.lcd(Skeuo.LcdAmber, 16), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatusLine(
    trainer: Boolean,
    controlling: Boolean,
    hrm: Boolean,
    simulated: Boolean,
) {
    val text = buildString {
        append(
            when {
                simulated -> "Simulated"
                controlling -> "KICKR · ERG"
                trainer -> "KICKR · no control"
                else -> "No trainer"
            }
        )
        append("   ·   ")
        append(if (hrm) "HR ok" else "No strap")
    }
    Text(
        text,
        style = Skeuo.lcd(
            when {
                simulated -> Skeuo.LcdBlue
                controlling -> Skeuo.LcdGreen
                else -> Skeuo.LcdAmber
            },
            11,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
