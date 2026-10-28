package com.autodj.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class LyricsAlignerTest {
    private val fps = 10.0

    /** Lines of four words, sung for [sing] seconds then [rest] seconds of band, from [from] to [to] (lyric seconds). */
    private fun lyrics(from: Double, to: Double, sing: Double = 3.0, rest: Double = 2.0): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        var t = from
        var line = 0
        while (t + sing < to) {
            for (k in 0 until 4) {
                val s = t + k * sing / 4
                words += LyricWord("w$line$k", "w", s, s + sing / 4, k == 0, k == 3, line)
            }
            t += sing + rest
            line++
        }
        return words
    }

    /** A singing curve with the same lines starting [shift] seconds later in our audio, plus noise. */
    private fun curve(words: List<LyricWord>, shift: Double, lengthSec: Double, noise: Double = 0.3): FloatArray {
        val r = Random(7)
        val c = FloatArray((lengthSec * fps).toInt()) { (r.nextDouble() * noise).toFloat() }
        for (w in words) {
            for (f in ((w.startSec + shift) * fps).toInt() until ((w.endSec + shift) * fps).toInt()) {
                if (f in c.indices) c[f] += 1f
            }
        }
        return c
    }

    @Test
    fun aVideoIntroIsFoundToATenthOfASecond() {
        val words = lyrics(20.0, 180.0)
        // Our version has 2.7 s more at the start, and a different ending too: the lengths don't tell.
        val off = LyricsAligner.fineOffset(words, curve(words, 2.7, 210.0), fps, versionGapSec = 9.0)
        assertNotNull(off)
        assertEquals(2.7, off!!, 0.11)
    }

    @Test
    fun theLyricsOwnTimingStaysWhenItFits() {
        val words = lyrics(15.0, 170.0)
        assertEquals(0.0, LyricsAligner.fineOffset(words, curve(words, 0.0, 200.0), fps, versionGapSec = 1.0)!!, 1e-9)
    }

    @Test
    fun anEarlierStartIsFound() {
        val words = lyrics(30.0, 190.0)
        assertEquals(-6.0, LyricsAligner.fineOffset(words, curve(words, -6.0, 200.0), fps)!!, 0.11)
    }

    @Test
    fun noSingingInTheAudioMeansNoTrust() {
        val words = lyrics(15.0, 170.0)
        val r = Random(3)
        val flat = FloatArray(2000) { r.nextFloat() }
        assertNull(LyricsAligner.fineOffset(words, flat, fps))
    }
}
