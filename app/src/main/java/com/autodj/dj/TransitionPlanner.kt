package com.autodj.dj

import com.autodj.analysis.Camelot
import com.autodj.analysis.Genres
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.Ramp
import com.autodj.model.TrackInfo
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** A planned mix from the playing track A into the next track B. Times are in each track's own seconds. */
class Transition(
    val recipe: Recipe,
    /** A time when the first effect starts (equals [exitSec] for blends). */
    val startSec: Double,
    val exitBar: Int,
    /** A time when B starts playing. */
    val exitSec: Double,
    /** A time when A is finished (tails included) and the decks swap. */
    val endSec: Double,
    val entryBar: Int,
    val entrySec: Double,
    /** B's beats per A beat (0.5, 1 or 2) when beat-matching half/double-time tracks. */
    val tempoMultiple: Double,
    val beatmatched: Boolean,
    val aPlan: FxPlan,
    val bPlan: FxPlan,
    /** Deck A's speed over A time during a tempo ramp, or null. */
    val aSpeedRamp: Ramp?,
    /** A time when B is fully in and A's own sound is gone (effect tails may still ring until [endSec]). */
    val handoverSec: Double,
    /** Key shift applied to B (semitones, key-locked tempo) so the two keys fit. */
    val bPitchSemis: Int,
    val phases: List<Phase>,
    val description: String,
) {
    /** Playback speed for B so its tempo locks to A playing at [aSpeed]. */
    fun speedForB(a: TrackInfo, b: TrackInfo, aSpeed: Double) =
        if (beatmatched) a.bpm * aSpeed * tempoMultiple / b.bpm else 1.0
}

/**
 * Decides the whole transition by searching every combination of recipe, exit bar in A and
 * entry bar in B, and scoring each on:
 *
 * - **pace:** B should take over around [TARGET_PLAY_SEC] into A, never before [MIN_PLAY_SEC],
 *   so the set moves instead of waiting for every outro;
 * - **A's structure:** leave on a phrase boundary, ideally where A breaks down or starts its
 *   outro. Blends want A calming down underneath; cuts want A loud right up to the cut;
 * - **B's structure:** blends enter on B's intro (it needs quiet bars to fade in over); cuts can
 *   drop straight into B's first big section;
 * - **the recipe's own fit** from [DecisionEngine] (genre, tempo match, history).
 */
object TransitionPlanner {
    /** Max time-stretch for a beat-matched overlap before it starts to sound wrong. */
    const val MAX_STRETCH = 0.08

    /** Max tempo change a Loop & ramp will attempt (after half/double-time folding). */
    const val MAX_RAMP = 0.45

    const val TARGET_PLAY_SEC = 165.0
    const val MIN_PLAY_SEC = 75.0
    private const val MIN_B_REMAINING_SEC = 90.0
    private const val LOUD = 0.7f

    fun tempoMultiple(fromBpm: Double, toBpm: Double): Double =
        listOf(1.0, 2.0, 0.5).minBy { abs(fromBpm * it / toBpm - 1) }

    /** Speed B would need to match A's tempo. */
    fun speedFor(fromBpm: Double, toBpm: Double) = fromBpm * tempoMultiple(fromBpm, toBpm) / toBpm

    private class Candidate(val recipe: Recipe, val entryBar: Int, val exitBar: Int, val mixBars: Int, val score: Double)

    fun plan(
        a: TrackInfo,
        aEntryBar: Int,
        nowSec: Double,
        b: TrackInfo,
        history: List<Recipe> = emptyList(),
        forced: Recipe? = null,
        /** Key shift A is already playing with. */
        aPitchSemis: Int = 0,
        mode: MixMode = MixMode.SMOOTH,
        /** For variety: picks among near-equal options. Null = always the top one (tests). */
        random: Random? = null,
    ): Transition {
        val multiple = tempoMultiple(a.bpm, b.bpm)
        val speed = a.bpm * multiple / b.bpm
        val beatmatched = abs(speed - 1) <= MAX_STRETCH
        // Ramp-ups stay short: the new song should arrive, not wait behind a long build.
        val rampBars = 4
        val (shift, keysFit) = keyShift(a, aPitchSemis, b)
        val ranked: List<Pair<Recipe, Double>> = if (forced != null) {
            listOf(forced to 0.0)
        } else {
            DecisionEngine.scores(a, b, beatmatched, speed, history, keysFit, mode)
                .filterValues { it.isFinite() }
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }
        }

        val firstBar = max(0, a.barAt(nowSec) + 2)
        val lastBar = floor((a.durationSec - 0.25 - a.downbeatSec) / a.barSec).toInt()
        val firstPhrase = (0 until 8).first { b.isPhraseStart(it) }
        // B's first sung phrase, if it comes early enough not to skip much of B.
        val firstVocal = (0 until b.barCount / 2)
            .takeWhile { b.barTime(it) - b.barTime(firstPhrase) <= MAX_VOCAL_SKIP_SEC }
            .firstOrNull { b.isPhraseStart(it) && b.vocalIn(it, it + 8) >= VOCAL }
        // For a bass drop: start B 16 (or 8) bars before its drop, so the drop lands in the mix.
        val bassDropEntry = listOf(8, 16).map { b.introEndBar - it }.firstOrNull { it >= 0 && b.isPhraseStart(it) }
            ?.takeIf { b.introEndBar < b.barCount - 32 }
        // B's first phrase at its main tempo: where an overlap can go if B opens at another tempo.
        val steadyEntry = (0 until b.barCount - 32)
            .firstOrNull { b.isPhraseStart(it) && b.steadyIn(b.barTime(it), b.barTime(it + 16)) }
        // B need not start at its beginning: any phrase in its first 40% that runs at its main
        // tempo (so it locks to A) is a place to bring it in.
        val tempoEntries = (0 until (b.barCount * MAX_ENTRY_SHARE).toInt())
            .filter { b.isPhraseStart(it) && b.steadyIn(b.barTime(it), b.barTime(it + 16)) }
        val entries = (
            listOfNotNull(
                firstPhrase,
                b.introEndBar.takeIf { it != firstPhrase && b.isPhraseStart(it) && it in 8 until b.barCount - 32 },
                firstVocal,
                bassDropEntry,
                steadyEntry,
            ) + tempoEntries
            ).distinct()

        // The best few styles first; if none of them fits anywhere, every style.
        var options = search(ranked.take(5), a, aEntryBar, b, entries, firstPhrase, steadyEntry, firstBar, lastBar, rampBars, beatmatched)
        if (options.isEmpty() && ranked.size > 5) {
            options = search(ranked.drop(5), a, aEntryBar, b, entries, firstPhrase, steadyEntry, firstBar, lastBar, rampBars, beatmatched)
        }
        // Variety: any style nearly as good as the best is fair game, so sets don't repeat one move.
        val top = options.maxOfOrNull { it.score }
        val best = when {
            top == null -> null
            random == null -> options.maxBy { it.score }
            else -> options.filter { it.score >= top - VARIETY_MARGIN }.random(random)
        }
        // Nothing fits (track nearly over): short filter fade from the next bar that leaves room.
        val c = best ?: Candidate(
            Recipe.FILTER_FADE, firstPhrase,
            exitBar = min(max(firstBar, a.outroStartBar), max(0, lastBar - 4)).coerceAtLeast(max(0, a.barAt(nowSec) + 1)),
            mixBars = max(1, min(4, lastBar - max(0, a.barAt(nowSec) + 1))),
            score = 0.0,
        )

        val raw = RecipeBuilder(a, b, a.barTime(c.exitBar), b.barTime(c.entryBar), multiple, beatmatched)
            .build(c.recipe, c.mixBars, rampBars, rampTo = 1 / speed)
        val built = if (mode == MixMode.TRICKSTER) withSoundEffects(raw, a, b, c, entrySec = b.barTime(c.entryBar)) else raw

        val tempoText = when {
            beatmatched -> {
                val pct = (speed - 1) * 100
                if (abs(pct) < 0.05) "same tempo" else "tempo ${if (pct > 0) "+" else ""}${"%.1f".format(pct)}%"
            }
            c.recipe == Recipe.LOOP_RAMP -> "ramp ${a.bpm.roundToInt()} → ${(a.bpm / speed).roundToInt()} BPM"
            else -> "${a.bpm.roundToInt()} → ${b.bpm.roundToInt()} BPM"
        }
        val where = when (c.exitBar) {
            a.outroStartBar -> "at the outro"
            in a.dropBars -> "at the breakdown"
            else -> "at bar ${c.exitBar + 1}"
        }
        val into = when {
            c.recipe == Recipe.VOCAL_SWAP -> " over B's vocal"
            c.recipe == Recipe.BASS_DROP -> " onto B's drop"
            c.entryBar != firstPhrase -> " into B's drop"
            else -> ""
        }
        val pitch = if (c.recipe.cut) 0 else shift
        val keyText = if (pitch != 0) " · key ${if (pitch > 0) "+" else ""}$pitch st" else ""
        return Transition(
            recipe = c.recipe,
            startSec = built.startSec,
            exitBar = c.exitBar,
            exitSec = a.barTime(c.exitBar),
            endSec = built.endSec,
            entryBar = c.entryBar,
            entrySec = b.barTime(c.entryBar),
            tempoMultiple = multiple,
            beatmatched = beatmatched,
            aPlan = built.aPlan,
            bPlan = built.bPlan,
            aSpeedRamp = built.speedRamp,
            handoverSec = built.handoverSec,
            bPitchSemis = pitch,
            phases = built.phases,
            description = "${c.recipe.label} $where$into · $tempoText$keyText",
        )
    }

    /**
     * The smallest pitch shift (±2 semitones) that puts B's key next to A's on the Camelot wheel,
     * and whether the keys then fit. Unclear keys are treated as fitting rather than guessed.
     */
    fun keyShift(a: TrackInfo, aPitchSemis: Int, b: TrackInfo): Pair<Int, Boolean> {
        if (a.keyConfidence < KEY_CONFIDENT || b.keyConfidence < KEY_CONFIDENT) return 0 to true
        val aPc = a.keyPc + aPitchSemis
        val best = (-2..2).minBy { s -> Camelot.distance(aPc, a.keyMinor, b.keyPc + s, b.keyMinor) * 10 + abs(s) }
        return best to (Camelot.distance(aPc, a.keyMinor, b.keyPc + best, b.keyMinor) <= 1)
    }

    private const val KEY_CONFIDENT = 0.25f

    /** Mean vocalness for a phrase to count as sung. */
    private const val VOCAL = 0.55

    /** B may be entered anywhere in its first 40%. */
    private const val MAX_ENTRY_SHARE = 0.4

    /** Score lost per minute of B skipped by entering it late. */
    private const val SKIP_COST_PER_MIN = 0.4

    /** How much of B a vocal swap may skip to reach B's first sung phrase. */
    private const val MAX_VOCAL_SKIP_SEC = 45.0

    /** How close to the best score a style must be to be picked for variety. */
    private const val VARIETY_MARGIN = 0.8

    /** For each of [recipes] that fits somewhere, its best (entry, exit). */
    private fun search(
        recipes: List<Pair<Recipe, Double>>,
        a: TrackInfo,
        aEntryBar: Int,
        b: TrackInfo,
        entries: List<Int>,
        firstPhrase: Int,
        steadyEntry: Int?,
        firstBar: Int,
        lastBar: Int,
        rampBars: Int,
        beatmatched: Boolean,
    ): List<Candidate> = recipes.mapNotNull { (recipe, recipeScore) ->
        var best: Candidate? = null
        run {
            for (entry in entries) {
                val mix = mixBarsFor(recipe, beatmatched, b, entry)
                val pre = if (recipe == Recipe.LOOP_RAMP) rampBars else recipe.preBars
                val exits = if (recipe.cut) firstBar + pre..lastBar - recipe.tailBars else firstBar..lastBar - mix
                for (exit in exits) {
                    if (!a.isPhraseStart(exit)) continue
                    // An overlap needs both tracks at their main tempo for its whole length,
                    // or the beat grids drift apart (a half-time intro, a tempo switch).
                    if (!recipe.cut && mix > 0) {
                        if (!a.steadyIn(a.barTime(exit), a.barTime(exit + mix))) continue
                        if (!b.steadyIn(b.barTime(entry), b.barTime(entry + mix))) continue
                    }
                    val score = recipeScore + exitScore(a, aEntryBar, exit, recipe, mix) +
                        entryScore(b, entry, firstPhrase, steadyEntry, recipe, mix)
                    if (score > (best?.score ?: Double.NEGATIVE_INFINITY)) best = Candidate(recipe, entry, exit, mix, score)
                }
            }
        }
        best
    }

    /**
     * Trickster dressing ([SfxDressing]): a build on A into the moment B takes over, then a hit
     * on B's first beat (or its drop), both picked by style and sized to the music there.
     */
    private fun withSoundEffects(raw: RecipeBuilder.Built, a: TrackInfo, b: TrackInfo, c: Candidate, entrySec: Double): RecipeBuilder.Built {
        val exit = a.barTime(c.exitBar)
        // B's big moment: its drop for a bass drop, else its first beat.
        val bHit = if (c.recipe == Recipe.BASS_DROP) b.barTime(b.introEndBar) else entrySec
        val aHit = if (c.recipe == Recipe.BASS_DROP) exit + (c.mixBars - 1) * a.barSec else exit
        // Sized to the moment: as long a build as A has room for, as loud as the music there.
        val turn = Math.floorMod((a.uri + b.uri).hashCode(), 3)
        val aHitBar = a.barAt(aHit + 0.01)
        val buildBars = if (c.recipe.cut) SfxDressing.buildBars(a, aHitBar).coerceAtMost(4) else 4
        val build = SfxDressing.build(a, aHitBar, buildBars, turn)
        val hits = SfxDressing.hit(b, b.barAt(bHit + 0.01), turn)
        return RecipeBuilder.Built(
            raw.aPlan.withEvents(build),
            raw.bPlan.withEvents(hits),
            raw.startSec, raw.endSec, raw.phases, raw.speedRamp, handoverSec = raw.handoverSec,
        )
    }

    private fun mixBarsFor(recipe: Recipe, beatmatched: Boolean, b: TrackInfo, entry: Int): Int = when {
        recipe.cut -> 0
        // Lead-in bars up to B's drop, plus the bar after it.
        recipe == Recipe.BASS_DROP -> (b.introEndBar - entry).let { if (it == 8 || it == 16) it + 1 else 0 }
        recipe == Recipe.VOCAL_SWAP -> 12
        // Different tempos can only overlap briefly; matched ones get a full 8-bar filter fade.
        !beatmatched -> 4
        recipe == Recipe.FILTER_FADE -> 8
        recipe == Recipe.LOFI_FADE -> 8
        quietBars(b, entry) >= 16 -> 12
        else -> 8
    }

    /** Bars from [from] before the track gets loud. */
    private fun quietBars(t: TrackInfo, from: Int): Int {
        var n = 0
        while (from + n < t.barCount && t.barEnergy[from + n] < LOUD) n++
        return n
    }

    private fun meanEnergy(t: TrackInfo, from: Int, to: Int): Double {
        val lo = from.coerceIn(0, t.barCount)
        val hi = to.coerceIn(lo, t.barCount)
        return if (hi == lo) 0.0 else (lo until hi).sumOf { t.barEnergy[it].toDouble() } / (hi - lo)
    }

    private fun exitScore(a: TrackInfo, aEntryBar: Int, exit: Int, recipe: Recipe, mix: Int): Double {
        // When B takes over: the cut itself, or halfway through a blend.
        val handover = when {
            recipe.cut -> exit
            recipe == Recipe.VOCAL_SWAP -> exit + 6
            recipe == Recipe.BASS_DROP -> exit + mix - 1
            else -> exit + mix / 2
        }
        val played = a.barTime(handover) - a.barTime(aEntryBar)
        val available = a.durationSec - a.barTime(aEntryBar)
        if (played < min(MIN_PLAY_SEC, 0.6 * available)) return Double.NEGATIVE_INFINITY
        var s = -abs(played - TARGET_PLAY_SEC) / 45
        if (exit == a.outroStartBar || exit in a.dropBars) s += 1.2
        val before = meanEnergy(a, exit - 8, exit)
        val after = meanEnergy(a, exit, exit + 8)
        if (recipe.cut) {
            s += (before - 0.6) * 1.5 // end a loud phrase with a bang
        } else if (recipe == Recipe.VOCAL_SWAP) {
            s += (after - 0.75) * 2 // B's vocal needs A's full beat underneath
            s += (1 - a.vocalIn(exit, exit + 8)) * 0.8 // cleaner still if A is instrumental there
        } else {
            s += (before - after) * 2 // blend over a stretch where A calms down
            s -= max(0.0, after - 0.75) * 2
        }
        // Leaving before the main section has had its 16 bars feels premature.
        if (exit < a.introEndBar + 16 && a.barCount > a.introEndBar + 32) s -= 1.0
        return s
    }

    private fun entryScore(b: TrackInfo, entry: Int, firstPhrase: Int, steadyEntry: Int?, recipe: Recipe, mix: Int): Double {
        if (b.durationSec - b.barTime(entry) < MIN_B_REMAINING_SEC) return Double.NEGATIVE_INFINITY
        val atDrop = entry != firstPhrase
        val skipped = (b.barTime(entry) - b.barTime(firstPhrase)) / 60
        if (recipe == Recipe.BASS_DROP) {
            // Valid only when B's drop lands 8 or 16 bars after B starts.
            // An 8-bar lead-in gets B going sooner than a 16-bar one.
            return when (mix) {
                9 -> 1.2
                17 -> 0.4
                else -> Double.NEGATIVE_INFINITY
            }
        }
        if (recipe == Recipe.VOCAL_SWAP) {
            // A sung phrase landing on the other track's beat is the showpiece move when it's available.
            return if (b.vocalIn(entry, entry + 8) >= VOCAL) 1.8 - 0.2 * skipped else Double.NEGATIVE_INFINITY
        }
        if (!recipe.cut) {
            // A blend can come in anywhere B runs at its matched tempo (the search only offers
            // such phrases), preferably over a quieter stretch, with a small cost for what it
            // skips. If B opens at another tempo, skipping that intro costs nothing extra.
            val introElsewhere = steadyEntry != null && steadyEntry != firstPhrase && entry <= steadyEntry
            val quiet = if (quietBars(b, entry) < mix / 2) -1.5 else 0.3
            return quiet - if (introElsewhere) 0.0 else SKIP_COST_PER_MIN * skipped
        }
        val loudAtEntry = b.energyIn(entry, entry + 4) >= 0.8
        return when {
            !atDrop -> 0.2
            // Dropping B in only lands if B is in full swing there.
            recipe in DROP_RECIPES && loudAtEntry -> 0.8 - 0.3 * skipped
            loudAtEntry -> -0.3 * skipped
            // Cutting into the middle of a quiet stretch is the weakest entry of all.
            else -> -0.8 - 0.3 * skipped
        }
    }

    /** Cuts that hit hardest when B comes in at full energy. */
    private val DROP_RECIPES = setOf(Recipe.BACKSPIN_DROP, Recipe.LOOP_RAMP, Recipe.GATE_BUILD, Recipe.ECHO_OUT)
}
