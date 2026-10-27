package com.telecomprofi.kickrhiit

import com.telecomprofi.kickrhiit.core.RiderProfile
import com.telecomprofi.kickrhiit.core.StepKind
import com.telecomprofi.kickrhiit.core.StepMode
import com.telecomprofi.kickrhiit.core.Workouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The programmes are data, so these are the tests that data has the shape the
 * blurb on the card promises. A workout that quietly ran forty-three minutes
 * while its subtitle said forty-five would never be noticed on the bike.
 */
class WorkoutTest {

    @Test
    fun `the six by thirty session is forty minutes`() {
        val w = Workouts.sit6x30()
        assertEquals(40 * 60, w.totalSec)
        assertEquals(40, w.minutes)
        assertEquals(6, w.sprintCount)
    }

    @Test
    fun `the quick session is twenty-four minutes with four sprints`() {
        val w = Workouts.quickSit4x30()
        assertEquals(24 * 60, w.totalSec)
        assertEquals(4, w.sprintCount)
    }

    @Test
    fun `the pyramid is forty-five minutes and never leaves ERG`() {
        val w = Workouts.pyramid
        assertEquals(45 * 60, w.totalSec)
        assertEquals(0, w.sprintCount)
        assertTrue(w.steps.all { it.mode == StepMode.ERG })
    }

    @Test
    fun `every sprint is thirty seconds of brake, not of watts`() {
        val w = Workouts.sit6x30()
        val sprints = w.steps.filter { it.isSprint }
        assertTrue(sprints.isNotEmpty())
        sprints.forEach {
            assertEquals(30, it.seconds)
            assertEquals(StepMode.RESISTANCE, it.mode)
            assertTrue(it.resistance > 0)
        }
    }

    @Test
    fun `the brake level from settings reaches the sprints`() {
        val w = Workouts.byId("sit-6x30", sprintResistance = 82)
        assertTrue(w.steps.filter { it.isSprint }.all { it.resistance == 82 })
    }

    @Test
    fun `recovery after every sprint is four minutes`() {
        val steps = Workouts.sit6x30().steps
        steps.forEachIndexed { i, step ->
            if (step.isSprint) {
                val next = steps.getOrNull(i + 1)
                assertNotNull("a sprint must be followed by something", next)
                assertEquals(StepKind.RECOVERY, next!!.kind)
                assertEquals(4 * 60, next.seconds)
            }
        }
    }

    @Test
    fun `an unknown id falls back rather than throwing`() {
        assertEquals("sit-6x30", Workouts.byId("no-such-workout").id)
    }

    @Test
    fun `step lookup lands on the right side of a boundary`() {
        val w = Workouts.sit6x30()
        val firstSprintIndex = w.steps.indexOfFirst { it.isSprint }
        val start = w.startTimes[firstSprintIndex]

        assertEquals(firstSprintIndex - 1, w.indexAt(start - 1))
        assertEquals(firstSprintIndex, w.indexAt(start))
        assertEquals(firstSprintIndex, w.indexAt(start + 29))
        assertEquals(firstSprintIndex + 1, w.indexAt(start + 30))

        assertEquals(0, w.elapsedInStep(start))
        assertEquals(30, w.remainingInStep(start))
        assertEquals(1, w.remainingInStep(start + 29))
    }

    @Test
    fun `past the end there is no step left`() {
        val w = Workouts.sit6x30()
        assertEquals(-1, w.indexAt(w.totalSec))
        assertNull(w.stepAt(w.totalSec))
        assertTrue(w.isFinished(w.totalSec))
        assertNull(w.nextStep(w.totalSec))
    }

    @Test
    fun `a ramped step starts and ends where it says`() {
        val ramp = Workouts.pyramid.steps.first { it.toPctFtp != null }
        assertEquals(ramp.pctFtp, ramp.pctAt(0), 1e-9)
        assertEquals(ramp.toPctFtp!!, ramp.pctAt(ramp.seconds - 1), 1e-9)
        // and it is monotone in between, which is the whole idea of a ramp
        val mid = ramp.pctAt(ramp.seconds / 2)
        assertTrue(mid > minOf(ramp.pctFtp, ramp.toPctFtp!!))
        assertTrue(mid < maxOf(ramp.pctFtp, ramp.toPctFtp!!))
    }

    @Test
    fun `a flat step ignores the clock`() {
        val flat = Workouts.pyramid.steps.first { it.toPctFtp == null }
        assertEquals(flat.pctFtp, flat.pctAt(0), 1e-9)
        assertEquals(flat.pctFtp, flat.pctAt(flat.seconds - 1), 1e-9)
    }

    @Test
    fun `planned work scales with the rider`() {
        val small = Workouts.sit6x30().plannedKj(RiderProfile(ftpWatts = 150))
        val big = Workouts.sit6x30().plannedKj(RiderProfile(ftpWatts = 300))
        assertTrue(big > small)
        // Two hundred watts for forty minutes is around 480 kJ; sprints push it up.
        assertTrue(small in 150..800)
    }

    @Test
    fun `every step start time is the sum of what came before`() {
        Workouts.all().forEach { w ->
            var t = 0
            w.steps.forEachIndexed { i, step ->
                assertEquals(t, w.startTimes[i])
                t += step.seconds
            }
            assertEquals(w.totalSec, t)
            assertTrue(w.steps.all { it.seconds > 0 })
            assertTrue(w.steps.all { it.label.isNotBlank() })
        }
    }

    @Test
    fun `the picker offers three distinct programmes`() {
        val all = Workouts.all()
        assertEquals(3, all.size)
        assertEquals(3, all.map { it.id }.toSet().size)
    }
}
