package com.telecomprofi.kickrhiit

import com.telecomprofi.kickrhiit.core.Loudness
import com.telecomprofi.kickrhiit.core.LoudnessNormaliser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Levelling exists so that no track in the queue startles you in headphones.
 * The measurement is EBU R128; the calibration point below is the one the
 * standard fixes, so it is the one worth pinning.
 */
class LoudnessTest {

    private fun sine(seconds: Double, hz: Double, amplitude: Double, rate: Int = 48_000) =
        FloatArray((seconds * rate).toInt()) {
            (amplitude * sin(2 * PI * hz * it / rate)).toFloat()
        }

    @Test
    fun `a full-scale kilohertz tone measures minus three LUFS`() {
        // BS.1770's own reference: a 0 dBFS 1 kHz sine is −3.01 LUFS.
        val lufs = Loudness.integratedLufs(sine(3.0, 1000.0, 1.0), 48_000)
        assertTrue(lufs != null)
        assertEquals(-3.01, lufs!!, 0.1)
    }

    @Test
    fun `halving the amplitude costs six decibels`() {
        val loud = Loudness.integratedLufs(sine(3.0, 1000.0, 1.0), 48_000)!!
        val quiet = Loudness.integratedLufs(sine(3.0, 1000.0, 0.5), 48_000)!!
        assertEquals(6.02, loud - quiet, 0.05)
    }

    @Test
    fun `silence and scraps of audio are not measurable`() {
        assertNull(Loudness.integratedLufs(FloatArray(48_000), 48_000))
        assertNull(Loudness.integratedLufs(sine(0.1, 1000.0, 1.0), 48_000))
        assertNull(Loudness.integratedLufs(sine(1.0, 1000.0, 1.0), 0))
    }

    @Test
    fun `the reference is the middle of the playlist, not an extreme`() {
        // One outlier must not drag the whole queue down: that was the bug that
        // made every track sound quiet.
        val typical = listOf(-9.0, -9.5, -10.0, -10.2, -11.0)
        val withOutlier = typical + (-30.0)
        val a = LoudnessNormaliser.reference(typical)!!
        val b = LoudnessNormaliser.reference(withOutlier)!!
        assertTrue("the outlier moved the reference by ${a - b} dB", kotlin.math.abs(a - b) < 1.0)
    }

    @Test
    fun `unmeasured tracks are ignored, and an empty list has no reference`() {
        assertNull(LoudnessNormaliser.reference(emptyList()))
        assertNull(LoudnessNormaliser.reference(listOf(0.0, 0.0)))
        assertEquals(-10.0, LoudnessNormaliser.reference(listOf(0.0, -10.0, 0.0))!!, 1e-9)
    }

    @Test
    fun `gain is clamped both ways`() {
        val reference = -10.0
        assertEquals(0.0, LoudnessNormaliser.gainDb(-10.0, reference), 1e-9)
        assertEquals(-8.0, LoudnessNormaliser.gainDb(-2.0, reference), 1e-9)   // loud track, cut
        assertEquals(6.0, LoudnessNormaliser.gainDb(-25.0, reference), 1e-9)   // quiet track, lifted
        assertEquals(0.0, LoudnessNormaliser.gainDb(0.0, reference), 1e-9)     // unmeasured
        assertEquals(0.0, LoudnessNormaliser.gainDb(-25.0, null), 1e-9)        // nothing to match
    }

    @Test
    fun `a cut is volume, a boost is the effect`() {
        assertEquals(1.0f, LoudnessNormaliser.attenuationFactor(3.0), 1e-6f)
        assertEquals(0.5012f, LoudnessNormaliser.attenuationFactor(-6.0), 1e-3f)
        assertEquals(0, LoudnessNormaliser.boostMillibels(-6.0))
        assertEquals(300, LoudnessNormaliser.boostMillibels(3.0))
        assertEquals(1200, LoudnessNormaliser.boostMillibels(40.0))
    }
}
