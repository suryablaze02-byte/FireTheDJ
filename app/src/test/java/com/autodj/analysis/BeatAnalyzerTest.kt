package com.autodj.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class BeatAnalyzerTest {
    private val sr = 11025.0

    /**
     * Synthetic dance track: 16-bar kick-only intro, main section with bass and hats,
     * 16-bar breakdown at bar 64, back in at 80, kick-only outro from bar 96.
     */
    private fun synthTrack(bpm: Double, startSec: Double, bars: Int = 112): FloatArray {
        val beatSec = 60.0 / bpm
        val total = ((startSec + bars * 4 * beatSec + 4) * sr).toInt()
        val out = FloatArray(total)
        val rnd = Random(42)
        fun main(bar: Int) = bar in 16 until 64 || bar in 80 until 96
        fun addKick(t: Double, amp: Double) {
            val s0 = (t * sr).toInt()
            for (i in 0 until (0.12 * sr).toInt()) {
                if (s0 + i >= total) break
                val x = i / sr
                out[s0 + i] += (amp * exp(-x * 30) * sin(2 * PI * (55 + 80 * exp(-x * 40)) * x)).toFloat()
            }
        }
        fun addHat(t: Double) {
            val s0 = (t * sr).toInt()
            for (i in 0 until (0.03 * sr).toInt()) {
                if (s0 + i >= total) break
                out[s0 + i] += (0.15 * exp(-i / sr * 150) * (rnd.nextDouble() * 2 - 1)).toFloat()
            }
        }
        for (b in 0 until bars * 4) {
            val bar = b / 4
            val t = startSec + b * beatSec
            when {
                main(bar) -> { addKick(t, 0.8); addHat(t + beatSec / 2) }
                bar in 64 until 80 -> {}
                else -> addKick(t, 0.3)
            }
        }
        for (i in 0 until total) {
            val sec = i / sr
            val bar = ((sec - startSec) / (4 * beatSec)).toInt()
            if (sec >= startSec && main(bar)) out[i] += (0.3 * sin(2 * PI * 45 * sec)).toFloat()
            if (sec >= startSec && bar in 64 until 80) out[i] += (0.08 * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
        return out
    }

    @Test
    fun findsTempoDownbeatAndStructure() {
        val bpm = 124.0
        val start = 0.37
        val r = BeatAnalyzer.analyze(synthTrack(bpm, start), sr)

        assertEquals(bpm, r.bpm, 0.15)
        val barSec = 240.0 / bpm
        val phaseErr = ((r.downbeatSec - start) % barSec + barSec) % barSec
        assertTrue("downbeat off by $phaseErr s", phaseErr < 0.03 || barSec - phaseErr < 0.03)
        assertEquals(0, r.phraseOffsetBars)
        assertEquals(16, r.introEndBar)
        assertEquals(96, r.outroStartBar)
        assertTrue("drops=${r.dropBars.toList()}", 64 in r.dropBars)
    }

    /** Boom-bap: kick on 1 and the "and" of 3, high snare on 2 and 4, no hats. */
    private fun synthHipHop(bpm: Double, bars: Int = 64): FloatArray {
        val beatSec = 60.0 / bpm
        val total = ((bars * 4 * beatSec + 2) * sr).toInt()
        val out = FloatArray(total)
        val rnd = Random(7)
        fun add(t: Double, len: Double, f: (Double) -> Double) {
            val s0 = (t * sr).toInt()
            for (i in 0 until (len * sr).toInt()) if (s0 + i < total) out[s0 + i] += f(i / sr).toFloat()
        }
        for (bar in 0 until bars) {
            val t = 0.5 + bar * 4 * beatSec
            for (k in listOf(0.0, 2.5)) add(t + k * beatSec, 0.15) { x -> 0.8 * exp(-x * 25) * sin(2 * PI * (50 + 70 * exp(-x * 40)) * x) }
            for (k in listOf(1.0, 3.0)) {
                // Second difference of noise: a bright snare with almost nothing in the kick band.
                var p1 = 0.0
                var p2 = 0.0
                add(t + k * beatSec, 0.12) { x ->
                    val n = rnd.nextDouble() * 2 - 1
                    val y = n - 2 * p1 + p2
                    p2 = p1; p1 = n
                    0.12 * exp(-x * 30) * y
                }
            }
        }
        return out
    }

    @Test
    fun classifiesSyntheticGenres() {
        val house = BeatAnalyzer.analyze(synthTrack(124.0, 0.37), sr)
        assertEquals(Genres.HOUSE, GenreClassifier.classify(house.features, house.bpm, "").family)

        val hiphop = BeatAnalyzer.analyze(synthHipHop(90.0), sr)
        val r = GenreClassifier.classify(hiphop.features, hiphop.bpm, "")
        assertEquals("bpm ${hiphop.bpm} four ${hiphop.features.fourOnFloor} sync ${hiphop.features.kickSyncopation}", Genres.HIPHOP, r.family)
    }

    /** An onset envelope with an impulse at each of [beatTimes] seconds. */
    private fun impulses(seconds: Double, beatTimes: (Int) -> Double?): DoubleArray {
        val fps = sr / 128
        val env = DoubleArray((seconds * fps).toInt())
        var k = 0
        while (true) {
            val t = beatTimes(k++) ?: break
            val i = (t * fps).roundToInt()
            if (i >= env.size) break
            env[i] = 1.0
        }
        return env
    }

    @Test
    fun mainTempoIsTheOneTheSongMostlyPlaysAt() {
        // Two minutes at 94, then the last 72 s switch to 128 (like "Just Can't Get Enough").
        val r = BeatAnalyzer.analyze(tempoSwitch(94.0, 120.0, 128.0, 72.0), sr)
        assertEquals(94.0, r.bpm, 1.0)
        assertTrue(r.tempoMap.takeLast(3).filter { it > 0f }.all { kotlin.math.abs(it - 128) < 3 })
    }

    /** [first] BPM for [firstSec], then [second] BPM for [secondSec]: kicks on beats, hats off-beat. */
    private fun tempoSwitch(first: Double, firstSec: Double, second: Double, secondSec: Double): FloatArray {
        val out = FloatArray(((firstSec + secondSec) * sr).toInt())
        val rnd = Random(5)
        fun hit(t: Double, kick: Boolean) {
            val s0 = (t * sr).toInt()
            val len = ((if (kick) 0.12 else 0.03) * sr).toInt()
            var p1 = 0.0
            var p2 = 0.0
            for (i in 0 until len) {
                if (s0 + i >= out.size) break
                val x = i / sr
                out[s0 + i] += if (kick) {
                    (0.8 * exp(-x * 30) * sin(2 * PI * (55 + 80 * exp(-x * 40)) * x)).toFloat()
                } else {
                    // High-passed noise, like a real hi-hat: nothing down in the kick band.
                    val n = rnd.nextDouble() * 2 - 1
                    val y = n - 2 * p1 + p2
                    p2 = p1; p1 = n
                    (0.08 * exp(-x * 150) * y).toFloat()
                }
            }
        }
        var t = 0.2
        while (t < out.size / sr) {
            val beat = 60.0 / if (t < firstSec) first else second
            hit(t, true)
            hit(t + beat / 2, false)
            t += beat
        }
        return out
    }

    @Test
    fun tempoMapFollowsATempoChange() {
        // 72 s at 96 BPM, then 120 s at 128 BPM: kicks on every beat, hats on the off-beats.
        val out = FloatArray(((72 + 120) * sr).toInt())
        val rnd = Random(5)
        fun hit(t: Double, kick: Boolean) {
            val s0 = (t * sr).toInt()
            val len = ((if (kick) 0.12 else 0.03) * sr).toInt()
            for (i in 0 until len) {
                if (s0 + i >= out.size) break
                val x = i / sr
                out[s0 + i] += if (kick) (0.8 * exp(-x * 30) * sin(2 * PI * (55 + 80 * exp(-x * 40)) * x)).toFloat()
                else (0.15 * exp(-x * 150) * (rnd.nextDouble() * 2 - 1)).toFloat()
            }
        }
        var t = 0.2
        while (t < out.size / sr) {
            val beat = if (t < 72) 60.0 / 96 else 60.0 / 128
            hit(t, true)
            hit(t + beat / 2, false)
            t += beat
        }
        val r = BeatAnalyzer.analyze(out, sr)
        val map = r.tempoMap
        // Early windows read the slow section, late ones the fast one.
        val early = map.take(3).filter { it > 0f }
        val late = map.takeLast(4).filter { it > 0f }
        assertTrue("early $early", early.isNotEmpty() && early.all { kotlin.math.abs(it - 96) < 3 })
        assertTrue("late $late", late.isNotEmpty() && late.all { kotlin.math.abs(it - 128) < 3 })
    }

    @Test
    fun doublesATempoReadAnOctaveLow() {
        val fps = sr / 128
        // Kicks every beat at 150 BPM, but the estimate said 75.
        val fast = impulses(120.0) { 0.3 + it * 0.4 }
        assertEquals(150.0, BeatAnalyzer.correctOctave(75.0, fast, fast, fps), 0.5)
        // A real 75 BPM four-on-the-floor stays.
        val slow = impulses(120.0) { 0.3 + it * 0.8 }
        assertEquals(75.0, BeatAnalyzer.correctOctave(75.0, slow, slow, fps), 0.0)
        // Boom-bap at 90: kicks on 1 and the "and" of 3 only. Stays at 90.
        val beat = 60.0 / 90
        val hiphop = impulses(120.0) { k -> 0.3 + (k / 2) * 4 * beat + if (k % 2 == 0) 0.0 else 2.5 * beat }
        assertEquals(90.0, BeatAnalyzer.correctOctave(90.0, hiphop, hiphop, fps), 0.0)
    }

    @Test
    fun handlesOtherTempos() {
        for (bpm in listOf(98.0, 128.0, 140.0, 150.0, 174.0)) {
            val r = BeatAnalyzer.analyze(synthTrack(bpm, 1.1), sr)
            assertEquals("tempo for $bpm", bpm, r.bpm, 0.2)
        }
    }
}
