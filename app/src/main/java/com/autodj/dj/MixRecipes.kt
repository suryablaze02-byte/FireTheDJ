package com.autodj.dj

import com.autodj.analysis.Genres
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Law
import com.autodj.dj.fx.Ramp
import com.autodj.model.TrackInfo
import kotlin.math.abs
import kotlin.math.ln

/**
 * The transition styles AutoDJ can perform. Blends overlap the two tracks from the exit
 * point; cuts play effects on A *before* the exit, drop B in on it, and let A's tail ring out.
 */
enum class Recipe(
    val label: String,
    val cut: Boolean,
    val preBars: Int = 0,
    val tailBars: Int = 0,
    /** Plays continuously into B with no audible break: a blend, or a loop ramping into B. */
    val smooth: Boolean = !cut,
) {
    BASS_SWAP_BLEND("Bass-swap blend", cut = false),
    PHASER_BLEND("Phaser blend", cut = false),
    FLANGER_BLEND("Flanger blend", cut = false),
    FILTER_FADE("Filter fade", cut = false),
    LOFI_FADE("Lo-fi fade", cut = false),
    ECHO_OUT("Echo-out", cut = true, preBars = 2, tailBars = 4),
    GATE_BUILD("Gate build & drop", cut = true, preBars = 2, tailBars = 2),
    BACKSPIN_DROP("Backspin drop", cut = true, preBars = 1, tailBars = 1),
    SCRATCH_CUT("Scratch cut", cut = true, preBars = 1, tailBars = 1),

    /** Cross-tempo: loop A's last bars and speed the loop up (or down) to B's tempo, then drop B. */
    LOOP_RAMP("Loop & ramp", cut = true, preBars = 4, tailBars = 1, smooth = true),

    /** A's vocal is cut while B's vocal phrase sings over A's beat; then the beats swap. */
    VOCAL_SWAP("Vocal swap", cut = false),

    /** B's intro rises over A with its bass held back; one bar of no bass; B's drop lands with full bass. */
    BASS_DROP("Bass drop", cut = false),
}

/** The two ways AutoDJ can play a set. */
enum class MixMode(val label: String) {
    /** Long crossfades, bass-drop mixes and speed ramp-ups; no tricks inside tracks. */
    SMOOTH("Smooth"),

    /** Trick-heavy: scratches, rolls, brakes, echo throws and DJ sound effects throughout. */
    TRICKSTER("Trickster"),
}

/** A labelled stretch of the transition timeline, in A's track seconds. */
class Phase(val label: String, val startSec: Double, val endSec: Double)

/**
 * The "AI" choice of transition: every recipe gets a score from how well it suits the genre,
 * the tempo match, the shape of both tracks' arrangements, and what was used recently.
 */
object DecisionEngine {
    private val byFamily: Map<String, Map<Recipe, Double>> = mapOf(
        Genres.HOUSE to mapOf(Recipe.BASS_SWAP_BLEND to 3.0, Recipe.PHASER_BLEND to 2.2, Recipe.ECHO_OUT to 1.6, Recipe.FLANGER_BLEND to 1.2),
        Genres.TECHNO to mapOf(Recipe.BASS_SWAP_BLEND to 2.6, Recipe.FLANGER_BLEND to 2.2, Recipe.GATE_BUILD to 2.2, Recipe.ECHO_OUT to 1.4),
        Genres.TRANCE to mapOf(Recipe.GATE_BUILD to 2.6, Recipe.BASS_SWAP_BLEND to 2.2, Recipe.ECHO_OUT to 2.0, Recipe.FLANGER_BLEND to 1.2),
        Genres.DNB to mapOf(Recipe.ECHO_OUT to 2.6, Recipe.BACKSPIN_DROP to 2.2, Recipe.BASS_SWAP_BLEND to 1.6, Recipe.GATE_BUILD to 1.2),
        Genres.BASS to mapOf(Recipe.BACKSPIN_DROP to 2.6, Recipe.ECHO_OUT to 2.2, Recipe.GATE_BUILD to 1.8, Recipe.SCRATCH_CUT to 1.2),
        Genres.HIPHOP to mapOf(Recipe.SCRATCH_CUT to 2.6, Recipe.BACKSPIN_DROP to 2.2, Recipe.ECHO_OUT to 1.6, Recipe.LOFI_FADE to 1.6),
        Genres.LATIN to mapOf(Recipe.ECHO_OUT to 2.2, Recipe.BASS_SWAP_BLEND to 2.0, Recipe.BACKSPIN_DROP to 1.6),
        Genres.POP to mapOf(Recipe.ECHO_OUT to 2.2, Recipe.BASS_SWAP_BLEND to 2.0, Recipe.BACKSPIN_DROP to 1.4, Recipe.FILTER_FADE to 1.2),
        Genres.CHILL to mapOf(Recipe.PHASER_BLEND to 2.6, Recipe.LOFI_FADE to 2.2, Recipe.FILTER_FADE to 1.6),
        Genres.ROCK to mapOf(Recipe.ECHO_OUT to 2.2, Recipe.FILTER_FADE to 2.0, Recipe.BACKSPIN_DROP to 1.2),
    )

    private val default = mapOf(Recipe.BASS_SWAP_BLEND to 2.0, Recipe.ECHO_OUT to 2.0, Recipe.FILTER_FADE to 1.5)

    /** Blends that need the beats locked for their whole length. */
    private val needsBeatmatch = setOf(Recipe.BASS_SWAP_BLEND, Recipe.PHASER_BLEND, Recipe.FLANGER_BLEND, Recipe.BASS_DROP)

    private val vocalGenres = setOf(Genres.POP, Genres.HOUSE, Genres.HIPHOP, Genres.LATIN)

    /** Smooth mode: continuous blends; a cut is a rare exception, never twice running. */
    private const val SMOOTH_BLEND_BIAS = 1.5
    private const val SMOOTH_CUT_BIAS = -3.0
    private const val CUT_AFTER_CUT = -3.0

    /** Trickster mode: cuts and tricks are the point; a blend only when nothing else fits. */
    private const val TRICK_CUT_BIAS = 1.5
    private const val TRICK_BLEND_BIAS = -1.8
    private const val BLEND_AFTER_BLEND = -1.5

    /**
     * [speed] is the rate B would need to match A (B's beats per A beat already folded in), so
     * A's loop must reach 1/[speed] to land on B's tempo. [keysFit]: the two keys are
     * compatible (after any pitch shift the planner may apply).
     */
    fun scores(
        a: TrackInfo,
        b: TrackInfo,
        beatmatched: Boolean,
        speed: Double,
        history: List<Recipe>,
        keysFit: Boolean = true,
        mode: MixMode = MixMode.SMOOTH,
    ): Map<Recipe, Double> {
        val base = (byFamily[a.genreFamily] ?: default).toMutableMap()
        // Every style stays possible at a low score so a set never gets stuck on one.
        Recipe.entries.forEach { base.putIfAbsent(it, 0.5) }
        val rampTo = 1 / speed
        base[Recipe.LOOP_RAMP] = when {
            beatmatched || abs(rampTo - 1) > TransitionPlanner.MAX_RAMP -> Double.NEGATIVE_INFINITY
            rampTo > 1 -> 2.8 // speeding a loop up sounds like a build
            else -> 1.6 // slowing down works but feels heavier
        }
        base[Recipe.VOCAL_SWAP] = when {
            !beatmatched || !keysFit -> Double.NEGATIVE_INFINITY // two vocals must share tempo and key
            a.genreFamily in vocalGenres -> 3.0
            else -> 2.3
        }
        // Whether B has a drop to land on is checked by the planner when it picks B's entry.
        base[Recipe.BASS_DROP] = if (mode == MixMode.SMOOTH) 3.4 else 1.8
        val crossGenre = a.genreFamily != b.genreFamily
        val entryBar = (0 until 8).first { b.isPhraseStart(it) }
        val bIntroBars = b.introEndBar - entryBar
        val aOutroBars = a.barCount - a.outroStartBar
        val aOutroEnergy = a.barEnergy.getOrElse(a.outroStartBar - 1) { 1f }
        return base.mapValues { (r, s) ->
            var score = s
            if (!beatmatched && r in needsBeatmatch) score = Double.NEGATIVE_INFINITY
            // Long overlaps in clashing keys sound out of tune.
            if (!keysFit && !r.cut) score -= 1.2
            if (!beatmatched && r == Recipe.FILTER_FADE) score += 1.5
            // Two different grooves overlapping clash; switch cleanly instead.
            if (crossGenre) score += if (r.cut) 0.8 else if (r == Recipe.FILTER_FADE) -0.5 else -1.0
            // B comes in loud: there is no intro to blend over, so cut instead.
            if (bIntroBars < 8) score += if (r.cut) 1.0 else -1.5
            // A long, gentle outro is made for blending.
            if (aOutroBars >= 16) score += if (r.cut) -0.3 else 0.8
            // A is still pumping right up to the outro: a dramatic cut lands better.
            if (aOutroEnergy >= 0.85f && r.cut) score += 0.5
            if (history.lastOrNull() == r) score -= 2.5
            else if (r in history.takeLast(4)) score -= 1.5 // a set should keep changing moves
            when (mode) {
                MixMode.SMOOTH -> {
                    score += if (r.smooth) SMOOTH_BLEND_BIAS else SMOOTH_CUT_BIAS
                    if (!r.smooth && history.lastOrNull()?.smooth == false) score += CUT_AFTER_CUT
                }
                MixMode.TRICKSTER -> {
                    val blend = r.smooth && r != Recipe.LOOP_RAMP
                    score += if (blend) TRICK_BLEND_BIAS else TRICK_CUT_BIAS
                    val lastWasBlend = history.lastOrNull()?.let { it.smooth && it != Recipe.LOOP_RAMP } == true
                    if (blend && lastWasBlend) score += BLEND_AFTER_BLEND
                }
            }
            score + jitter(a, b, r)
        }
    }


    /** Small, repeatable variety: the same pair always gets the same choice. */
    private fun jitter(a: TrackInfo, b: TrackInfo, r: Recipe): Double =
        ((a.uri + b.uri + r.name).hashCode() and 0xffff) / 65535.0 * 0.3
}

/** Builds both decks' automation for a recipe. A's times are A track seconds, B's are B's. */
internal class RecipeBuilder(
    private val a: TrackInfo,
    private val b: TrackInfo,
    /** A time at which B starts playing. */
    private val exitSec: Double,
    private val entrySec: Double,
    tempoMultiple: Double,
    beatmatched: Boolean,
) {
    class Built(
        val aPlan: FxPlan,
        val bPlan: FxPlan,
        val startSec: Double,
        val endSec: Double,
        val phases: List<Phase>,
        /** Deck A playback speed over A time, for tempo ramps. */
        val speedRamp: Ramp? = null,
        /** A time when B is fully in and A's dry signal is gone (effect tails may ring on). */
        val handoverSec: Double = endSec,
    )

    private val bar = a.barSec
    private val beat = a.beatSec
    private val ratio = if (beatmatched) tempoMultiple * b.beatSec / a.beatSec else 1.0

    /** The B track time that plays alongside A track time [aSec]. */
    private fun toB(aSec: Double) = entrySec + (aSec - exitSec) * ratio

    private fun aPlan(gain: List<Ramp>, bass: List<Ramp>, events: List<FxEvent>) =
        FxPlan(a.beatSec, a.downbeatSec, Lane(gain, 1f), Lane(bass, 1f), events)

    private fun bPlan(gain: List<Ramp>, bass: List<Ramp>, events: List<FxEvent>) =
        FxPlan(b.beatSec, b.downbeatSec, Lane(gain, 1f), Lane(bass, 1f), events)

    /** B comes in on the exit point and crossfades up over a bar while A's dry signal fades out. */
    private fun bDropIn(extra: List<FxEvent> = emptyList()) =
        bPlan(listOf(Ramp(entrySec, entrySec + b.barSec, 0f, 1f, Law.CONSTANT_POWER)), emptyList(), extra)

    /** A's dry signal crossfades out over a bar from [at] (effect tails keep ringing). */
    private fun aOutAt(at: Double) = listOf(Ramp(at, at + bar, 1f, 0f, Law.CONSTANT_POWER))

    fun build(recipe: Recipe, mixBars: Int, rampBars: Int = 8, rampTo: Double = 1.0): Built {
        val built = recipeFor(recipe, mixBars, rampBars, rampTo)
        // Keep A running (silent, dry) until its echo and reverb tails have died away, so the
        // deck never stops mid-tail. A track that ends sooner simply ends the transition then.
        val tailEnd = built.aPlan.events.maxOfOrNull { e ->
            when (e.type) {
                FxType.ECHO -> e.end + e.p1 * beat * ln(0.001) / ln(e.p2.coerceIn(0.05f, 0.95f).toDouble())
                FxType.REVERB -> e.end + e.p1
                else -> e.end
            }
        } ?: built.endSec
        val end = maxOf(built.endSec, minOf(tailEnd, a.durationSec - 0.1))
        return Built(built.aPlan, built.bPlan, built.startSec, end, built.phases, built.speedRamp, handoverSec = built.handoverSec)
    }

    private fun recipeFor(recipe: Recipe, mixBars: Int, rampBars: Int, rampTo: Double): Built = when (recipe) {
        Recipe.LOOP_RAMP -> loopRamp(rampBars, rampTo)
        Recipe.VOCAL_SWAP -> vocalSwap()
        Recipe.BASS_DROP -> bassDrop(mixBars - 1)
        Recipe.BASS_SWAP_BLEND, Recipe.PHASER_BLEND, Recipe.FLANGER_BLEND -> blend(recipe, mixBars)
        Recipe.FILTER_FADE -> filterFade(mixBars)
        Recipe.LOFI_FADE -> lofiFade(mixBars)
        Recipe.ECHO_OUT -> echoOut()
        Recipe.GATE_BUILD -> gateBuild()
        Recipe.BACKSPIN_DROP -> backspin()
        Recipe.SCRATCH_CUT -> scratch()
    }

    /**
     * The crossfade under every blend: across exactly [e, end], A goes 100% → 0% while B goes
     * 0% → 100% on matching equal-power curves (both ~71% at the midpoint), so one song hands
     * over to the other steadily with no dip, jump or wait. Effects only decorate this.
     */
    private fun crossfade(e: Double, end: Double): Pair<List<Ramp>, List<Ramp>> =
        listOf(Ramp(e, end, 1f, 0f, Law.CONSTANT_POWER)) to listOf(Ramp(toB(e), toB(end), 0f, 1f, Law.CONSTANT_POWER))

    /** Crossfade with B's lows trimmed for the first quarter, and the basslines swapped at the midpoint. */
    private fun blend(recipe: Recipe, mixBars: Int): Built {
        val e = exitSec
        val mid = e + mixBars / 2 * bar
        val end = e + mixBars * bar
        val (aGain, bGain) = crossfade(e, end)
        val aEvents = when (recipe) {
            Recipe.PHASER_BLEND -> listOf(
                FxEvent(FxType.PHASER, mid, end, 0f, 0.8f, p1 = 0.3f),
                FxEvent(FxType.TREMOLO, end - 2 * bar, end, 0f, 0.4f, p1 = 0.5f),
            )
            Recipe.FLANGER_BLEND -> listOf(FxEvent(FxType.FLANGER, mid, end, 0f, 0.7f, p1 = 0.25f))
            else -> emptyList()
        }
        val aOut = aPlan(
            gain = aGain,
            bass = listOf(Ramp(mid - beat / 2, mid + beat / 2, 1f, 0f)),
            events = aEvents,
        )
        // Only B's lows are trimmed, and only at first, so its music is heard straight away.
        val bIn = bPlan(
            gain = bGain,
            bass = listOf(Ramp(toB(mid - beat / 2), toB(mid + beat / 2), 0f, 1f)),
            events = listOf(FxEvent(FxType.HIGH_PASS, toB(e), toB(e + (mid - e) / 2), 0.4f, 0f)),
        )
        val outLabel = when (recipe) {
            Recipe.PHASER_BLEND -> "Phaser out"
            Recipe.FLANGER_BLEND -> "Flanger out"
            else -> "Fade out"
        }
        return Built(
            aOut, bIn, e, end,
            listOf(
                Phase("Filter in", e, mid),
                Phase("Bass swap", mid, mid + 2 * bar),
                Phase(outLabel, mid + 2 * bar, end),
            ),
        )
    }

    /** Different tempos: A sinks under a low-pass into reverb while B opens up from a high-pass. */
    private fun filterFade(mixBars: Int): Built {
        val e = exitSec
        val mid = e + mixBars / 2.0 * bar
        val end = e + mixBars * bar
        val (aGain, bGain) = crossfade(e, end)
        val aOut = aPlan(
            gain = aGain,
            bass = emptyList(),
            events = listOf(
                FxEvent(FxType.LOW_PASS, e, end, 0f, 0.85f),
                FxEvent(FxType.REVERB, mid, end, 0f, 0.6f, p1 = 3.0f),
            ),
        )
        val bIn = bPlan(
            gain = bGain,
            bass = emptyList(),
            events = listOf(FxEvent(FxType.HIGH_PASS, toB(e), toB(mid), 0.45f, 0f)),
        )
        return Built(aOut, bIn, e, end, listOf(Phase("Filters cross", e, mid), Phase("Reverb wash", mid, end)))
    }

    /** A degrades into a crushed, darker version of itself as B comes in. */
    private fun lofiFade(mixBars: Int): Built {
        val e = exitSec
        val mid = e + mixBars / 2.0 * bar
        val end = e + mixBars * bar
        val (aGain, bGain) = crossfade(e, end)
        val aOut = aPlan(
            gain = aGain,
            bass = emptyList(),
            events = listOf(
                FxEvent(FxType.LOFI, e, end, 0f, 1f, p1 = 6f, p2 = 8f),
                FxEvent(FxType.LOW_PASS, mid, end, 0f, 0.6f),
            ),
        )
        val bIn = bPlan(
            gain = bGain,
            bass = emptyList(),
            events = listOf(FxEvent(FxType.HIGH_PASS, toB(e), toB(e + (mid - e) / 2), 0.4f, 0f)),
        )
        return Built(aOut, bIn, e, end, listOf(Phase("Lo-fi crush", e, mid), Phase("Fade out", mid, end)))
    }

    /** A's last bar rolls tighter while echoes build; A drops out on the one, B slams in, echoes ring on. */
    private fun echoOut(): Built {
        val p = exitSec
        val aOut = aPlan(
            gain = aOutAt(p),
            bass = listOf(Ramp(p - bar, p - bar + beat, 1f, 0f)),
            events = listOf(
                FxEvent(FxType.ECHO, p - 2 * bar, p, 0f, 1f, p1 = 0.75f, p2 = 0.55f),
                FxEvent(FxType.LOOP_ROLL, p - bar, p - bar / 2, p1 = 1f),
                FxEvent(FxType.LOOP_ROLL, p - bar / 2, p, p1 = 0.5f),
                FxEvent(FxType.REVERB, p - bar, p, 0f, 0.5f, p1 = 2.4f),
            ),
        )
        val bIn = bDropIn(listOf(FxEvent(FxType.HIGH_PASS, entrySec, toB(p + bar), 0.5f, 0f)))
        return Built(
            aOut, bIn, p - 2 * bar, p + 4 * bar,
            listOf(Phase("Echo", p - 2 * bar, p - bar), Phase("Roll", p - bar, p), Phase("Crossfade · echo tail", p, p + bar)),
            handoverSec = p + bar,
        )
    }

    /** Two bars of tightening gate and rising high-pass build tension, then B crossfades in. */
    private fun gateBuild(): Built {
        val p = exitSec
        val aOut = aPlan(
            gain = aOutAt(p),
            bass = emptyList(),
            events = listOf(
                FxEvent(FxType.GATE, p - 2 * bar, p - bar, 0.4f, 1f, p1 = 0.5f),
                FxEvent(FxType.GATE, p - bar, p, 1f, 1f, p1 = 0.25f),
                FxEvent(FxType.HIGH_PASS, p - 2 * bar, p, 0f, 0.7f),
                FxEvent(FxType.REVERB, p - bar, p, 0f, 0.8f, p1 = 3.0f),
            ),
        )
        return Built(
            aOut, bDropIn(), p - 2 * bar, p + 2 * bar,
            listOf(Phase("Gate 1/8", p - 2 * bar, p - bar), Phase("Gate 1/16", p - bar, p), Phase("Crossfade", p, p + bar)),
            handoverSec = p + bar,
        )
    }

    /** A's platter is spun backwards to a stop; B drops on the downbeat. */
    private fun backspin(): Built {
        val p = exitSec
        val aOut = aPlan(
            gain = aOutAt(p),
            bass = emptyList(),
            events = listOf(
                FxEvent(FxType.BACKSPIN, p - 2 * beat, p, p1 = 3f),
                FxEvent(FxType.REVERB, p - 2 * beat, p, 0.3f, 0.6f, p1 = 1.8f),
            ),
        )
        return Built(
            aOut, bDropIn(), p - 2 * beat, p + bar,
            listOf(Phase("Backspin", p - 2 * beat, p), Phase("Crossfade", p, p + bar)),
            handoverSec = p + bar,
        )
    }

    /**
     * Captures A's bar before the ramp and loops it while the deck's speed ramps to B's tempo
     * (key-locked). The loop halves as it goes (1 bar → 2 → 1 → ½ beat) under a rising high-pass,
     * so the stretch sounds like a build; B drops on the loop's downbeat at its own tempo.
     */
    private fun loopRamp(rampBars: Int, rampTo: Double): Built {
        val p = exitSec
        val s = p - rampBars * bar
        val half = p - rampBars / 2.0 * bar
        val aOut = aPlan(
            gain = aOutAt(p),
            bass = listOf(Ramp(p - bar, p - bar + beat, 1f, 0.2f)),
            events = listOf(
                FxEvent(FxType.LOOP_ROLL, s, half, p1 = 4f),
                FxEvent(FxType.LOOP_ROLL, half, p - bar, p1 = 2f),
                FxEvent(FxType.LOOP_ROLL, p - bar, p - bar / 2, p1 = 1f),
                FxEvent(FxType.LOOP_ROLL, p - bar / 2, p, p1 = 0.5f),
                FxEvent(FxType.HIGH_PASS, s, p, 0f, 0.75f),
                FxEvent(FxType.REVERB, p - bar, p, 0f, 0.6f, p1 = 2.0f),
            ),
        )
        val bIn = bDropIn(listOf(FxEvent(FxType.HIGH_PASS, entrySec, entrySec + bar, 0.4f, 0f)))
        // The ramp finishes a bar early so the last bar already runs at B's tempo.
        val speed = Ramp(s + bar, p - bar, 1f, rampTo.toFloat())
        return Built(
            aOut, bIn, s, p + bar,
            listOf(Phase("Loop", s, s + bar), Phase("Ramp → B tempo", s + bar, p - bar), Phase("Roll", p - bar, p), Phase("Crossfade", p, p + bar)),
            speedRamp = speed,
            handoverSec = p + bar,
        )
    }

    /**
     * 8 bars of B's vocal (centre, vocal band only) over A's beat with A's own vocal cut out,
     * then the beats swap: bass lines change over and B opens to full range. It is still one
     * continuous crossfade: B is heard (vocal-forward, music underneath) from the first beat and
     * rises, while A, its own vocal cut, eases down across the whole 16 bars.
     */
    private fun vocalSwap(): Built {
        val e = exitSec
        val swap = e + 6 * bar
        val end = e + 12 * bar
        val (aGain, bGain) = crossfade(e, end)
        val aOut = aPlan(
            gain = aGain,
            bass = listOf(Ramp(swap - beat / 2, swap + beat / 2, 1f, 0f)),
            events = listOf(
                FxEvent(FxType.VOCAL_CUT, e, e + 2 * bar, 0f, 1f),
                FxEvent(FxType.VOCAL_CUT, e + 2 * bar, end, 1f, 1f),
            ),
        )
        // Vocal emphasised, not isolated: B's own music stays audible underneath its singer.
        val bIn = bPlan(
            gain = bGain,
            bass = listOf(Ramp(toB(swap - beat / 2), toB(swap + beat / 2), 0f, 1f)),
            events = listOf(
                FxEvent(FxType.VOCAL_ISOLATE, toB(e), toB(e + 4 * bar), 0.5f, 0.5f),
                FxEvent(FxType.VOCAL_ISOLATE, toB(e + 4 * bar), toB(swap), 0.5f, 0f),
            ),
        )
        return Built(
            aOut, bIn, e, end,
            listOf(Phase("A vocal out · B vocal in", e, swap), Phase("Beat swap", swap, swap + 2 * bar), Phase("A out", swap + 2 * bar, end)),
        )
    }

    /**
     * [lead] bars of B's intro (lows filtered off) rise over A while A eases down under a rising
     * high-pass; in the last bar both basslines are gone (tension); B's drop lands on [exitSec] +
     * [lead] bars with its full bass.
     */
    private fun bassDrop(lead: Int): Built {
        val e = exitSec
        val d = e + lead * bar
        val end = d + bar
        // Plain crossfade over the lead-in, landing exactly on B's drop.
        val (aGain, bGain) = crossfade(e, d)
        val aOut = aPlan(
            gain = aGain,
            bass = listOf(Ramp(d - bar, d - bar + beat / 2, 1f, 0f)),
            events = listOf(FxEvent(FxType.HIGH_PASS, d - 4 * bar, d, 0f, 0.5f)),
        )
        // B's lows trimmed only for the first half of the lead-in; its bass proper lands on the drop.
        val bIn = bPlan(
            gain = bGain,
            bass = listOf(Ramp(toB(d) - 0.01, toB(d), 0f, 1f)),
            events = listOf(FxEvent(FxType.HIGH_PASS, toB(e), toB(e + lead / 2.0 * bar), 0.4f, 0f)),
        )
        return Built(
            aOut, bIn, e, end,
            listOf(Phase("Crossfade", e, d - bar), Phase("No bass", d - bar, d), Phase("Drop", d, end)),
            handoverSec = d,
        )
    }

    /** Grab a beat of A, scratch it back and forth for the rest of the bar, then cut to B. */
    private fun scratch(): Built {
        val p = exitSec
        val aOut = aPlan(
            gain = aOutAt(p),
            bass = listOf(Ramp(p - bar, p - bar + beat / 2, 1f, 0.3f)),
            events = listOf(FxEvent(FxType.SCRATCH, p - bar, p, p1 = 1f, p2 = 0.5f)),
        )
        return Built(
            aOut, bDropIn(), p - bar, p + bar,
            listOf(Phase("Catch", p - bar, p - bar + beat), Phase("Scratch", p - bar + beat, p), Phase("Crossfade", p, p + bar)),
            handoverSec = p + bar,
        )
    }
}
