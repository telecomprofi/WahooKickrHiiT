package com.telecomprofi.kickrhiit.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** One second of the ride, kept for the graph and the summary. */
data class TracePoint(
    val tSec: Int,
    val targetW: Int,
    val powerW: Int,
    val cadenceRpm: Int,
    val hr: Int,
)

/**
 * The ride's history, at one point a second, in a fixed-size ring.
 *
 * A forty-five minute session is 2,700 points; keeping them in a pre-allocated
 * array rather than a growing list means the graph never allocates mid-sprint,
 * which is exactly when a stutter would be noticed.
 */
class EffortTrace(val capacity: Int = 4096) {

    private val buffer = arrayOfNulls<TracePoint>(capacity)
    private var head = 0
    var size: Int = 0
        private set

    fun add(point: TracePoint) {
        buffer[head] = point
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun clear() {
        head = 0
        size = 0
    }

    /** Oldest first. */
    fun points(): List<TracePoint> {
        if (size == 0) return emptyList()
        val start = (head - size + capacity) % capacity
        return List(size) { buffer[(start + it) % capacity]!! }
    }

    /** The last [seconds] of the trace, oldest first — what the graph draws. */
    fun window(seconds: Int): List<TracePoint> {
        val all = points()
        if (all.isEmpty()) return all
        val cutoff = all.last().tSec - seconds
        return all.dropWhile { it.tSec < cutoff }
    }
}

/**
 * The numbers that describe an interval session after the fact.
 *
 * Normalised power is the one that matters: a session of sprints and coasting
 * averages out to something modest, but it does not *feel* modest, and it does
 * not stress the body like a steady ride at that average. Raising each 30-second
 * rolling average to the fourth power before averaging weights the hard bits the
 * way the body seems to, which is why every training platform reports it.
 */
class RideMetrics {

    private var seconds = 0
    private var powerSum = 0L
    private var powerSamples = 0
    private var cadenceSum = 0L
    private var cadenceSamples = 0
    private var hrSum = 0L
    private var hrSamples = 0

    var maxPowerW: Int = 0; private set
    var maxHr: Int = 0; private set
    var workJoules: Double = 0.0; private set

    /** Rolling 30-second window for normalised power and the best-sprint figure. */
    private val window = ArrayDeque<Int>()
    private var windowSum = 0L
    private val fourthPowers = ArrayList<Double>(4096)

    var bestThirtySecondW: Int = 0; private set

    fun tick(dtSec: Int, powerW: Int?, cadence: Int?, hr: Int?) {
        if (dtSec <= 0) return
        seconds += dtSec
        val p = powerW ?: 0
        if (powerW != null) {
            powerSum += p.toLong() * dtSec
            powerSamples += dtSec
            workJoules += p.toDouble() * dtSec
            if (p > maxPowerW) maxPowerW = p
        }
        cadence?.let { if (it > 0) { cadenceSum += it.toLong() * dtSec; cadenceSamples += dtSec } }
        hr?.let {
            if (it > 30) {
                hrSum += it.toLong() * dtSec
                hrSamples += dtSec
                if (it > maxHr) maxHr = it
            }
        }

        repeat(dtSec) {
            window.addLast(p)
            windowSum += p
            while (window.size > 30) windowSum -= window.removeFirst()
            if (window.size == 30) {
                val rolling = windowSum.toDouble() / 30.0
                fourthPowers += rolling.pow(4)
                if (rolling.roundToInt() > bestThirtySecondW) bestThirtySecondW = rolling.roundToInt()
            }
        }
    }

    val elapsedSec: Int get() = seconds
    val avgPowerW: Int get() = if (powerSamples == 0) 0 else (powerSum / powerSamples).toInt()
    val avgCadence: Int get() = if (cadenceSamples == 0) 0 else (cadenceSum / cadenceSamples).toInt()
    val avgHr: Int get() = if (hrSamples == 0) 0 else (hrSum / hrSamples).toInt()
    val workKj: Double get() = workJoules / 1000.0

    /**
     * Kilojoules of mechanical work happen to be within a few per cent of the
     * kilocalories the body spends producing them, because human gross
     * efficiency on a bike is close to 23% and there are 4.184 kJ in a kcal.
     * The two very nearly cancel, which is why power meters quote them
     * interchangeably.
     */
    val kcal: Int get() = (workKj / 4.184 / 0.225).roundToInt()

    val normalisedPowerW: Int
        get() {
            if (fourthPowers.isEmpty()) return avgPowerW
            return fourthPowers.average().pow(0.25).roundToInt()
        }

    fun intensityFactor(ftp: Int): Double =
        if (ftp <= 0) 0.0 else normalisedPowerW.toDouble() / ftp

    /** Training stress: an hour at threshold is 100. */
    fun stressScore(ftp: Int): Int {
        if (ftp <= 0) return 0
        val ifactor = intensityFactor(ftp)
        return ((seconds * normalisedPowerW * ifactor) / (ftp * 36.0)).roundToInt()
    }
}
