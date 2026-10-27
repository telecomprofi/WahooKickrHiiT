package com.telecomprofi.kickrhiit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telecomprofi.kickrhiit.hrm.HrmState
import com.telecomprofi.kickrhiit.hrm.ScannedDevice
import com.telecomprofi.kickrhiit.trainer.TrainerState
import com.telecomprofi.kickrhiit.ui.components.GlossyButton
import com.telecomprofi.kickrhiit.ui.components.InsetPanel
import com.telecomprofi.kickrhiit.ui.components.Stepper
import com.telecomprofi.kickrhiit.ui.components.StitchedHeader
import com.telecomprofi.kickrhiit.ui.components.SwitchRow
import com.telecomprofi.kickrhiit.ui.components.MetalSurface
import com.telecomprofi.kickrhiit.ui.components.ValueRow
import com.telecomprofi.kickrhiit.ui.theme.Skeuo
import com.telecomprofi.kickrhiit.ui.vm.SettingsViewModel
import kotlin.math.roundToInt

private enum class Scanning { NONE, TRAINER, HRM }

/**
 * The back of the machine: the rider's numbers, the two radios, and the few
 * knobs worth having. Everything here is stored the moment it is touched —
 * there is no Save button, because there is nothing to lose by writing.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val trainerDevices by vm.trainerDevices.collectAsState()
    val hrmDevices by vm.hrmDevices.collectAsState()
    val trainerState by vm.trainerState.collectAsState()
    val hrmState by vm.hrmState.collectAsState()
    var scanning by remember { mutableStateOf(Scanning.NONE) }

    val rider = settings.rider
    val devices = settings.devices
    val coach = settings.coach
    val ride = settings.ride

    // Scanning without these is a silent empty list, which reads as a broken
    // radio rather than a missing permission — so ask before starting.
    var pending by remember { mutableStateOf(Scanning.NONE) }
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted.values.all { it }
        val want = pending
        pending = Scanning.NONE
        if (ok && want != Scanning.NONE) {
            scanning = want
            if (want == Scanning.TRAINER) vm.scanTrainer() else vm.scanHrm()
        }
    }

    fun beginScan(which: Scanning) {
        if (scanning == which) {
            scanning = Scanning.NONE
            vm.stopScans()
            return
        }
        val needed = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            scanning = which
            if (which == Scanning.TRAINER) vm.scanTrainer() else vm.scanHrm()
        } else {
            pending = which
            permissions.launch(needed.toTypedArray())
        }
    }

    DisposableEffect(Unit) { onDispose { vm.stopScans() } }

    MetalSurface {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 28.dp),
        ) {
            StitchedHeader("Rider", trailing = "%.2f W/kg".format(rider.wattsPerKg))
            Spacer(Modifier.height(6.dp))
            Stepper(
                label = "Functional threshold power",
                value = rider.ftpWatts,
                onChange = { v -> vm.updateRider { it.copy(ftpWatts = v) } },
                range = 60..500,
                step = 5,
                suffix = " W",
            )
            Text(
                "Every target is a share of this. If the recoveries feel like work, " +
                    "take five watts off; if the sprints feel easy, add five.",
                style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
            )
            Stepper(
                label = "Weight",
                value = rider.weightKg.roundToInt(),
                onChange = { v -> vm.updateRider { it.copy(weightKg = v.toDouble()) } },
                range = 35..220,
                suffix = " kg",
            )
            Stepper(
                label = "Age",
                value = rider.ageYears,
                onChange = { v -> vm.updateRider { it.copy(ageYears = v) } },
                range = 12..95,
            )
            Stepper(
                label = "Resting heart rate",
                value = rider.restingHr,
                onChange = { v -> vm.updateRider { it.copy(restingHr = v) } },
                range = 30..110,
                suffix = " bpm",
            )
            Stepper(
                label = "Measured max heart rate",
                value = rider.measuredMaxHr,
                onChange = { v -> vm.updateRider { it.copy(measuredMaxHr = v) } },
                range = 0..230,
                step = 1,
                suffix = if (rider.measuredMaxHr == 0) "  (estimate)" else " bpm",
            )
            ValueRow("Max heart rate in use", "${rider.maxHr} bpm")
            if (!rider.isPlausible()) {
                Text(
                    "One of these is outside the range the maths trusts.",
                    style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.LcdRed),
                )
            }

            Spacer(Modifier.height(16.dp))
            StitchedHeader("Trainer", trailing = trainerLabel(trainerState))
            Spacer(Modifier.height(6.dp))
            ValueRow(
                "Paired",
                devices.trainerName ?: devices.trainerAddress ?: "nothing yet",
                accent = if (devices.trainerAddress != null) Skeuo.LcdGreen else Skeuo.Rivet,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlossyButton(
                    text = if (scanning == Scanning.TRAINER) "Stop" else "Find KICKR",
                    onClick = { beginScan(Scanning.TRAINER) },
                    tint = if (scanning == Scanning.TRAINER) Skeuo.Danger else Skeuo.MetalMid,
                    height = 40.dp,
                    modifier = Modifier.weight(1f),
                )
                if (devices.trainerAddress != null) {
                    GlossyButton(
                        text = "Forget",
                        onClick = { vm.forgetTrainer() },
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (scanning == Scanning.TRAINER) {
                Spacer(Modifier.height(8.dp))
                DeviceList(trainerDevices, "Looking for fitness machines…") { vm.pairTrainer(it); scanning = Scanning.NONE }
            }
            Stepper(
                label = "Sprint brake level",
                value = ride.sprintResistance,
                onChange = { v -> vm.updateRide { it.copy(sprintResistance = v) } },
                range = 20..100,
                step = 5,
                suffix = " %",
            )
            Text(
                "All-out sprints run in resistance mode, not ERG: the trainer holds " +
                    "a gear rather than a wattage, so the power is whatever you can push. " +
                    "Raise this if you spin out, lower it if you bog down.",
                style = Skeuo.engraved.copy(fontSize = 11.sp, color = Skeuo.Rivet),
            )

            Spacer(Modifier.height(16.dp))
            StitchedHeader("Heart rate strap", trailing = hrmLabel(hrmState))
            Spacer(Modifier.height(6.dp))
            ValueRow(
                "Paired",
                devices.hrmName ?: devices.hrmAddress ?: "nothing yet",
                accent = if (devices.hrmAddress != null) Skeuo.LcdGreen else Skeuo.Rivet,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlossyButton(
                    text = if (scanning == Scanning.HRM) "Stop" else "Find strap",
                    onClick = { beginScan(Scanning.HRM) },
                    tint = if (scanning == Scanning.HRM) Skeuo.Danger else Skeuo.MetalMid,
                    height = 40.dp,
                    modifier = Modifier.weight(1f),
                )
                if (devices.hrmAddress != null) {
                    GlossyButton(
                        text = "Forget",
                        onClick = { vm.forgetHrm() },
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (scanning == Scanning.HRM) {
                Spacer(Modifier.height(8.dp))
                DeviceList(hrmDevices, "Wet the strap, then wait…") { vm.pairHrm(it); scanning = Scanning.NONE }
            }
            SwitchRow(
                label = "Demo mode",
                checked = devices.simulate,
                onChange = { v -> vm.updateDevices { it.copy(simulate = v) } },
                description = "Invent power and heart rate so the app can be tried off the bike.",
            )

            Spacer(Modifier.height(16.dp))
            StitchedHeader("Coach")
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "Spoken cues",
                checked = coach.voiceEnabled,
                onChange = { v -> vm.updateCoach { it.copy(voiceEnabled = v) } },
                description = "Counts you into every sprint and reads the result back.",
            )
            SwitchRow(
                label = "Announce every step",
                checked = coach.announceEveryStep,
                onChange = { v -> vm.updateCoach { it.copy(announceEveryStep = v) } },
            )
            Stepper(
                label = "Countdown before a hard effort",
                value = coach.countdownSeconds,
                onChange = { v -> vm.updateCoach { it.copy(countdownSeconds = v) } },
                range = 0..10,
                suffix = " s",
            )
            Stepper(
                label = "Speaking rate",
                value = (coach.speechRate * 100).roundToInt(),
                onChange = { v -> vm.updateCoach { it.copy(speechRate = v / 100f) } },
                range = 70..160,
                step = 5,
                suffix = " %",
            )

            Spacer(Modifier.height(16.dp))
            StitchedHeader("Display")
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "Keep the screen on",
                checked = ride.keepScreenOn,
                onChange = { v -> vm.updateRide { it.copy(keepScreenOn = v) } },
                description = "Only while a ride is running.",
            )
            Stepper(
                label = "Effort graph window",
                value = ride.graphWindowSec,
                onChange = { v -> vm.updateRide { it.copy(graphWindowSec = v) } },
                range = 60..900,
                step = 60,
                suffix = " s",
            )
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<ScannedDevice>,
    empty: String,
    onPick: (ScannedDevice) -> Unit,
) {
    InsetPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            if (devices.isEmpty()) {
                Text(empty, style = Skeuo.lcd(Skeuo.LcdDim, 13))
            }
            devices.forEach { device ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name ?: "(no name)", style = Skeuo.lcd(Skeuo.LcdGreen, 15), maxLines = 1)
                        Text(
                            "${device.address}   ${device.rssi} dBm",
                            style = Skeuo.lcd(Skeuo.LcdDim, 11),
                            maxLines = 1,
                        )
                    }
                    GlossyButton(
                        text = "Pair",
                        onClick = { onPick(device) },
                        height = 34.dp,
                        corner = 8.dp,
                        tint = Skeuo.Go,
                    )
                }
            }
        }
    }
}

private fun trainerLabel(state: TrainerState): String = when (state) {
    is TrainerState.Idle -> "idle"
    is TrainerState.Scanning -> "scanning"
    is TrainerState.Connecting -> "connecting"
    is TrainerState.Connected -> if (state.hasControl) "in control" else "connected"
    is TrainerState.Disconnected -> state.reason ?: "disconnected"
    is TrainerState.Failed -> state.message
}

private fun hrmLabel(state: HrmState): String = when (state) {
    is HrmState.Idle -> "idle"
    is HrmState.Scanning -> "scanning"
    is HrmState.Connecting -> "connecting"
    is HrmState.Connected -> state.batteryPercent?.let { "connected · $it%" } ?: "connected"
    is HrmState.Simulated -> "demo"
    is HrmState.Disconnected -> state.reason ?: "disconnected"
    is HrmState.Failed -> state.message
}
