package com.telecomprofi.kickrhiit

import com.telecomprofi.kickrhiit.core.EffortTrace
import com.telecomprofi.kickrhiit.core.PowerZone
import com.telecomprofi.kickrhiit.core.RideMetrics
import com.telecomprofi.kickrhiit.core.RiderProfile
import com.telecomprofi.kickrhiit.core.TracePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerMathTest {

    private fun ride(seconds: Int, power: (Int) -> Int): RideMetrics {
        val m = RideMetrics()
        for (t in 0 until seconds) m.tick(1, power(t), 90, 140)
        return m
    }

    @Test
    fun `a steady hour at threshold is one hundred stress points`() {
        val m = ride(3600) { 250 }
        assertEquals(250, m.avgPowerW)
        assertEquals(250, m.normalisedPowerW)
        assertEquals(1.0, m.intensityFactor(250), 1e-6)
        assertEquals(100, m.stressScore(250))
    }

    @Test
    fun `normalised power exceeds the average when the ride is spiky`() {
        // Same average as a steady 200 W, but delivered in one-minute blocks of
        // 400 and 0 — which is what a sprint session actually feels like.
        val spiky = ride(3600) { if ((it / 60) % 2 == 0) 400 else 0 }
        assertEquals(200, spiky.avgPowerW)
        assertTrue(
            "spiky NP ${spiky.normalisedPowerW} should be well above the 200 W average",
            spiky.normalisedPowerW > 300,
        )
    }

    @Test
    fun `normalised power equals average power when nothing varies`() {
        val steady = ride(600) { 180 }
        assertEquals(steady.avgPowerW, steady.normalisedPowerW)
    }

    @Test
    fun `with less than thirty seconds there is nothing to normalise`() {
        val short = ride(10) { 300 }
        assertEquals(300, short.avgPowerW)
        assertEquals(300, short.normalisedPowerW)
        assertEquals(0, short.bestThirtySecondW)
    }

    @Test
    fun `the best thirty seconds finds the sprint, not the average`() {
        // Ten minutes easy with a single all-out half minute buried in it.
        val m = ride(600) { if (it in 300 until 330) 900 else 120 }
        assertEquals(900, m.bestThirtySecondW)
        assertEquals(900, m.maxPowerW)
        assertTrue(m.avgPowerW < 200)
    }

    @Test
    fun `work and calories follow from the watts`() {
        val m = ride(600) { 200 }
        assertEquals(120.0, m.workKj, 0.001)          // 200 W × 600 s = 120 kJ
        assertEquals(127, m.kcal)                      // 120 / 4.184 / 0.225
    }

    @Test
    fun `heart rate below thirty is a dropout, not a reading`() {
        val m = RideMetrics()
        repeat(60) { m.tick(1, 200, 90, 0) }
        assertEquals(0, m.avgHr)
        assertEquals(0, m.maxHr)
        repeat(60) { m.tick(1, 200, 90, 150) }
        assertEquals(150, m.avgHr)
    }

    @Test
    fun `a zero-length tick changes nothing`() {
        val m = RideMetrics()
        m.tick(0, 500, 120, 190)
        assertEquals(0, m.elapsedSec)
        assertEquals(0, m.maxPowerW)
    }

    @Test
    fun `stress and intensity are zero without an FTP`() {
        val m = ride(600) { 200 }
        assertEquals(0.0, m.intensityFactor(0), 1e-9)
        assertEquals(0, m.stressScore(0))
    }

    @Test
    fun `the trace keeps the newest points when it wraps`() {
        val trace = EffortTrace(capacity = 100)
        for (t in 0 until 250) trace.add(TracePoint(t, 200, 210, 90, 140))
        assertEquals(100, trace.size)
        val points = trace.points()
        assertEquals(150, points.first().tSec)
        assertEquals(249, points.last().tSec)
    }

    @Test
    fun `the graph window is the last few seconds only`() {
        val trace = EffortTrace(capacity = 4096)
        for (t in 0 until 1000) trace.add(TracePoint(t, 200, 210, 90, 140))
        val window = trace.window(60)
        assertEquals(61, window.size)
        assertEquals(939, window.first().tSec)
        assertEquals(999, window.last().tSec)
    }

    @Test
    fun `an empty trace has an empty window`() {
        val trace = EffortTrace(capacity = 16)
        assertTrue(trace.window(300).isEmpty())
        trace.add(TracePoint(0, 100, 100, 80, 120))
        trace.clear()
        assertEquals(0, trace.size)
        assertTrue(trace.points().isEmpty())
    }

    @Test
    fun `watts come from the share of FTP`() {
        val rider = RiderProfile(ftpWatts = 240, weightKg = 96.0)
        assertEquals(240, rider.watts(1.0))
        assertEquals(120, rider.watts(0.5))
        assertEquals(2.5, rider.wattsPerKg, 1e-9)
    }

    @Test
    fun `max heart rate is estimated until it is measured`() {
        val estimated = RiderProfile(ageYears = 52, restingHr = 60)
        assertEquals(172, estimated.maxHr)                       // 208 − 0.7 × 52
        val measured = estimated.copy(measuredMaxHr = 181)
        assertEquals(181, measured.maxHr)
        // A figure that cannot be real is ignored rather than believed.
        assertEquals(172, estimated.copy(measuredMaxHr = 65).maxHr)
    }

    @Test
    fun `power zones cover every share without a gap`() {
        assertEquals(PowerZone.RECOVERY, PowerZone.forPct(0.0))
        assertEquals(PowerZone.ENDURANCE, PowerZone.forPct(0.60))
        assertEquals(PowerZone.THRESHOLD, PowerZone.forPct(1.00))
        assertEquals(PowerZone.VO2MAX, PowerZone.forPct(1.10))
        assertEquals(PowerZone.NEUROMUSCULAR, PowerZone.forPct(4.0))
        val rider = RiderProfile(ftpWatts = 200)
        assertEquals(PowerZone.NEUROMUSCULAR, PowerZone.forWatts(1000, rider))
        assertEquals(PowerZone.RECOVERY, PowerZone.forWatts(0, rider))
    }
}
