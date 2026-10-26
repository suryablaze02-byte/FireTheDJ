package com.autodj.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class HarmonyTest {
    private val sr = 11025.0
    private val barSec = 2.0

    private fun midiHz(note: Int) = 440.0 * 2.0.pow((note - 69) / 12.0)

    /** Each bar holds one chord (MIDI notes) as sustained sines. */
    private fun chords(bars: List<List<Int>>): FloatArray {
        val n = (bars.size * barSec * sr).toInt()
        return FloatArray(n) { i ->
            val t = i / sr
            val chord = bars[(t / barSec).toInt().coerceAtMost(bars.size - 1)]
            (chord.sumOf { sin(2 * PI * midiHz(it) * t) } * 0.15).toFloat()
        }
    }

    private val C = listOf(48, 60, 64, 67)
    private val F = listOf(53, 60, 65, 69)
    private val G = listOf(55, 59, 62, 67)
    private val Am = listOf(57, 60, 64, 69)
    private val Dm = listOf(50, 62, 65, 69)
    private val Em = listOf(52, 59, 64, 67)

    @Test
    fun findsTheKeyOfAChordProgression() {
        val song = (0 until 12).flatMap { listOf(C, F, G, C) }
        val h = HarmonyAnalyzer.analyze(chords(song), sr, 0.0, barSec, FloatArray(song.size) { 1f }, 0)
        assertEquals(0, h.keyPc)
        assertFalse(h.minor)
        assertTrue(h.keyConfidence > 0f)
    }

    @Test
    fun findsTheMostRepeatedPhraseAsTheHook() {
        val verse = listOf(Am, Dm, Em, Am, Am, Dm, Em, Am)
        val chorus = listOf(C, G, Am, F, C, G, F, C)
        val bridge = listOf(Dm, Dm, Em, Em, F, F, G, G)
        val song = verse + chorus + verse + chorus + bridge + chorus
        val h = HarmonyAnalyzer.analyze(chords(song), sr, 0.0, barSec, FloatArray(song.size) { 1f }, 0)
        assertEquals(8, h.hookBar)
    }

    @Test
    fun camelotWheel() {
        assertEquals("8B", Camelot.code(0, false)) // C
        assertEquals("8A", Camelot.code(9, true)) // Am
        assertEquals("9B", Camelot.code(7, false)) // G
        assertEquals("5A", Camelot.code(0, true)) // Cm
        assertEquals(0, Camelot.distance(0, false, 0, false))
        assertEquals(1, Camelot.distance(0, false, 9, true)) // relative minor
        assertEquals(1, Camelot.distance(0, false, 7, false)) // a fifth up
        assertEquals(6, Camelot.distance(0, false, 6, false)) // tritone
    }
}
