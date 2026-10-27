package com.telecomprofi.kickrhiit.ride

import com.telecomprofi.kickrhiit.core.PowerZone
import com.telecomprofi.kickrhiit.core.Step
import com.telecomprofi.kickrhiit.core.StepKind
import com.telecomprofi.kickrhiit.core.StepMode
import com.telecomprofi.kickrhiit.core.TracePoint
import com.telecomprofi.kickrhiit.music.NowPlaying

enum class RideStatus { IDLE, RUNNING, PAUSED, FINISHED }

/** Everything the ride screen draws, rebuilt once a second. */
data class RideUiState(
    val status: RideStatus = RideStatus.IDLE,
    val workoutId: String = "",
    val workoutName: String = "",
    val elapsedSec: Int = 0,
    val totalSec: Int = 0,

    val step: Step? = null,
    val stepIndex: Int = 0,
    val stepCount: Int = 0,
    val stepElapsedSec: Int = 0,
    val stepRemainingSec: Int = 0,
    val nextStep: Step? = null,
    /** Counting into a work step: 3, 2, 1 on the screen and in your ear. */
    val countdown: Int = 0,

    val targetW: Int = 0,
    val resistanceTarget: Int = 0,
    val powerW: Int = 0,
    val cadenceRpm: Int = 0,
    val hr: Int = 0,

    val avgPowerW: Int = 0,
    val maxPowerW: Int = 0,
    val normalisedPowerW: Int = 0,
    val workKj: Double = 0.0,
    val kcal: Int = 0,
    val avgCadence: Int = 0,
    val avgHr: Int = 0,
    val bestThirtyW: Int = 0,
    val intensityFactor: Double = 0.0,

    val trace: List<TracePoint> = emptyList(),

    val trainerConnected: Boolean = false,
    val trainerControlling: Boolean = false,
    val trainerNote: String? = null,
    val hrmConnected: Boolean = false,
    val simulated: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val musicStatus: com.telecomprofi.kickrhiit.music.MusicStatus =
        com.telecomprofi.kickrhiit.music.MusicStatus(),
    val musicMuted: Boolean = false,
) {
    val isActive: Boolean get() = status == RideStatus.RUNNING || status == RideStatus.PAUSED
    val remainingSec: Int get() = (totalSec - elapsedSec).coerceAtLeast(0)
    val kind: StepKind get() = step?.kind ?: StepKind.WARMUP
    val isSprint: Boolean get() = step?.isSprint == true
    val isFreeRide: Boolean get() = step?.mode != StepMode.ERG

    /** How far through the current step, 0..1, for the bar under the gauge. */
    val stepProgress: Float
        get() {
            val total = step?.seconds ?: return 0f
            if (total <= 0) return 0f
            return (stepElapsedSec.toFloat() / total).coerceIn(0f, 1f)
        }

    val sessionProgress: Float
        get() = if (totalSec <= 0) 0f else (elapsedSec.toFloat() / totalSec).coerceIn(0f, 1f)

    /**
     * How far off target, as a share of it. Drives the needle's colour: in ERG
     * the trainer holds the number for you, so a persistent gap means the
     * cadence has sagged and the flywheel is winning.
     */
    val powerErrorPct: Double
        get() = if (targetW <= 0) 0.0 else (powerW - targetW).toDouble() / targetW

    fun zone(ftp: Int): PowerZone = PowerZone.forPct(if (ftp <= 0) 0.0 else powerW.toDouble() / ftp)
}
