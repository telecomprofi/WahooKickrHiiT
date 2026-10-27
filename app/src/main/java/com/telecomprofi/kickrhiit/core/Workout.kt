package com.telecomprofi.kickrhiit.core

import kotlin.math.roundToInt

/**
 * How the trainer is driven for one step.
 *
 * The distinction matters more than it looks. **ERG** holds a wattage whatever
 * your cadence, which is exactly right for a threshold block and exactly wrong
 * for a sprint: asked to go all-out you would be held at the target and no
 * harder. So a sprint runs in **RESISTANCE**, where the brake is set to a fixed
 * level and the watts are whatever you can produce against it — which is what
 * "all-out" means, and what the app then measures.
 */
enum class StepMode { ERG, RESISTANCE, FREE }

/** What a step is for, which drives the wording and the colour. */
enum class StepKind { WARMUP, WORK, SPRINT, RECOVERY, COOLDOWN }

/**
 * One block of a workout.
 *
 * Power is stored as a share of FTP rather than watts so a workout survives a
 * change of fitness: raise FTP in Settings and every session rescales.
 */
data class Step(
    val kind: StepKind,
    val seconds: Int,
    val mode: StepMode = StepMode.ERG,
    /** Target as a share of FTP. Ignored unless [mode] is ERG. */
    val pctFtp: Double = 0.5,
    /** Ramp to this share across the step; null holds [pctFtp] flat. */
    val toPctFtp: Double? = null,
    /** Brake setting 0-100 for a RESISTANCE step. */
    val resistance: Int = 0,
    /** Cadence to aim for, and what the music is matched to. */
    val cadenceRpm: Int = 90,
    val label: String,
    /** Spoken as the step begins. */
    val cue: String = "",
) {
    val isSprint: Boolean get() = kind == StepKind.SPRINT

    /** Share of FTP at [second] into the step, following any ramp. */
    fun pctAt(second: Int): Double {
        val to = toPctFtp ?: return pctFtp
        if (seconds <= 1) return to
        val f = (second.toDouble() / (seconds - 1)).coerceIn(0.0, 1.0)
        return pctFtp + (to - pctFtp) * f
    }

    /** Highest share this step reaches, for scaling the profile graph. */
    val peakPct: Double get() = maxOf(pctFtp, toPctFtp ?: pctFtp)

    fun targetWatts(profile: RiderProfile, second: Int = 0): Int = profile.watts(pctAt(second))
}

/** A named programme: a list of steps and the arithmetic over them. */
data class Workout(
    val id: String,
    val name: String,
    val subtitle: String,
    val blurb: String,
    val steps: List<Step>,
) {
    val totalSec: Int = steps.sumOf { it.seconds }
    val minutes: Int get() = (totalSec + 59) / 60
    val sprintCount: Int get() = steps.count { it.isSprint }
    val workCount: Int get() = steps.count { it.kind == StepKind.WORK || it.isSprint }

    /** Wall-clock second each step begins at. */
    val startTimes: List<Int> = buildList {
        var t = 0
        steps.forEach { add(t); t += it.seconds }
    }

    fun indexAt(elapsedSec: Int): Int {
        if (elapsedSec < 0) return 0
        startTimes.forEachIndexed { i, start ->
            if (elapsedSec < start + steps[i].seconds) return i
        }
        return -1
    }

    fun stepAt(elapsedSec: Int): Step? = steps.getOrNull(indexAt(elapsedSec))

    fun nextStep(elapsedSec: Int): Step? {
        val i = indexAt(elapsedSec)
        return if (i < 0) null else steps.getOrNull(i + 1)
    }

    fun elapsedInStep(elapsedSec: Int): Int {
        val i = indexAt(elapsedSec)
        if (i < 0) return 0
        return (elapsedSec - startTimes[i]).coerceAtLeast(0)
    }

    fun remainingInStep(elapsedSec: Int): Int {
        val i = indexAt(elapsedSec)
        if (i < 0) return 0
        return (startTimes[i] + steps[i].seconds - elapsedSec).coerceAtLeast(0)
    }

    fun isFinished(elapsedSec: Int): Boolean = elapsedSec >= totalSec

    /** Highest share of FTP anywhere in the plan, for scaling the graph. */
    val peakPct: Double get() = steps.maxOfOrNull { it.peakPct } ?: 1.0

    /**
     * Planned work in kilojoules, treating a sprint at its nominal share. Rough
     * by nature — the whole point of a sprint is that the number is unknown
     * until you have done it — but enough to compare two workouts.
     */
    fun plannedKj(profile: RiderProfile): Int {
        var joules = 0.0
        steps.forEach { step ->
            val pct = if (step.isSprint) SPRINT_NOMINAL_PCT else (step.pctFtp + (step.toPctFtp ?: step.pctFtp)) / 2
            joules += profile.watts(pct) * step.seconds
        }
        return (joules / 1000).roundToInt()
    }

    companion object {
        /** What a sprint is assumed to be worth when estimating, only. */
        const val SPRINT_NOMINAL_PCT = 2.0
    }
}
