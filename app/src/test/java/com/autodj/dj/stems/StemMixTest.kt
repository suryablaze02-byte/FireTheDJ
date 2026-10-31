package com.autodj.dj.stems

import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Ramp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class StemMixTest {
    private fun plan(bass: Lane = Lane(emptyList(), 1f), events: List<FxEvent> = emptyList()) =
        FxPlan(0.5, 0.0, Lane(emptyList(), 1f), bass, events)

    @Test
    fun noMovesPlaysEveryStemInFull() {
        val g = StemMix.gains(plan(), 10.0)
        assertEquals(listOf(1f, 1f, 1f, 1f), g.toList())
    }

    @Test
    fun aBassSwapTurnsOnlyTheBassStemDown() {
        val p = plan(bass = Lane(listOf(Ramp(10.0, 14.0, 1f, 0f)), 1f))
        val mid = StemMix.gains(p, 12.0)
        assertEquals(1f, mid[0], 0f) // the kick stays
        assertEquals(0.5f, mid[1], 0.01f)
        assertEquals(0f, StemMix.gains(p, 15.0)[1], 0f)
    }

    @Test
    fun vocalCutAndIsolateWorkOnTheRealParts() {
        val cut = plan(events = listOf(FxEvent(FxType.VOCAL_CUT, 0.0, 10.0, 1f, 1f)))
        assertEquals(listOf(1f, 1f, 1f, 0f), StemMix.gains(cut, 5.0).toList())
        // An a cappella: everything but the vocals out, the bass too.
        val iso = plan(events = listOf(FxEvent(FxType.VOCAL_ISOLATE, 0.0, 10.0, 1f, 1f)))
        assertEquals(listOf(0f, 0f, 0f, 1f), StemMix.gains(iso, 5.0).toList())
    }

    @Test
    fun aWordLoopFromStemsSingsAlone() {
        // The marker a word loop carries: everything but the vocals out while it loops.
        val loop = plan(events = listOf(FxEvent(FxType.LOOP_ROLL, 0.0, 4.0, p1 = 0.5f), FxEvent(FxType.STEM_ACAPELLA, 0.0, 4.0)))
        assertEquals(listOf(0f, 0f, 0f, 1f), StemMix.gains(loop, 2.0).toList())
        assertEquals(listOf(1f, 1f, 1f, 1f), StemMix.gains(loop, 5.0).toList())
    }

    @Test
    fun alignmentFindsTheExactOffset() {
        val rnd = Random(3)
        val rate = 44100
        val guide = FloatArray(rate * 4) { (sin(it * 0.002) * 0.3 + (rnd.nextFloat() - 0.5f) * 0.4).toFloat() }
        // The deck's audio really starts 137 frames later in the window than its clock says.
        val clockIndex = rate
        val trueLag = 137
        val live = guide.copyOfRange(clockIndex + trueLag, clockIndex + trueLag + (0.6 * rate).toInt())
        assertEquals(trueLag, StemMix.align(live, guide, clockIndex, (0.3 * rate).toInt()))
        assertEquals(-250, StemMix.align(guide.copyOfRange(clockIndex - 250, clockIndex - 250 + 26460), guide, clockIndex, 13230))
    }

    @Test
    fun alignmentRefusesAudioThatIsntInTheWindow() {
        val rnd = Random(5)
        val guide = FloatArray(44100 * 3) { rnd.nextFloat() - 0.5f }
        val other = FloatArray(26460) { rnd.nextFloat() - 0.5f }
        assertNull(StemMix.align(other, guide, 44100, 13230))
    }

    @Test
    fun resamplingThereAndBackKeepsATone() {
        val x = FloatArray(48000) { sin(it * 2 * Math.PI * 440 / 48000).toFloat() * 0.5f }
        val back = Resampler.resample(Resampler.resample(x, 48000, 44100), 44100, 48000)
        var err = 0.0
        for (i in 1000 until 47000) err = maxOf(err, kotlin.math.abs((back[i] - x[i]).toDouble()))
        assert(err < 0.01) { "round trip error $err" }
    }
}
