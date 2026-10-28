package com.autodj.lyrics

import com.autodj.analysis.AudioFeatures
import com.autodj.analysis.Genres
import com.autodj.dj.MixMode
import com.autodj.dj.Recipe
import com.autodj.dj.SetPlanner
import com.autodj.dj.TransitionPlanner
import com.autodj.dj.WordPlay
import com.autodj.model.TrackInfo
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WordPlayTest {
    private val features = AudioFeatures(0.35, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)

    /** 120 BPM (2 s bars), downbeat at 0, sung on [sungBars]. */
    private fun track(name: String, bars: Int = 120, sungBars: Set<Int> = emptySet(), bpm: Double = 120.0) = TrackInfo(
        uri = name, title = name, artist = "", genre = Genres.POP, genreFamily = Genres.POP,
        genreConfidence = 0.9f, genreSource = "tag",
        durationSec = bars * 240.0 / bpm + 1.0, bpm = bpm, downbeatSec = 0.0, phraseOffsetBars = 0,
        barEnergy = FloatArray(bars) { if (it < 8 || it >= bars - 8) 0.4f else 1f },
        introEndBar = 8, outroStartBar = bars - 8, dropBars = intArrayOf(),
        loudnessDb = -10.0, features = features,
        vocalness = FloatArray(bars) { if (it in sungBars) 0.9f else 0.1f },
        keyPc = 0, keyMinor = false, keyConfidence = 0f, hookBar = -1, tempoMap = FloatArray(0),
    )

    private fun lrc(vararg lines: Pair<Double, String>) =
        lines.joinToString("\n") { (t, text) -> "[%02d:%05.2f] %s".format((t / 60).toInt(), t % 60, text) }

    @Test
    fun keysForgiveSpelling() {
        assertEquals(WordKey.of("nuvvu"), WordKey.of("nuvu"))
        assertEquals(WordKey.of("Party!"), WordKey.of("party"))
        assertEquals(WordKey.of("thalli"), WordKey.of("talli"))
        assertEquals(WordKey.of("Deewana"), WordKey.of("divana"))
        assertFalse(WordKey.linkable(WordKey.of("the")))
        assertFalse(WordKey.linkable(WordKey.of("ra")))
        assertTrue(WordKey.linkable(WordKey.of("party")))
    }

    @Test
    fun readsLrcWithAndWithoutWordStamps() {
        val words = Lrc.parse(lrc(10.0 to "It's time to party", 14.0 to "", 20.0 to "Kiss Kiss Kissik"))
        assertEquals(listOf("It's", "time", "to", "party", "Kiss", "Kiss", "Kissik"), words.map { it.text })
        assertEquals(10.0, words[0].startSec, 1e-9)
        assertTrue(words[3].lineEnd && words[4].lineStart)
        // About half a second a word.
        assertEquals(11.5, words[3].startSec, 1e-9)
        val stamped = Lrc.parse("[00:05.00] <00:05.00> Hello <00:06.20> world")
        assertEquals(6.2, stamped[1].startSec, 1e-9)
        // Another script goes through the transliterator; words it can't make Latin are dropped.
        assertEquals(listOf("cuttamalle"), Lrc.parse("[00:01.00] చుట్టమల్లె", toLatin = { "cuttamalle" }).map { it.text })
        assertTrue(Lrc.parse("[00:01.00] చుట్టమల్లె").isEmpty())
    }

    @Test
    fun cleansUploadTitles() {
        assertEquals("Time To Party", LyricsSources.cleanTitle("Time To Party Full Video Song | Attarintiki Daredi || Pawan Kalyan"))
        assertEquals("Chuttamalle", LyricsSources.cleanTitle("Chuttamalle Lyrical | Devara"))
        assertEquals("Kissik", LyricsSources.cleanTitle("Kissik (From \"Pushpa 2 The Rule\")"))
        assertEquals("", LyricsSources.cleanArtist("Aditya Music"))
        assertEquals("Sublahshini", LyricsSources.cleanArtist("Sublahshini - Topic"))
    }

    @Test
    fun picksTheTimedVersionClosestInLength() {
        val s = """"trackName":"Song","artistName":"Singer""""
        val results = JSONArray(
            """[{$s,"syncedLyrics":null,"duration":200},{$s,"syncedLyrics":"[00:01.00] a","duration":260},""" +
                """{$s,"syncedLyrics":"[00:02.00] b","duration":205},{$s,"syncedLyrics":"[00:03.00] c","duration":300}]""",
        )
        assertEquals("[00:02.00] b", Lrclib { null }.pick(results, 200.0, "Song", "Singer")?.lrc)
        assertNull(Lrclib { null }.pick(JSONArray("""[{$s,"syncedLyrics":"[00:01.00] a","duration":260}]"""), 200.0, "Song", "Singer"))
    }

    @Test
    fun onlyTakesLyricsOfTheSameSong() {
        // Several songs are called "Finesse": only Bruno Mars's will do, even if another is closer in length.
        val results = JSONArray(
            """[{"trackName":"Finesse","artistName":"Someone Else","syncedLyrics":"[00:01.00] wrong","duration":191},""" +
                """{"trackName":"Finesse (Remix) [feat. Cardi B]","artistName":"Bruno Mars & Cardi B","syncedLyrics":"[00:02.00] right","duration":200},""" +
                """{"trackName":"Finesse Freestyle","artistName":"Bruno Mars","syncedLyrics":"[00:03.00] other song","duration":190}]""",
        )
        val lrclib = Lrclib { null }
        assertEquals("[00:02.00] right", lrclib.pick(results, 190.0, "Finesse", "Bruno Mars, Cardi B")?.lrc)
        // With no artist to check (a label's upload), only a near-identical length vouches for a match.
        assertEquals("[00:01.00] wrong", lrclib.pick(results, 191.5, "Finesse", "")?.lrc)
        assertNull(lrclib.pick(results, 206.0, "Finesse", ""))
    }

    @Test
    fun alignsLyricsToTheSungBars() {
        // Sung on bars 10–19 and 40–49 (20–40 s and 80–100 s); the lyrics were timed 6 s early.
        val t = track("t", sungBars = (10..19).toSet() + (40..49).toSet())
        val text = (0 until 20).map { (14.0 + it) to "line number $it" } + (0 until 20).map { (74.0 + it) to "more words $it" }
        val words = Lrc.parse(lrc(*text.toTypedArray()))
        val off = LyricsAligner.offset(words, t, versionGapSec = 6.0)
        assertNotNull(off)
        assertTrue("offset $off", abs(off!! - 6.0) <= 1.5)
        // Lyrics that never meet the singing are not trusted.
        assertNull(LyricsAligner.offset(words, track("instrumental")))
    }

    private fun timed(vararg lines: Pair<Double, String>) = TimedLyrics(Lrc.parse(lrc(*lines)), "test", 0.0)

    @Test
    fun findsTheSharedWordAndRhymes() {
        val a = timed(100.0 to "dance all night", 140.0 to "we came to party", 160.0 to "hands up")
        val b = timed(10.0 to "party people move", 30.0 to "something else")
        val link = WordPlay.find(a, b, 80.0, 200.0, 0.0, 60.0, 150.0)
        assertNotNull(link)
        assertEquals("party", link!!.aWord.key)
        assertEquals(10.0, link.bWord.startSec, 1e-9)
        assertFalse(link.rhyme)
        // No shared word, no link; a word B only sings mid-line doesn't count either.
        assertNull(WordPlay.find(a, timed(10.0 to "move your body"), 80.0, 200.0, 0.0, 60.0, 150.0))
        assertNull(WordPlay.find(a, timed(10.0 to "let us party now"), 80.0, 200.0, 0.0, 60.0, 150.0))
        // A filler before the word is fine.
        assertNotNull(WordPlay.find(a, timed(10.0 to "oh party people"), 80.0, 200.0, 0.0, 60.0, 150.0))
        assertTrue(WordPlay.hasLink(a, b, 240.0))
        assertFalse(WordPlay.hasLink(a, timed(10.0 to "move your body"), 240.0))
    }

    @Test
    fun planCutsOnTheBeatSoBAnswersTheWord() {
        val a = track("a", sungBars = (0 until 120).toSet())
        val b = track("b", sungBars = (0 until 120).toSet())
        // A sings "party" ending its line at 150.3 s; B's line "party people" starts at 21.1 s.
        val aL = timed(148.3 to "time to party", 160.0 to "on and on")
        val bL = timed(21.1 to "party people", 30.0 to "elsewhere entirely")
        val lyrics = { t: TrackInfo -> if (t.uri == "a") aL else bL }
        val tr = TransitionPlanner.plan(a, 0, 10.0, b, mode = MixMode.TRICKSTER, lyrics = lyrics)
        assertEquals(Recipe.WORD_PLAY, tr.recipe)
        val word = aL.words.single { it.key == "party" }
        // Same tempo: locked. The word loops twice in a beat-sized loop from the quarter beat it
        // starts on ("party, party"), then the mix cuts on A's next beat.
        assertTrue(tr.description, tr.description.contains("locked"))
        val loop = tr.aPlan.events.single { it.type == com.autodj.dj.fx.FxType.LOOP_ROLL }
        assertTrue(loop.start <= word.startSec && word.startSec - loop.start < a.beatSec / 4 + 1e-9)
        assertEquals(WordPlay.loopBeats(word.endSec - loop.start, a.beatSec), loop.p1.toDouble(), 1e-9)
        assertTrue(tr.exitSec >= loop.start + 2 * loop.p1 * a.beatSec - 1e-9 && tr.exitSec - (loop.start + 2 * loop.p1 * a.beatSec) < a.beatSec)
        assertEquals(0.0, (tr.exitSec / a.beatSec) % 1.0, 1e-6)
        assertTrue(tr.entrySec <= 21.1 && 21.1 - tr.entrySec <= b.beatSec + 0.05)
        assertEquals(0.0, (tr.entrySec / b.beatSec) % 1.0, 1e-6)
        assertTrue(tr.aPlan.uses(com.autodj.dj.fx.FxType.ECHO))
        assertTrue(tr.description, tr.description.startsWith("Word play “party”"))
        // Never twice in a row, and nothing without lyrics.
        assertNotEquals(Recipe.WORD_PLAY, TransitionPlanner.plan(a, 0, 10.0, b, listOf(Recipe.WORD_PLAY), mode = MixMode.TRICKSTER, lyrics = lyrics).recipe)
        assertNotEquals(Recipe.WORD_PLAY, TransitionPlanner.plan(a, 0, 10.0, b, mode = MixMode.TRICKSTER).recipe)
        // The next-song choice prefers the song that can answer.
        val c = track("c", sungBars = (0 until 120).toSet())
        assertEquals("b", SetPlanner.pickNext(a, listOf(c, b), lyrics = { t -> when (t.uri) { "a" -> aL; "b" -> bL; else -> null } })?.uri)
    }

    @Test
    fun theTempoGapDecidesHowTheWordBridges() {
        val aL = timed(148.3 to "time to party", 160.0 to "on and on")
        val bL = timed(21.1 to "party people", 30.0 to "elsewhere entirely")
        val a = track("a", sungBars = (0 until 120).toSet())
        fun planInto(bpm: Double): com.autodj.dj.Transition {
            val b = track("b", sungBars = (0 until 160).toSet(), bars = 160, bpm = bpm)
            return TransitionPlanner.plan(a, 0, 10.0, b, mode = MixMode.TRICKSTER, lyrics = { t -> if (t.uri == "a") aL else bL })
        }
        // 120 → 124: locked to A's tempo.
        val near = planInto(124.0)
        assertTrue(near.description, near.description.contains("locked") && near.beatmatched)
        // 120 → 140: an echo bridge, B at its own tempo, a reverb wash on A's word.
        val mid = planInto(140.0)
        assertTrue(mid.description, mid.description.contains("echo bridge") && !mid.beatmatched)
        assertTrue(mid.aPlan.uses(com.autodj.dj.fx.FxType.REVERB))
        // 120 → 170 (not half or double): the word, a pause, then B half a beat after A's cut.
        val far = planInto(170.0)
        assertTrue(far.description, far.description.contains("pause"))
        val cut = far.aPlan.gain.let { g -> (0 until 4000).map { it * 0.05 }.first { g.at(it) == 0f } }
        assertEquals(a.beatSec / 2, far.exitSec - cut, 0.06)
    }

    @Test
    fun aWordBeforeTheDropLoopsIntoIt() {
        // The drop lands at bar 8 (16 s), with "Kissik" opening a line a beat-and-a-bit before the last bar.
        val t = track("k", sungBars = (0 until 120).toSet())
        val l = timed(13.5 to "Kissik Kissik", 40.0 to "Kissik again", 50.0 to "Kissik more", 60.0 to "and the rest")
        val p = com.autodj.dj.Performer.plan(t, 0, 1.0, 120.0, mode = MixMode.TRICKSTER, lyrics = l)
        val drop = 16.0
        val rolls = p.events.filter { it.type == com.autodj.dj.fx.FxType.LOOP_ROLL && it.end <= drop + 1e-9 && it.end > drop - 2 * t.barSec }
        // Starts where the word does, at the word's own loop length, and tightens into the drop.
        assertEquals(13.5, rolls.minOf { it.start }, 1e-9)
        assertEquals(0.125f, rolls.maxBy { it.end }.p1, 1e-6f)
        assertTrue(p.tricks.any { it.label.startsWith("Word loop “Kissik”") })
        // Without lyrics, the plain roll.
        assertTrue(com.autodj.dj.Performer.plan(t, 0, 1.0, 120.0, mode = MixMode.TRICKSTER).tricks.none { it.label.startsWith("Word loop") })
    }
}
