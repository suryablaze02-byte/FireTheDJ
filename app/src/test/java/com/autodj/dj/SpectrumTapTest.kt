package com.autodj.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

class SpectrumTapTest {
    private val sr = 44100

    /** Stereo tone: [hz] for [seconds], silence after [toneUntil]. */
    private fun tone(hz: Double, seconds: Double, toneUntil: Double = seconds) = FloatArray((seconds * sr).toInt() * 2) { i ->
        val t = (i / 2).toDouble() / sr
        if (t < toneUntil) (0.5 * sin(2 * PI * hz * t)).toFloat() else 0f
    }

    @Test
    fun aToneLightsItsOwnBandAtTheRightTime() {
        val tap = SpectrumTap()
        tap.configure(sr)
        val audio = tone(1000.0, 2.0, toneUntil = 1.0)
        // Fed in uneven chunks, the way the audio pipeline delivers it, starting at track time 30 s.
        var frame = 0
        while (frame < audio.size / 2) {
            val n = minOf(777, audio.size / 2 - frame)
            tap.feed(audio.copyOfRange(frame * 2, (frame + n) * 2), n, 2, 30.0 + frame.toDouble() / sr)
            frame += n
        }
        val bands = FloatArray(SpectrumTap.BANDS)
        val level = tap.frameAt(30.5, bands)
        assertTrue(level > 0.3f)
        val loudest = bands.indices.maxBy { bands[it] }
        val expected = (ln(1000.0 / 40.0) / ln(16000.0 / 40.0) * SpectrumTap.BANDS).toInt()
        assertTrue("loudest band $loudest, expected ~$expected", loudest in expected - 1..expected + 1)
        assertTrue(bands[loudest] > 0.8f)
        assertTrue(bands[2] < 0.3f) // far below the tone: quiet
        // A second later the tone had stopped: the frame for that time is silent.
        assertEquals(0f, tap.frameAt(31.5, bands), 1e-3f)
        // Nothing was captured near 40 s.
        assertEquals(-1f, tap.frameAt(40.0, bands), 0f)
    }
}
