package com.autodj.dj

import com.autodj.analysis.AudioFeatures
import com.autodj.analysis.Genres
import com.autodj.dj.fx.FxType
import com.autodj.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
        // Even a cut is a bar-long crossfade: both decks heard together, neither slams.
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
        // Roll build into the drop at bar 80, tightening to 1/8 beat, landing with horn and impact.
        val dropRolls = p.events.filter { it.type == FxType.LOOP_ROLL && it.end == t.barTime(80) }
        assertEquals(0.125f, dropRolls.single().p1, 0f)
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
        val rap = track("a", 92.0, Genres.HIPHOP, vocalBars = 0 until 112)
        val p = Performer.plan(rap, entryBar = 0, startSpeed = 1.0, masterBpm = 92.0, mode = MixMode.TRICKSTER)
        // A fill at nearly every phrase end from bar 16 to the outro: 16, 24, … 80 minus busy spots.
        val fills = p.tricks.filter { it.label !in setOf("Roll into drop", "Hook replay") && !it.label.startsWith("Tempo") && !it.label.startsWith("Glide") }
        assertTrue("fills: ${fills.map { it.label }}", fills.size >= 5)
        assertTrue(p.events.any { it.type == FxType.SCRATCH })
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
        val trick = TransitionPlanner.plan(a, 0, 10.0, b, mode = MixMode.TRICKSTER)
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
    fun tricksterPrefersCutsEvenWhenAVocalSwapFits() {
        // Pop pair where B has an early sung phrase and the keys fit: Smooth takes the vocal swap…
        val a = track("a", 124.0, Genres.POP, keyConfidence = 0.8f)
        val b = track("b", 124.0, Genres.POP, vocalBars = 16 until 48, keyConfidence = 0.8f)
        val smooth = TransitionPlanner.plan(a, 0, 10.0, b, history = listOf(Recipe.BASS_DROP))
        assertEquals(Recipe.VOCAL_SWAP, smooth.recipe)
        // …Trickster goes for a cut trick.
        val trick = TransitionPlanner.plan(a, 0, 10.0, b, mode = MixMode.TRICKSTER)
        assertTrue("${trick.recipe}", trick.recipe.cut)
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
}
