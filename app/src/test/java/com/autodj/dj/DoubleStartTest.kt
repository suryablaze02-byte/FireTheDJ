package com.autodj.dj

import com.autodj.analysis.AudioFeatures
import com.autodj.analysis.Genres
import com.autodj.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleStartTest {
    private val features = AudioFeatures(0.35, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)

    /** A quiet run-up of [runUp] bars, then the drop and a loud song to bar 96, then a quiet outro. */
    private fun track(
        name: String,
        bpm: Double,
        runUp: Int = 32,
        family: String = Genres.HOUSE,
        keyPc: Int = 0,
        bars: Int = 112,
    ) = TrackInfo(
        uri = name, title = name, artist = "", genre = family, genreFamily = family,
        genreConfidence = 0.9f, genreSource = "tag",
        durationSec = 0.2 + bars * 240.0 / bpm + 1.0, bpm = bpm, downbeatSec = 0.2, phraseOffsetBars = 0,
        barEnergy = FloatArray(bars) { if (it < runUp || it >= 96) 0.4f else 1f },
        introEndBar = 16, outroStartBar = 96, dropBars = intArrayOf(),
        loudnessDb = -10.0, features = features,
        vocalness = FloatArray(bars) { 0.1f },
        keyPc = keyPc, keyMinor = false, keyConfidence = 0.8f, hookBar = -1,
        tempoMap = FloatArray(0),
    )

    @Test
    fun equalRunUpsStartTogetherAndDropTogether() {
        val a = track("a", 124.0)
        val b = track("b", 124.2)
        val p = DoubleStart.pair(a, b)!!
        assertEquals(0, p.aStartBar)
        assertEquals(0, p.bStartBar)
        assertEquals(32, p.aDropBar)
        assertEquals(32, p.bDropBar)

        val t = DoubleStart.plan(p, MixMode.SMOOTH)
        assertEquals(Recipe.DOUBLE_START, t.recipe)
        assertEquals(a.barTime(0), t.exitSec, 1e-9)
        assertEquals(b.barTime(0), t.entrySec, 1e-9)
        // B, at A's tempo, reaches its drop exactly when A reaches its own.
        val aDrop = a.barTime(32)
        val bDrop = t.entrySec + (aDrop - t.exitSec) * b.beatSec / a.beatSec
        assertEquals(b.barTime(32), bDrop, 1e-9)
        assertEquals(a.bpm / b.bpm, t.speedForB(a, b, 1.0), 1e-9)
    }

    @Test
    fun oneBassAndOneVoiceAtATime() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        val t = DoubleStart.plan(DoubleStart.pair(a, b)!!, MixMode.SMOOTH)
        val aDrop = a.barTime(32)
        val bDrop = b.barTime(32)
        // Before the drop: A's bass and voice, B at full level with neither.
        assertEquals(1f, t.aPlan.bass.at(aDrop - 2), 1e-6f)
        assertEquals(0f, t.bPlan.bass.at(bDrop - 2), 1e-6f)
        assertEquals(1f, t.bPlan.gain.at(bDrop - 2), 1e-6f)
        assertNotNull(t.bPlan.active(com.autodj.dj.fx.FxType.VOCAL_CUT, bDrop - 4))
        assertNull(t.aPlan.active(com.autodj.dj.fx.FxType.VOCAL_CUT, aDrop - 4))
        // After it: the bass and the voice are B's, and B is at full level.
        assertEquals(0f, t.aPlan.bass.at(aDrop + 1), 1e-6f)
        assertEquals(1f, t.bPlan.bass.at(bDrop + 1), 1e-6f)
        assertEquals(1f, t.bPlan.gain.at(bDrop + 1), 1e-6f)
        assertNotNull(t.aPlan.active(com.autodj.dj.fx.FxType.VOCAL_CUT, aDrop + 4))
        assertNull(t.bPlan.active(com.autodj.dj.fx.FxType.VOCAL_CUT, bDrop + 4))
        // A rides 16 bars of the drop, then is gone 8 bars later.
        assertEquals(1f, t.aPlan.gain.at(aDrop + 15 * a.barSec), 1e-6f)
        assertEquals(0f, t.aPlan.gain.at(t.endSec), 1e-6f)
        assertEquals(a.barTime(32 + 24), t.endSec, 1e-9)
    }

    @Test
    fun bothSongsPlayFromTheFirstBeatInEitherMode() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        for (mode in listOf(MixMode.SMOOTH, MixMode.TRICKSTER)) {
            val t = DoubleStart.plan(DoubleStart.pair(a, b)!!, mode)
            // From the first beat both are at full level.
            assertEquals(mode.name, 1f, t.aPlan.gain.at(a.barTime(0) + 0.1), 1e-6f)
            assertEquals(mode.name, 1f, t.bPlan.gain.at(b.barTime(0) + 0.1), 1e-6f)
            assertEquals(mode.name, 1f, t.bPlan.gain.at(b.barTime(1)), 1e-6f)
            // Through the drop's ride both are at full level.
            assertEquals(mode.name, 1f, t.aPlan.gain.at(a.barTime(40)), 1e-6f)
            assertEquals(mode.name, 1f, t.bPlan.gain.at(b.barTime(40)), 1e-6f)
        }
    }

    @Test
    fun tricksterWeavesBOutAndBackInOnTheBar() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        val smooth = DoubleStart.plan(DoubleStart.pair(a, b)!!, MixMode.SMOOTH)
        val trick = DoubleStart.plan(DoubleStart.pair(a, b)!!, MixMode.TRICKSTER)
        val mid = b.barSec / 2
        // Bar 4 of every 4 (bars 3, 7, … counting from 0) B is out, and back on the next downbeat.
        for (k in listOf(3, 7, 11, 23)) {
            assertEquals("bar $k", 0f, trick.bPlan.gain.at(b.barTime(k) + mid), 1e-6f)
            assertEquals("bar ${k + 1}", 1f, trick.bPlan.gain.at(b.barTime(k + 1) + mid), 1e-6f)
            assertEquals("smooth bar $k", 1f, smooth.bPlan.gain.at(b.barTime(k) + mid), 1e-6f)
        }
        // The riser bars before the drop stay in.
        for (k in 28 until 32) assertEquals("bar $k", 1f, trick.bPlan.gain.at(b.barTime(k) + mid), 1e-6f)
    }

    @Test
    fun aShorterRunUpStillStartsTogetherOnAPhrase() {
        val a = track("a", 124.0, runUp = 40)
        val b = track("b", 124.0, runUp = 24)
        val p = DoubleStart.pair(a, b)!!
        // Three phrases is all B has, so A starts three phrases before its own drop.
        assertEquals(40, p.aDropBar)
        assertEquals(24, p.bDropBar)
        assertEquals(16, p.aStartBar)
        assertEquals(0, p.bStartBar)
        assertTrue(a.isPhraseStart(p.aStartBar))
    }

    @Test
    fun pairsThatWouldClashAreLeftOut() {
        val a = track("a", 124.0)
        assertNull("tempo 0.8% apart", DoubleStart.pair(a, track("b", 125.0)))
        assertNull("keys a tritone apart", DoubleStart.pair(a, track("b", 124.0, keyPc = 6)))
        assertNull("another genre", DoubleStart.pair(a, track("b", 124.0, family = Genres.HIPHOP)))
        assertNull("a run-up under two phrases", DoubleStart.pair(a, track("b", 124.0, runUp = 8)))
        assertNull("a song can't pair with itself", DoubleStart.pair(a, a))
        assertNotNull("a neighbour key fits", DoubleStart.pair(a, track("b", 124.0, keyPc = 7)))
    }

    @Test
    fun findPrefersTheSameKey() {
        val a = track("a", 124.0)
        val near = track("near", 124.0, keyPc = 7)
        val same = track("same", 124.0)
        repeat(10) { assertEquals("same", DoubleStart.find(listOf(a), listOf(a, near, same))!!.b.title) }
        assertNull(DoubleStart.find(listOf(a), listOf(a, track("far", 131.0))))
    }

    @Test
    fun aMixBetweenTwoSongsIsNeverADoubleStart() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        val scores = DecisionEngine.scores(a, b, beatmatched = true, speed = 1.0, history = emptyList())
        assertEquals(Double.NEGATIVE_INFINITY, scores[Recipe.DOUBLE_START]!!, 0.0)
        assertTrue(TransitionPlanner.plan(a, 0, 10.0, b).recipe != Recipe.DOUBLE_START)
    }
}
