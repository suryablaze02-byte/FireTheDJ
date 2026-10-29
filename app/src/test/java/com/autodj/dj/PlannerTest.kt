package com.autodj.dj

import com.autodj.analysis.AudioFeatures
import com.autodj.analysis.Genres
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Ramp
import com.autodj.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlannerTest {
    private val houseFeatures = AudioFeatures(0.35, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)

    /**
     * A typical dance arrangement: quiet intro, main section, 16-bar breakdown at [drop],
     * main again, quiet outro.
     */
    private fun track(
        name: String,
        bpm: Double,
        family: String = Genres.HOUSE,
        genre: String = family,
        bars: Int = 112,
        intro: Int = 16,
        outro: Int = 96,
        drop: Int = 64,
        loudness: Double = -10.0,
        vocalBars: IntRange = IntRange.EMPTY,
        keyPc: Int = 0,
        keyMinor: Boolean = false,
        keyConfidence: Float = 0f,
        hookBar: Int = -1,
        tempoMap: FloatArray = FloatArray(0),
    ) = TrackInfo(
        uri = name, title = name, artist = "", genre = genre, genreFamily = family,
        genreConfidence = 0.9f, genreSource = "tag",
        durationSec = 0.2 + bars * 240.0 / bpm + 1.0, bpm = bpm, downbeatSec = 0.2, phraseOffsetBars = 0,
        barEnergy = FloatArray(bars) { if (it < intro || it >= outro || it in drop until drop + 16) 0.4f else 1f },
        introEndBar = intro, outroStartBar = outro, dropBars = intArrayOf(drop),
        loudnessDb = loudness, features = houseFeatures,
        vocalness = FloatArray(bars) { if (it in vocalBars) 0.9f else 0.1f },
        keyPc = keyPc, keyMinor = keyMinor, keyConfidence = keyConfidence, hookBar = hookBar,
        tempoMap = tempoMap,
    )

    private fun played(t: TrackInfo, tr: Transition) = t.barTime(if (tr.recipe.cut) tr.exitBar else tr.exitBar + 8) - t.barTime(0)

    @Test
    fun blendsAtTheBreakdownInsteadOfWaitingForTheOutro() {
        val a = track("a", 124.0)
        val b = track("b", 126.0)
        val t = TransitionPlanner.plan(a, 0, 10.0, b)
        assertTrue(t.recipe in setOf(Recipe.BASS_SWAP_BLEND, Recipe.BASS_DROP))
        assertTrue(t.beatmatched)
        assertEquals(64, t.exitBar)
        assertTrue(t.description.contains("breakdown"))
        assertEquals(124.0 / 126.0, t.speedForB(a, b, 1.0), 1e-9)
        // A fades out and B fades in across the 16 bars.
        assertEquals(1f, t.aPlan.gain.at(t.exitSec), 1e-6f)
        assertEquals(0f, t.aPlan.gain.at(t.endSec), 1e-6f)
        assertEquals(0f, t.bPlan.gain.at(t.entrySec), 1e-6f)
        assertEquals(1f, t.bPlan.gain.at(t.entrySec + 60), 1e-6f)
    }

    @Test
    fun longTracksAreLeftAroundTheTargetLength() {
        val a = track("a", 124.0, bars = 200, outro = 184, drop = 104)
        val t = TransitionPlanner.plan(a, 0, 10.0, track("b", 124.0))
        assertTrue("exit ${t.exitBar}", t.exitBar < 184)
        val sec = played(a, t)
        assertTrue("played $sec s", sec in TransitionPlanner.MIN_PLAY_SEC..240.0)
    }

    @Test
    fun cutRecipesPlayEffectsBeforeTheExit() {
        val a = track("a", 124.0)
        val t = TransitionPlanner.plan(a, 0, 10.0, track("b", 124.0), forced = Recipe.ECHO_OUT)
        assertTrue(a.isPhraseStart(t.exitBar))
        assertEquals(a.barTime(t.exitBar - 2), t.startSec, 1e-9)
        assertEquals(a.barTime(t.exitBar + 4), t.endSec, 1e-9)
        val echo = t.aPlan.events.first { it.type == FxType.ECHO }
        assertEquals(t.exitSec, echo.end, 1e-9)
        // Even a cut is a bar-long equal-power crossfade (beats locked): neither slams.
        assertTrue(t.aPlan.gain.at(t.exitSec + a.barSec / 2) in 0.1f..0.9f)
        assertEquals(0f, t.aPlan.gain.at(t.exitSec + a.barSec), 1e-6f)
        assertTrue(t.bPlan.gain.at(t.entrySec + a.barSec / 2) in 0.3f..0.9f)
        assertEquals(1f, t.bPlan.gain.at(t.entrySec + a.barSec), 1e-6f)
    }

    @Test
    fun farTemposAreNeverStretchedForAnOverlap() {
        val a = track("a", 124.0)
        val b = track("b", 100.0)
        val t = TransitionPlanner.plan(a, 0, 10.0, b)
        assertFalse(t.beatmatched)
        assertTrue(t.recipe !in setOf(Recipe.BASS_SWAP_BLEND, Recipe.PHASER_BLEND, Recipe.FLANGER_BLEND))
        assertEquals(1.0, t.speedForB(a, b, 1.0), 0.0)
    }

    @Test
    fun crossGenreTempoGapUsesLoopAndRamp() {
        val a = track("a", 95.0, Genres.HIPHOP)
        val b = track("b", 124.0, Genres.HOUSE)
        val t = TransitionPlanner.plan(a, 0, 10.0, b)
        assertEquals(Recipe.LOOP_RAMP, t.recipe)
        val ramp = t.aSpeedRamp!!
        assertEquals(124.0 / 95.0, ramp.to.toDouble(), 1e-4)
        assertTrue(ramp.end <= t.exitSec - a.barSec + 1e-9)
        assertEquals(a.barTime(t.exitBar - 4), t.startSec, 1e-9)
        // B drops at its own tempo, on its first big section.
        assertEquals(1.0, t.speedForB(a, b, ramp.to.toDouble()), 0.0)
        assertEquals(b.introEndBar, t.entryBar)
        assertTrue(t.aPlan.uses(FxType.LOOP_ROLL))
    }

    @Test
    fun vocalSwapPutsBsVocalOverAsBeat() {
        // B sings from bar 16; both in C major.
        val a = track("a", 124.0, keyConfidence = 0.8f)
        val b = track("b", 124.0, vocalBars = 16 until 48, keyConfidence = 0.8f)
        // (A bass drop would also suit this pair; having just used one, the vocal swap's turn.)
        val t = TransitionPlanner.plan(a, 0, 10.0, b, history = listOf(Recipe.BASS_DROP))
        assertEquals(Recipe.VOCAL_SWAP, t.recipe)
        assertEquals(16, t.entryBar)
        // A's vocal is cut under B's acapella, then the beats swap 8 bars in.
        assertTrue(t.aPlan.uses(FxType.VOCAL_CUT))
        assertTrue(t.bPlan.uses(FxType.VOCAL_ISOLATE))
        assertTrue(t.bPlan.active(FxType.VOCAL_ISOLATE, t.entrySec + 1.0) != null)
        assertIsACrossfade(a, t)
    }

    /** B audible early and rising, A easing down gradually, both overlapping through the middle. */
    private fun assertIsACrossfade(a: TrackInfo, t: Transition) {
        val bar = a.barSec
        val span = t.handoverSec - t.exitSec
        val bAt = { aSec: Double -> t.bPlan.gain.at(t.entrySec + (aSec - t.exitSec)) }
        assertTrue("B audible within 2 bars", bAt(t.exitSec + 2 * bar) > 0.25f)
        assertTrue("A never drops at once", (1..15).all { i ->
            val x = t.exitSec + span * i / 16
            t.aPlan.gain.at(x) - t.aPlan.gain.at(x + span / 16) < 0.25f
        })
        // Equal power all the way: as A goes down, B comes up by the same amount.
        for (i in 1..9) {
            val x = t.exitSec + span * i / 10
            val power = t.aPlan.gain.at(x).let { it * it } + bAt(x).let { it * it }
            assertEquals("power at ${i * 10}%", 1f, power, 0.08f)
        }
        assertEquals(0f, t.aPlan.gain.at(t.handoverSec), 1e-6f)
        assertEquals(1f, bAt(t.handoverSec), 1e-6f)
    }

    @Test
    fun allBlendsIncludingVocalSwapAndBassDropAreEqualPowerCrossfades() {
        val a = track("a", 124.0, keyConfidence = 0.8f)
        val b = track("b", 124.0, vocalBars = 16 until 48, keyConfidence = 0.8f)
        for (r in listOf(Recipe.VOCAL_SWAP, Recipe.BASS_DROP, Recipe.FLANGER_BLEND)) {
            assertIsACrossfade(a, TransitionPlanner.plan(a, 0, 10.0, b, forced = r))
        }
    }

    @Test
    fun filterFadeIsLongWhenTemposMatch() {
        val a = track("a", 124.0)
        val matched = TransitionPlanner.plan(a, 0, 10.0, track("b", 125.0), forced = Recipe.FILTER_FADE)
        assertEquals(8 * a.barSec, matched.handoverSec - matched.exitSec, 1e-6)
        val apart = TransitionPlanner.plan(a, 0, 10.0, track("c", 100.0), forced = Recipe.FILTER_FADE)
        assertEquals(4 * a.barSec, apart.handoverSec - apart.exitSec, 1e-6)
    }

    @Test
    fun setsVaryTheirTransitions() {
        val a = track("a", 124.0, keyConfidence = 0.8f)
        val b = track("b", 124.0, vocalBars = 16 until 48, keyConfidence = 0.8f)
        val picks = (0 until 30).map { TransitionPlanner.plan(a, 0, 10.0, b, random = Random(it)).recipe }.toSet()
        assertTrue("picks $picks", picks.size >= 2)
        // Never a move outside the near-best few: a random pick is still a good one.
        assertTrue(picks.all { it.smooth })
    }

    @Test
    fun blendsAreTrueCrossfades() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        for (r in listOf(Recipe.BASS_SWAP_BLEND, Recipe.PHASER_BLEND, Recipe.FILTER_FADE, Recipe.LOFI_FADE)) {
            assertIsACrossfade(a, TransitionPlanner.plan(a, 0, 10.0, b, forced = r))
        }
    }

    @Test
    fun vocalSwapNeedsMatchingKeys() {
        // C major (8B) into F♯ minor (11A): no shift within ±2 st brings it next to C.
        val a = track("a", 124.0, keyConfidence = 0.8f)
        val b = track("b", 124.0, vocalBars = 16 until 48, keyPc = 6, keyMinor = true, keyConfidence = 0.8f)
        assertFalse(TransitionPlanner.keyShift(a, 0, b).second)
        val t = TransitionPlanner.plan(a, 0, 10.0, b)
        assertNotEquals(Recipe.VOCAL_SWAP, t.recipe)
    }

    @Test
    fun incomingTrackIsPitchedIntoKey() {
        // D major (10B) next to C major (8B): two steps apart, so B drops 2 semitones to C.
        val a = track("a", 124.0, keyConfidence = 0.8f)
        val b = track("b", 124.0, keyPc = 2, keyConfidence = 0.8f)
        assertEquals(-2 to true, TransitionPlanner.keyShift(a, 0, b))
        val t = TransitionPlanner.plan(a, 0, 10.0, b, forced = Recipe.BASS_SWAP_BLEND)
        assertEquals(-2, t.bPitchSemis)
        // A cut has no overlap to clash, so no shift.
        assertEquals(0, TransitionPlanner.plan(a, 0, 10.0, b, forced = Recipe.ECHO_OUT).bPitchSemis)
    }

    @Test
    fun performerPlansTricksInsideTheTrack() {
        // Breakdown 64–79, drop back in at 80, sung hook at 32.
        val t = track("a", 124.0, hookBar = 32, vocalBars = 32 until 40)
        val p = Performer.plan(t, entryBar = 0, startSpeed = 1.0, masterBpm = 126.0, mode = MixMode.TRICKSTER)
        assertEquals(126.0 / 124.0, p.targetSpeed, 1e-9)
        // Glides to the master tempo, then pushes on.
        assertEquals(1f, p.speed.at(0.0), 1e-6f)
        assertEquals((126.0 / 124.0).toFloat(), p.speed.at(t.barTime(12)), 1e-6f)
        assertTrue(p.speed.at(t.barTime(60)) > p.speed.at(t.barTime(12)))
        // Roll build into the drop at bar 80, tightening and rising in pitch, set up by a gap, a bass
        // kill or a tape stop that ends on the drop.
        val drop = t.barTime(80)
        val dropRolls = p.events.filter { it.type == FxType.LOOP_ROLL && it.end <= drop + 1e-9 && it.end >= drop - 2 * t.beatSec - 1e-9 }
        assertTrue(dropRolls.maxBy { it.end }.p1 <= 0.5f)
        assertTrue(dropRolls.all { it.p2 > 0f })
        assertTrue(p.events.any { it.type in setOf(FxType.MUTE, FxType.BASS_KILL, FxType.BRAKE) && it.end == drop })
        // Dressed with a build that ends on the drop and a hit that lands on it.
        assertTrue(p.events.any { it.type.isSfx && it.end == t.barTime(80) })
        assertTrue(p.events.count { it.type.isSfx && it.start == t.barTime(80) } == 2)
        // Hook replay: after the hook's first pass, jump back to it.
        assertEquals(40, p.replay!!.atBar)
        assertEquals(32, p.replay!!.toBar)
        assertTrue(p.tricks.any { it.label == "Hook replay" })
        // Never two replays in a row.
        assertEquals(null, Performer.plan(t, 0, 1.0, 126.0, allowReplay = false, mode = MixMode.TRICKSTER).replay)
    }

    @Test
    fun smoothModeNeverInterruptsATrack() {
        val t = track("a", 92.0, Genres.HIPHOP, hookBar = 32, vocalBars = 0 until 112)
        val p = Performer.plan(t, entryBar = 0, startSpeed = 1.0, masterBpm = 92.0, mode = MixMode.SMOOTH)
        assertEquals(null, p.replay)
        assertTrue(p.events.isEmpty())
        // Energy comes from tempo instead: a 4% push.
        assertEquals(1.04f, p.speed.at(t.durationSec), 1e-4f)
    }

    @Test
    fun tricksterFillsPhrasesWithTricks() {
        // Rapped up to bar 40, a beat tape after: scratches only where nobody is rapping.
        val rap = track("a", 92.0, Genres.HIPHOP, vocalBars = 0 until 40)
        val p = Performer.plan(rap, entryBar = 0, startSpeed = 1.0, masterBpm = 92.0, mode = MixMode.TRICKSTER)
        // A fill every other phrase end, a few per track, never over the rapping.
        val fills = p.tricks.filter { it.label !in setOf("Hook replay", "Bass boost", "Key lift +1") && "drop" !in it.label && !it.label.startsWith("Tempo") && !it.label.startsWith("Glide") }
        assertTrue("fills: ${fills.map { it.label }}", fills.size >= 2)
        assertTrue(p.events.filter { it.type == FxType.SCRATCH }.all { rap.vocalIn(rap.barAt(it.start + 0.01), rap.barAt(it.start + 0.01) + 1) < 0.5 })
        // Every fill ends on a phrase boundary (the horn starts on one).
        for (f in fills) {
            val edge = if (f.label in setOf("Air horn", "Crash", "Gunshot")) f.startSec else f.endSec
            assertTrue(rap.isPhraseStart(rap.barAt(edge + 0.01)))
        }
    }

    @Test
    fun smoothNeverCutsTricksterCutsWithSoundEffects() {
        val a = track("a", 138.0, Genres.TRANCE, outro = 104)
        val b = track("b", 138.0, Genres.TRANCE)
        for (h in listOf(emptyList(), listOf(Recipe.BASS_SWAP_BLEND), listOf(Recipe.BASS_DROP, Recipe.PHASER_BLEND))) {
            assertTrue(TransitionPlanner.plan(a, 0, 10.0, b, history = h, mode = MixMode.SMOOTH).recipe.smooth)
        }
        val trick = TransitionPlanner.plan(a, 0, 10.0, b, history = listOf(Recipe.BASS_SWAP_BLEND, Recipe.HARD_CUT), mode = MixMode.TRICKSTER)
        assertTrue(trick.recipe.cut)
        assertTrue(trick.aPlan.events.any { it.type.isSfx && it.end == trick.exitSec })
        assertTrue(trick.bPlan.events.any { it.type.isSfx && it.start == trick.entrySec })
        // Adding the effects must not move the handover: "mixing" ends once B is fully in.
        assertEquals(trick.exitSec + a.barSec, trick.handoverSec, 1e-9)
    }

    /** 96 BPM for the first minute, then the 128 BPM main section (windows every 12 s). */
    private val slowIntroMap = FloatArray(20) { if (it < 5) 96f else 128f }

    @Test
    fun nextTrackMatchesTheTempoWhereTheSongsMeet() {
        val current = track("super bass", 127.0)
        // Main tempo 128 looks perfect, but it opens at 96: not where a mix from 127 can land.
        val slowIntro = track("slow intro", 128.0, tempoMap = slowIntroMap)
        val steady = track("steady", 120.0)
        assertEquals(steady, SetPlanner.pickNext(current, listOf(slowIntro, steady)))
    }

    @Test
    fun heavierBassCountsAgainstAMatch() {
        val heavy = AudioFeatures(0.45, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)
        val light = AudioFeatures(0.15, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)
        val a = track("a", 124.0)
        val same = track("same", 124.0)
        val b = TrackInfo(
            same.uri + "-light", "light", "", same.genre, same.genreFamily, same.genreConfidence, same.genreSource,
            same.durationSec, same.bpm, same.downbeatSec, same.phraseOffsetBars, same.barEnergy, same.introEndBar,
            same.outroStartBar, same.dropBars, same.loudnessDb, light, same.vocalness, same.keyPc, same.keyMinor,
            same.keyConfidence, same.hookBar, same.tempoMap,
        )
        val c = TrackInfo(
            same.uri + "-heavy", "heavy", "", same.genre, same.genreFamily, same.genreConfidence, same.genreSource,
            same.durationSec, same.bpm, same.downbeatSec, same.phraseOffsetBars, same.barEnergy, same.introEndBar,
            same.outroStartBar, same.dropBars, same.loudnessDb, heavy, same.vocalness, same.keyPc, same.keyMinor,
            same.keyConfidence, same.hookBar, same.tempoMap,
        )
        // a has the house fixture's bass share (0.35): 0.45 is nearer than 0.15.
        assertTrue(SetPlanner.transitionCost(a, c) < SetPlanner.transitionCost(a, b))
    }

    @Test
    fun noBlendIntoAnIntroAtAnotherTempo() {
        val a = track("a", 128.0)
        val b = track("b", 128.0, tempoMap = slowIntroMap)
        for (mode in MixMode.entries) {
            val t = TransitionPlanner.plan(a, 0, 10.0, b, mode = mode)
            if (!t.recipe.cut) {
                // Any overlap must sit where B is already at its main tempo (after its first minute).
                assertTrue("${t.recipe} enters B at ${t.entrySec}", b.steadyIn(t.entrySec, t.entrySec + 16 * b.barSec))
            }
        }
    }

    @Test
    fun incomingTrackComesInWhereItsTempoMatches() {
        // B opens at 96 for its first minute, then runs at 128 like A: the mix enters B in
        // its 128 section rather than at its beginning.
        val a = track("a", 128.0)
        val b = track("b", 128.0, tempoMap = slowIntroMap, bars = 160, outro = 144, drop = 96)
        val t = TransitionPlanner.plan(a, 0, 10.0, b, mode = MixMode.SMOOTH)
        assertTrue("${t.recipe} at ${t.entrySec}", t.recipe.smooth)
        assertTrue(t.entrySec >= 60.0)
        assertTrue(b.steadyIn(t.entrySec, t.entrySec + 16 * b.barSec))
    }

    @Test
    fun aTeluguSetBlendsAfterTwoDrumFillCuts() {
        val t1 = track("t1", 96.0, Genres.POP, genre = "Telugu")
        val t2 = track("t2", 97.0, Genres.POP, genre = "Telugu")
        val s = DecisionEngine.scores(t1, t2, true, 1.0, listOf(Recipe.DRUM_FILL_CUT, Recipe.DRUM_FILL_CUT), mode = MixMode.TRICKSTER)
        val best = s.maxBy { it.value }.key
        assertTrue("$best", best.smooth)
    }

    @Test
    fun tricksterBlendsAgainAfterTwoCuts() {
        // Pop pair where B has an early sung phrase and the keys fit: Smooth takes the vocal swap…
        val a = track("a", 124.0, Genres.POP, keyConfidence = 0.8f)
        val b = track("b", 124.0, Genres.POP, vocalBars = 16 until 48, keyConfidence = 0.8f)
        val smooth = TransitionPlanner.plan(a, 0, 10.0, b, history = listOf(Recipe.BASS_DROP))
        assertEquals(Recipe.VOCAL_SWAP, smooth.recipe)
        // …and so does Trickster after two cuts in a row: a third would stop the set flowing.
        val trick = TransitionPlanner.plan(a, 0, 10.0, b, history = listOf(Recipe.HARD_CUT, Recipe.ECHO_OUT), mode = MixMode.TRICKSTER)
        assertTrue("${trick.recipe}", trick.recipe.smooth)
    }

    @Test
    fun bassDropLandsBsDropAfterABarWithoutBass() {
        val a = track("a", 124.0)
        val b = track("b", 124.0) // intro 16 bars, drop at bar 16
        val t = TransitionPlanner.plan(a, 0, 10.0, b, forced = Recipe.BASS_DROP)
        val lead = b.introEndBar - t.entryBar
        assertTrue(lead == 8 || lead == 16)
        val drop = t.exitSec + lead * a.barSec // A time where B's drop lands
        // The bar before the drop: A's bass is gone and B's hasn't arrived.
        assertEquals(0f, t.aPlan.bass.at(drop - 0.5 * a.barSec), 1e-6f)
        assertEquals(0f, t.bPlan.bass.at(t.entrySec + (drop - 0.5 * a.barSec - t.exitSec)), 1e-6f)
        // On the drop: B at full bass, A gone.
        assertEquals(1f, t.bPlan.bass.at(b.barTime(b.introEndBar) + 0.01), 1e-6f)
        assertEquals(0f, t.aPlan.gain.at(drop), 1e-6f)
        assertIsACrossfade(a, t)
    }

    @Test
    fun deckRunsUntilEffectTailsHaveRungOut() {
        // A filter fade ends with a 3 s reverb wash: A must keep running until it has died away.
        val a = track("a", 124.0)
        val t = TransitionPlanner.plan(a, 0, 10.0, track("b", 124.0), forced = Recipe.FILTER_FADE)
        val reverb = t.aPlan.events.first { it.type == FxType.REVERB }
        assertTrue(t.endSec >= reverb.end + reverb.p1 - 1e-9)
    }

    @Test
    fun halfTimeTracksBeatmatch() {
        val t = TransitionPlanner.plan(track("a", 87.0, Genres.HIPHOP), 0, 10.0, track("b", 172.0, Genres.HIPHOP))
        assertTrue(t.beatmatched)
        assertEquals(2.0, t.tempoMultiple, 0.0)
    }

    @Test
    fun blendsAreTheDefaultAndCutsAreOccasional() {
        val a = track("a", 138.0, Genres.TRANCE, outro = 104)
        val b = track("b", 138.0, Genres.TRANCE)
        // A fresh pair gets a smooth blend.
        assertTrue(TransitionPlanner.plan(a, 0, 10.0, b).recipe.smooth)
        // In Trickster, the genre's signature cut.
        val trick = TransitionPlanner.plan(a, 0, 10.0, b, history = listOf(Recipe.PHASER_BLEND), mode = MixMode.TRICKSTER)
        assertEquals(Recipe.GATE_BUILD, trick.recipe)
        // In Smooth, a cut never follows a cut.
        val hiphop = TransitionPlanner.plan(
            track("a", 90.0, Genres.HIPHOP, outro = 104), 0, 10.0, track("b", 92.0, Genres.HIPHOP),
            history = listOf(Recipe.SCRATCH_CUT),
        )
        assertTrue(hiphop.recipe.smooth)
    }

    @Test
    fun doesNotRepeatTheLastRecipe() {
        val t = TransitionPlanner.plan(track("a", 124.0), 0, 10.0, track("b", 126.0), history = listOf(Recipe.BASS_SWAP_BLEND))
        assertNotEquals(Recipe.BASS_SWAP_BLEND, t.recipe)
    }

    @Test
    fun everyRecipeFitsInsideTheTrack() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        for (r in Recipe.entries) {
            val t = TransitionPlanner.plan(a, 0, 10.0, b, forced = r)
            assertTrue("$r ends after the track", t.endSec <= a.durationSec)
            assertTrue("$r starts in the past", t.startSec >= 10.0)
            assertTrue("$r exit before start", t.exitSec >= t.startSec)
            assertTrue("$r plays too little", played(a, t) >= TransitionPlanner.MIN_PLAY_SEC)
            assertNotNull(t.phases.firstOrNull())
        }
    }

    @Test
    fun shortTrackStillGetsATransition() {
        val a = track("a", 124.0, bars = 40, intro = 4, outro = 32, drop = 16)
        val t = TransitionPlanner.plan(a, 0, 5.0, track("b", 124.0))
        assertTrue(t.endSec <= a.durationSec)
    }

    @Test
    fun nextTrackStaysInGenreThenMovesToANeighbour() {
        val house = track("house", 124.0, Genres.HOUSE)
        val house2 = track("house2", 125.0, Genres.HOUSE)
        val techno = track("techno", 128.0, Genres.TECHNO)
        val dnb = track("dnb", 174.0, Genres.DNB)
        assertEquals(house2, SetPlanner.pickNext(house, listOf(dnb, techno, house2)))
        assertEquals(techno, SetPlanner.pickNext(house, listOf(dnb, techno)))
        assertEquals(listOf(house2, techno, dnb), SetPlanner.upcoming(house, listOf(dnb, techno, house2)))
    }

    @Test
    fun everySessionCanTakeADifferentRoute() {
        val tracks = (1..6).map { track("t$it", 120.0 + it) }
        val openers = (0 until 60).map { SetPlanner.opener(tracks, Random(it)) }.toSet()
        assertEquals("every track can open", tracks.toSet(), openers)

        // Near-equal next tracks are chosen at random; a clearly worse one never is.
        val house = track("house", 124.0)
        val a = track("a", 124.5)
        val b = track("b", 123.5)
        val far = track("far", 174.0, Genres.DNB)
        val picks = (0 until 60).map { SetPlanner.pickNext(house, listOf(a, b, far), Random(it)) }.toSet()
        assertEquals(setOf(a, b), picks)
    }

    @Test
    fun setOrderGroupsSimilarTracks() {
        val tracks = listOf(
            track("house1", 122.0, genre = "House"),
            track("techno", 132.0, Genres.TECHNO),
            track("house2", 124.0, genre = "Deep House"),
            track("techno2", 130.0, Genres.TECHNO),
        )
        val order = SetPlanner.order(tracks).map { it.title }
        assertEquals("house1", order.first())
        assertEquals("house2", order[1])
        assertTrue(order.indexOf("techno") - order.indexOf("techno2") in listOf(-1, 1))
    }

    @Test
    fun soundEffectsFollowTheMusic() {
        val t = track("a", 126.0) // breakdown 64–79, drop at 80
        // Quieter over the breakdown than over the main section.
        assertTrue(SfxDressing.level(t, 70) < 0.7f * SfxDressing.level(t, 40))
        // The build is as long as the quiet run-up: 8 bars into the drop, 2 where there is none.
        assertEquals(8, SfxDressing.buildBars(t, 80))
        assertEquals(2, SfxDressing.buildBars(t, 40))
        // Different drops get different dressing, and both styles have their own.
        val dressings = (0 until 3).map { turn -> SfxDressing.build(t, 80, 8, turn).map { it.type } + SfxDressing.hit(t, 80, turn).map { it.type } }
        assertEquals(3, dressings.toSet().size)
        val rap = track("r", 92.0, Genres.HIPHOP)
        val rapTypes = (0 until 3).flatMap { SfxDressing.build(rap, 80, 4, it) + SfxDressing.hit(rap, 80, it) }.map { it.type }.toSet()
        assertTrue(FxType.SIREN in rapTypes && FxType.HORN in rapTypes && FxType.GUNSHOT in rapTypes)
        // Every build ends on the hit, every hit starts on it.
        for (turn in 0 until 3) {
            assertTrue(SfxDressing.build(t, 80, 8, turn).all { it.end == t.barTime(80) })
            assertTrue(SfxDressing.hit(t, 80, turn).all { it.start == t.barTime(80) })
        }
    }

    @Test
    fun indianTagsGoToPopAndGetRegionalDrums() {
        assertEquals(Genres.POP, Genres.family("Telugu Folk"))
        assertEquals(Genres.POP, Genres.family("Telangana folk"))
        assertEquals(Genres.ROCK, Genres.family("Folk"))
        assertEquals(Genres.HIPHOP, Genres.family("Telugu Hip Hop"))
        assertEquals(Genres.Region.INDIAN, Genres.region("", "Maala (From \"Aadarsha Kutumbam\")"))
        assertEquals(Genres.Region.SOUTH, Genres.region("Tollywood"))
        assertEquals(Genres.Region.NORTH, Genres.region("Bhangra"))
        // YouTube titles and artists, with no genre tag at all.
        assertEquals(Genres.Region.SOUTH, Genres.region("", "Time To Party Full Video Song | Attarintiki Daredi || Pawan Kalyan, Samantha || DSP"))
        assertEquals(Genres.Region.SOUTH, Genres.region("", "Kissik", "Devi Sri Prasad, Sublahshini"))
        assertEquals(Genres.Region.INDIAN, Genres.region("", "Chuttamalle Lyrical"))
        assertEquals(null, Genres.region("", "Strobe", "deadmau5"))
        val telugu = track("t", 96.0, Genres.POP, genre = "Telugu")
        val types = (0 until 3).flatMap { SfxDressing.build(telugu, 80, 4, it) + SfxDressing.hit(telugu, 80, it) }.map { it.type }.toSet()
        assertTrue(types.any { it == FxType.DAPPU || it == FxType.MARFA })
        assertFalse(FxType.SNARE_ROLL in types) // no EDM dressing
        assertFalse(FxType.DHOL in types) // the dhol is the north's
    }

    @Test
    fun drumFillCutIsForIndianMusicOnly() {
        val h1 = track("h1", 124.0)
        val h2 = track("h2", 124.0)
        assertEquals(Double.NEGATIVE_INFINITY, DecisionEngine.scores(h1, h2, true, 1.0, emptyList())[Recipe.DRUM_FILL_CUT]!!, 0.0)
        val t1 = track("t1", 96.0, Genres.POP, genre = "Telugu")
        val t2 = track("t2", 96.0, Genres.POP, genre = "Telugu")
        val scores = DecisionEngine.scores(t1, t2, true, 1.0, emptyList(), mode = MixMode.TRICKSTER)
        assertEquals(Recipe.DRUM_FILL_CUT, scores.maxBy { it.value }.key)
        val tr = TransitionPlanner.plan(t1, 0, 30.0, t2, forced = Recipe.DRUM_FILL_CUT, mode = MixMode.TRICKSTER)
        val drums = setOf(FxType.DAPPU, FxType.MARFA)
        assertTrue(tr.aPlan.events.any { it.type in drums && it.end == tr.exitSec }) // the fill runs into the cut
        assertTrue(tr.bPlan.events.any { it.type in drums && it.start == tr.entrySec }) // its accents land on B's one
        assertTrue(tr.description, tr.description.contains("fill cut"))
    }

    @Test
    fun newMovesLandWhereTheyBelong() {
        val a = track("a", 126.0)
        val b = track("b", 126.0)
        // Drop swap: leaves at A's own drop, half a beat of silence, into B at full swing.
        val swap = TransitionPlanner.plan(a, 0, 30.0, b, forced = Recipe.DROP_SWAP)
        assertEquals(Recipe.DROP_SWAP, swap.recipe)
        assertTrue(a.energyIn(swap.exitBar - 4, swap.exitBar) < 0.65 && a.energyIn(swap.exitBar, swap.exitBar + 4) >= 0.85)
        val gap = swap.aPlan.events.single { it.type == FxType.MUTE }
        assertEquals(swap.exitSec + 0.024, gap.end, 1e-9) // held until A has faded
        assertEquals(a.beatSec / 2, swap.exitSec - gap.start, 1e-9)
        assertTrue(b.energyIn(swap.entryBar, swap.entryBar + 4) >= 0.8)
        // Brake stop: the platter stops, then silence until the cut.
        val brake = TransitionPlanner.plan(a, 0, 30.0, b, forced = Recipe.BRAKE_STOP)
        assertTrue(brake.aPlan.uses(FxType.BRAKE) && brake.aPlan.uses(FxType.MUTE))
        // Hard cut: nothing added, even in Trickster, and A is gone straight after the line.
        val cut = TransitionPlanner.plan(a, 0, 30.0, b, forced = Recipe.HARD_CUT, mode = MixMode.TRICKSTER)
        assertTrue(cut.aPlan.events.isEmpty() && cut.bPlan.events.isEmpty())
        assertEquals(0f, cut.aPlan.gain.at(cut.exitSec + 0.02), 0f)
        // Breakdown swap: starts at A's breakdown, under B's beat.
        val bd = TransitionPlanner.plan(a, 0, 30.0, b, forced = Recipe.BREAKDOWN_SWAP)
        assertEquals(Recipe.BREAKDOWN_SWAP, bd.recipe)
        assertEquals(64, bd.exitBar)
        assertTrue(b.energyIn(bd.entryBar, bd.entryBar + 4) >= 0.6)
    }

    @Test
    fun keyLiftsUpTheWheelIntoBiggerTracks() {
        val a = track("a", 124.0, keyPc = 0, keyConfidence = 0.9f) // C major, 8B
        val up1 = track("g", 124.0, keyPc = 7, keyConfidence = 0.9f) // G major, 9B
        val up2 = track("d", 124.0, keyPc = 2, keyConfidence = 0.9f) // D major, 10B
        val quieter = track("q", 124.0, keyPc = 7, keyConfidence = 0.9f, loudness = -16.0)
        assertEquals(1, TransitionPlanner.keyLift(a, 0, up1))
        assertEquals(2, TransitionPlanner.keyLift(a, 0, up2))
        assertEquals(0, TransitionPlanner.keyLift(a, 0, quieter))
        assertEquals(0, TransitionPlanner.keyLift(up1, 0, a)) // stepping down isn't a lift
        val same = track("c", 124.0, keyPc = 0, keyConfidence = 0.9f)
        assertTrue(SetPlanner.transitionCost(a, up1) < SetPlanner.transitionCost(a, same))
        val tr = TransitionPlanner.plan(a, 0, 30.0, up2, forced = Recipe.ECHO_OUT)
        assertTrue(tr.description, tr.description.contains("key lift +2"))
        assertEquals(0, tr.bPitchSemis) // a cut plays B as it is, a step up
    }



    @Test
    fun aSkipLandsTheMixOnTheAskedBar() {
        val a = track("a", 124.0)
        val b = track("b", 124.0)
        // Tapped two bars before a phrase: B comes in on it, with a move whose lead-in has room.
        val now = a.barTime(30) + 0.1
        for (mode in MixMode.entries) {
            val tr = TransitionPlanner.plan(a, 0, now, b, exitAt = 32, mode = mode)
            assertEquals("$mode", 32, tr.exitBar)
            assertTrue("$mode ${tr.description}", tr.startSec >= now)
            assertTrue(tr.recipe != Recipe.WORD_PLAY)
        }
        // Early in a song (still in its intro) a skip still gets a proper mix, not just the fallback cut.
        val early = TransitionPlanner.plan(a, 0, a.barTime(6) + 0.1, b, exitAt = 8, mode = MixMode.SMOOTH)
        assertEquals(8, early.exitBar)
        assertTrue(early.description, early.recipe != Recipe.HARD_CUT)
        // Tapped again a beat before the next bar: only a move without a lead-in fits.
        val late = a.barTime(33) - a.beatSec
        val tr = TransitionPlanner.plan(a, 0, late, b, exitAt = 33)
        assertEquals(33, tr.exitBar)
        assertTrue(tr.description, tr.startSec >= late - 1e-9)
        // The speed lane after a skip holds where it got to.
        val lane = Lane(listOf(Ramp(1.0, 1.01, 1f, 0.5f), Ramp(2.0, 2.01, 0.5f, 1f)), 1f)
        assertEquals(1f, lane.until(2.5).at(3.0), 0f)
        assertEquals(0.5f, lane.until(1.5).at(3.0), 0f)
    }
    @Test
    fun theLanguageSungPicksThePartySounds() {
        val latinTypes = setOf(FxType.TIMBALES, FxType.WHISTLE, FxType.COWBELL, FxType.GUIRA, FxType.SHAKER)
        val arabicTypes = setOf(FxType.DARBUKA, FxType.CLAPS, FxType.MIZMAR)
        val regional = latinTypes + arabicTypes + setOf(FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL, FxType.NADASWARAM)
        fun dressing(t: TrackInfo) = (0 until 3).flatMap { SfxDressing.build(t, 80, 4, it) + SfxDressing.hit(t, 80, it) }.map { it.type }.toSet()
        val saved = SfxDressing.languageOf
        try {
            // Reggaeton names Spanish: Latin sounds, and timbales for the drum-fill cut.
            val reggaeton = track("r", 96.0, Genres.LATIN, genre = "Reggaeton")
            assertEquals(SfxDressing.Flavour.LATIN, SfxDressing.flavour(reggaeton))
            assertTrue(dressing(reggaeton).any { it in latinTypes })
            assertEquals(FxType.TIMBALES, SfxDressing.drum(reggaeton, 0))
            // A techno track sung in Arabic stays techno, with the darbuka on top.
            SfxDressing.languageOf = { "Arabic" }
            val techno = track("a", 128.0, Genres.TECHNO)
            assertEquals(SfxDressing.Flavour.ARABIC, SfxDressing.flavour(techno))
            assertTrue(dressing(techno).any { it in arabicTypes })
            val p = Performer.plan(techno, 0, 1.0, 128.0, mode = MixMode.TRICKSTER)
            assertTrue(p.events.any { it.type in arabicTypes })
            assertTrue(p.tricks.any { it.label in setOf("Gate stutter", "Mids out", "Roll fill", "Echo throw", "Muffle") })
            // Telugu by language alone gets the south's drums; an English house track nothing regional.
            SfxDressing.languageOf = { "Telugu" }
            assertEquals(SfxDressing.Flavour.SOUTH_INDIAN, SfxDressing.flavour(track("t", 96.0, Genres.POP)))
            SfxDressing.languageOf = { "English" }
            val house = track("h", 124.0)
            assertEquals(null, SfxDressing.flavour(house))
            assertTrue(dressing(house).none { it in regional })
            // A song whose language isn't known follows the set's.
            SfxDressing.languageOf = { null }
            SfxDressing.setLanguage = "Spanish"
            assertEquals(SfxDressing.Flavour.LATIN, SfxDressing.flavour(house))
        } finally {
            SfxDressing.languageOf = saved
            SfxDressing.setLanguage = null
        }
    }
    @Test
    fun indianTracksGetRegionalFillsAndARewind() {
        val t = track("t", 96.0, Genres.HIPHOP, genre = "Telugu Hip Hop")
        val p = Performer.plan(t, 0, 1.0, 96.0, mode = MixMode.TRICKSTER)
        assertTrue(p.events.any { it.type == FxType.DAPPU || it.type == FxType.MARFA || it.type == FxType.THAVIL })
        val r = p.replay!!
        assertTrue(r.cut)
        assertEquals(8, r.atBar - r.toBar)
        assertTrue(p.events.any { it.type == FxType.BACKSPIN && it.end == t.barTime(r.atBar) })
        assertTrue(p.tricks.any { it.label == "Rewind" })
        // Smooth mode leaves the track alone.
        assertEquals(null, Performer.plan(t, 0, 1.0, 96.0, mode = MixMode.SMOOTH).replay)
    }

    @Test
    fun eachGenreSoundsLikeItself() {
        val techno = track("t", 130.0, Genres.TECHNO)
        val house = track("h", 124.0, Genres.HOUSE)
        val chill = track("c", 90.0, Genres.CHILL)
        fun sfx(t: TrackInfo) = (0 until 3).flatMap { SfxDressing.build(t, 80, 4, it) + SfxDressing.hit(t, 80, it) }.map { it.type }.toSet()
        // No horns, sirens or gunshots outside the genres that use them; chill barely any.
        for (t in listOf(techno, house)) assertTrue(sfx(t).none { it in setOf(FxType.HORN, FxType.SIREN, FxType.GUNSHOT, FxType.LASER) })
        assertTrue(sfx(chill).all { it in setOf(FxType.REVERSE_CYMBAL, FxType.WHOOSH, FxType.DOWNLIFTER) })
        // The set's crate styles a mix: a hip-hop set dresses even a house song in horns and shots.
        val hiphopStyle = (0 until 3).flatMap { SfxDressing.hit(house, 80, it, family = Genres.HIPHOP) }.map { it.type }.toSet()
        assertTrue(FxType.HORN in hiphopStyle)
        // Scratch cuts are hip-hop's alone; with All genres the incoming song decides.
        assertEquals(Double.NEGATIVE_INFINITY, DecisionEngine.scores(house, house, true, 1.0, emptyList(), mode = MixMode.TRICKSTER)[Recipe.SCRATCH_CUT]!!, 0.0)
        assertTrue(DecisionEngine.scores(house, house, true, 1.0, emptyList(), mode = MixMode.TRICKSTER, family = Genres.HIPHOP)[Recipe.SCRATCH_CUT]!!.isFinite())
        // In-track fills: no scratches outside hip-hop.
        for (t in listOf(techno, house, track("p", 124.0, Genres.POP), track("r", 120.0, Genres.ROCK))) {
            assertFalse(Performer.plan(t, 0, 1.0, t.bpm, mode = MixMode.TRICKSTER).events.any { it.type == FxType.SCRATCH })
        }
    }

    @Test
    fun theGapBeforeACutStaysSilentThroughTheCut() {
        // After the brake (or a drop swap's build) A must stay silent: no blip of A as B comes in.
        val a = track("a", 126.0)
        val b = track("b", 126.0)
        for (r in listOf(Recipe.BRAKE_STOP, Recipe.DROP_SWAP)) {
            val tr = TransitionPlanner.plan(a, 0, 30.0, b, forced = r)
            val gap = tr.aPlan.events.single { it.type == FxType.MUTE }
            // A's sound is gone (gain 0) before the gap lets go.
            assertEquals("$r", 0f, tr.aPlan.gain.at(gap.end), 0f)
        }
    }

    @Test
    fun aBackspunRecordDoesNotResume() {
        val a = track("a", 126.0)
        val tr = TransitionPlanner.plan(a, 0, 30.0, track("b", 126.0), forced = Recipe.BACKSPIN_DROP)
        // A is gone as soon as the spin ends; B is in at full on the one.
        assertEquals(0f, tr.aPlan.gain.at(tr.exitSec + 0.05), 0f)
        assertEquals(1f, tr.bPlan.gain.at(tr.entrySec + 0.05), 0f)
    }

    @Test
    fun tricksterKeepsATrickComingAndOnlyVoiceSafeOnesOverSinging() {
        // Sung all the way through: tricks still come, but only ones that sit on top of the voice.
        val sung = track("sung", 124.0, Genres.HOUSE, bars = 160, outro = 144, vocalBars = 0 until 160)
        val p = Performer.plan(sung, entryBar = 0, startSpeed = 1.0, masterBpm = 124.0, mode = MixMode.TRICKSTER)
        val fills = p.tricks.filterNot { it.label.startsWith("Tempo") || it.label.startsWith("Glide") || "drop" in it.label || it.label in setOf("Hook replay", "Rewind", "Bass boost", "Key lift +1") }
        assertTrue("fills: ${fills.map { it.label }}", fills.size in 3..6)
        val bending = setOf(FxType.LOOP_ROLL, FxType.SCRATCH, FxType.GATE, FxType.BRAKE, FxType.BACKSPIN)
        val builds = p.tricks.filter { "drop" in it.label }.map { it.startSec..it.endSec }
        // Only the builds into drops (the showpieces) bend the track.
        val wrong = p.events.filter { e -> e.type in bending && builds.none { e.start in it } }
        assertTrue("bending: ${wrong.map { "${it.type} @${it.start}" }} tricks ${p.tricks.map { "${it.label} @${it.startSec}" }}", wrong.isEmpty())
        // About a trick every 16 bars: never two within 8 bars.
        val starts = fills.map { it.startSec }.sorted()
        assertTrue(starts.zipWithNext().all { (x, y) -> y - x >= 8 * sung.barSec - 1e-6 })
    }

    @Test
    fun aSongWithoutAClearDropStillGetsAShowpiece() {
        // Energy steps up at bar 48 but never from quiet: no textbook drop.
        val t = track("steady", 124.0, Genres.HOUSE, bars = 128, outro = 112, drop = 48)
        val p = Performer.plan(t, entryBar = 0, startSpeed = 1.0, masterBpm = 124.0, mode = MixMode.TRICKSTER)
        assertTrue("tricks: ${p.tricks.map { it.label }}", p.tricks.any { "drop" in it.label })
    }

    @Test
    fun noMixPlaysBothSongsNearFullOrTwoBasslinesAtOnce() {
        val a = track("a", 124.0, Genres.HOUSE, keyConfidence = 0.8f, vocalBars = 40 until 56)
        val b = track("b", 124.0, Genres.HOUSE, keyConfidence = 0.8f, vocalBars = 16 until 48)
        val recipes = Recipe.entries.filter { it != Recipe.WORD_PLAY && it != Recipe.LOOP_RAMP && it != Recipe.DRUM_FILL_CUT }
        for (r in recipes) {
            val t = runCatching { TransitionPlanner.plan(a, 0, 10.0, b, forced = r, mode = MixMode.TRICKSTER) }.getOrNull() ?: continue
            if (t.recipe != r) continue
            var s = t.exitSec
            while (s < t.handoverSec) {
                val bSec = t.entrySec + (s - t.exitSec)
                val aG = t.aPlan.gain.at(s)
                val bG = t.bPlan.gain.at(bSec)
                assertFalse("$r at ${"%.2f".format(s)}: A $aG and B $bG both near full", aG > 0.75f && bG > 0.75f)
                // A high-pass takes a deck's bass out as surely as its bass control.
                val aCut = (t.aPlan.active(FxType.HIGH_PASS, s)?.amount(s) ?: 0f) > 0.3f
                val bCut = (t.bPlan.active(FxType.HIGH_PASS, bSec)?.amount(bSec) ?: 0f) > 0.3f
                val aLow = if (aCut) 0f else aG * t.aPlan.bass.at(s)
                val bLow = if (bCut) 0f else bG * t.bPlan.bass.at(bSec)
                assertFalse("$r at ${"%.2f".format(s)}: two basslines (A $aLow, B $bLow)", aLow > 0.5f && bLow > 0.5f)
                s += 0.05
            }
        }
    }

    @Test
    fun theLastSungChorusStepsUpAKey() {
        // A loud, sung chorus at bars 112–127, before the outro at 144.
        val t = track("chorus", 124.0, Genres.POP, bars = 160, outro = 144, vocalBars = 112 until 128)
        val p = Performer.plan(t, entryBar = 0, startSpeed = 1.0, masterBpm = 124.0, mode = MixMode.TRICKSTER)
        val at = p.keyLiftSec
        assertNotNull(at)
        assertTrue(t.isPhraseStart(t.barAt(at!! + 0.01)))
        assertTrue(t.barAt(at + 0.01) in 112 until 128)
        assertTrue(p.tricks.any { it.label == "Key lift +1" })
        // Smooth leaves the key alone.
        assertNull(Performer.plan(t, 0, 1.0, 124.0, mode = MixMode.SMOOTH).keyLiftSec)
    }

    @Test
    fun aHalfTimeBarSlowsToHalfSpeedAndSnapsBackOnTheDownbeat() {
        // Bass music, no singing: some song in the crate gets a half-time bar in its rotation.
        val p = (0 until 12).asSequence().map { n ->
            val t = track("bass$n", 140.0, Genres.BASS, bars = 160, outro = 144)
            t to Performer.plan(t, entryBar = 0, startSpeed = 1.0, masterBpm = 140.0, mode = MixMode.TRICKSTER)
        }.firstOrNull { (_, p) -> p.tricks.any { it.label.startsWith("Half-time") } }
        assertNotNull("no half-time in 12 songs", p)
        val (t, perf) = p!!
        val trick = perf.tricks.first { it.label.startsWith("Half-time") }
        val before = perf.speed.at(trick.startSec - 0.05)
        assertEquals(before / 2, perf.speed.at(trick.startSec + 0.5), 1e-4f)
        assertEquals(before, perf.speed.at(trick.endSec + 0.05), 1e-4f)
        assertTrue(t.isPhraseStart(t.barAt(trick.endSec + 0.01)))
    }
}
