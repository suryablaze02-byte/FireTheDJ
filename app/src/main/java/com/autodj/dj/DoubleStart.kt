package com.autodj.dj

import com.autodj.analysis.Camelot
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Law
import com.autodj.dj.fx.Ramp
import com.autodj.model.TrackInfo
import kotlin.math.abs
import kotlin.random.Random

/**
 * The Double start, a set opener: two songs at the same tempo start together, timed so their
 * drops land on the same beat (a double drop). Until the drop both play at full level, B with no
 * bass and no vocal (in Trickster B cuts out and back in on the bar); on the drop the bass swaps to B; 16 bars on, A's vocal is long
 * gone and A filters out over 8 bars. B is then the set's second song, and the set carries on.
 */
object DoubleStart {
    /** The two tempos may differ by this much at most, so nothing is audibly stretched. */
    const val MAX_TEMPO_GAP = 0.003

    /** Phrases (8 bars) of run-up both songs play together before the drop: at least 2, at most 4. */
    const val MIN_PHRASES = 2
    const val MAX_PHRASES = 4

    /** Bars both drops play together, then the bars A takes to filter out. */
    const val RIDE_BARS = 16
    const val OUT_BARS = 8

    /** Trickster: B drops out for the last bar of every 4 in the run-up, then comes straight back. */
    const val WEAVE_BARS = 4

    /** Trickster: a riser and a filter sweep on B over this many bars before the drop. */
    const val TRICK_RISE_BARS = 4

    /** A pair that can open together: where each starts, and the bar each one drops on. */
    class Pair(val a: TrackInfo, val b: TrackInfo, val aStartBar: Int, val bStartBar: Int, val aDropBar: Int, val bDropBar: Int) {
        val runUpBars get() = aDropBar - aStartBar
    }

    /**
     * The song's first drop: a phrase start after its intro where four quiet bars give way to
     * four loud ones (the rule the roll build uses), or -1.
     */
    fun firstDrop(t: TrackInfo): Int =
        ((t.introEndBar + 1) until t.outroStartBar)
            .firstOrNull { t.isPhraseStart(it) && t.energyIn(it - 4, it) < 0.65 && t.energyIn(it, it + 4) >= 0.85 } ?: -1

    /** [a] and [b] as a Double start, or null when they don't fit. */
    fun pair(a: TrackInfo, b: TrackInfo): Pair? = check(a, b) as? Pair

    /** [a] and [b] as a [Pair], or the reason they don't fit (for the log). */
    fun check(a: TrackInfo, b: TrackInfo): Any {
        if (a === b || a.bpm <= 0 || b.bpm <= 0) return "no tempo"
        if (abs(b.bpm / a.bpm - 1) > MAX_TEMPO_GAP) return "tempo"
        if (a.tempoUnsure || b.tempoUnsure) return "unsure tempo"
        if (a.genreFamily != b.genreFamily) return "genre"
        if (Camelot.distance(a.keyPc, a.keyMinor, b.keyPc, b.keyMinor) > 1) return "key"
        val dA = firstDrop(a)
        val dB = firstDrop(b)
        if (dA < 0 || dB < 0) return "no clear drop"
        // Whole phrases back from each drop, as far as both songs' music allows.
        val phrases = minOf((dA - a.musicStartBar) / 8, (dB - b.musicStartBar) / 8, MAX_PHRASES)
        if (phrases < MIN_PHRASES) return "run-up too short"
        val aStart = dA - phrases * 8
        val bStart = dB - phrases * 8
        // A must last through the ride and its filter out; B must have a song left after it.
        if (dA + RIDE_BARS + OUT_BARS > minOf(a.outroStartBar, a.musicEndBar)) return "A ends too soon"
        if (dB + RIDE_BARS + OUT_BARS + 16 > b.musicEndBar) return "B ends too soon"
        // Both beats must hold steady over the layered stretch, or the two would drift apart.
        val aSpan = a.barTime(aStart) to a.barTime(dA + RIDE_BARS + OUT_BARS)
        val bSpan = b.barTime(bStart) to b.barTime(dB + RIDE_BARS + OUT_BARS)
        if (!a.steadyIn(aSpan.first, aSpan.second) || !b.steadyIn(bSpan.first, bSpan.second)) return "tempo drifts"
        return Pair(a, b, aStart, bStart, dA, dB)
    }

    /**
     * Why no Double start was found among [openers] and [tracks]: how many pairs each rule
     * stopped (the first rule a pair fails), and the closest tempos, for the log.
     */
    fun whyNot(openers: List<TrackInfo>, tracks: List<TrackInfo>): String {
        val reasons = openers.flatMap { a -> tracks.filter { it !== a }.map { b -> check(a, b) } }
            .filterIsInstance<String>().groupingBy { it }.eachCount()
        val closest = openers.flatMap { a -> tracks.filter { it !== a && it.bpm > 0 && a.bpm > 0 }.map { b -> a to b } }
            .minByOrNull { (a, b) -> abs(b.bpm / a.bpm - 1) }
            ?.let { (a, b) -> "closest \"${a.title}\" %.1f + \"${b.title}\" %.1f BPM: ${check(a, b) as? String ?: "fits"}".format(a.bpm, b.bpm) }
        val drops = tracks.count { firstDrop(it) >= 0 }
        return "${openers.size} openers × ${tracks.size} songs · " +
            reasons.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" } +
            " · $drops songs with a clear drop · ${closest ?: "no tempos"}"
    }

    /**
     * A Double start from [openers] (the songs the set may open with) and a partner from
     * [tracks], or null when no two fit. Same-key pairs are preferred; among those, one at random.
     */
    fun find(openers: List<TrackInfo>, tracks: List<TrackInfo>, random: Random = Random.Default): Pair? {
        val pairs = openers.flatMap { a -> tracks.mapNotNull { b -> pair(a, b) } }
        if (pairs.isEmpty()) return null
        val best = pairs.minOf { keyDistance(it) }
        return pairs.filter { keyDistance(it) == best }.random(random)
    }

    private fun keyDistance(p: Pair) = Camelot.distance(p.a.keyPc, p.a.keyMinor, p.b.keyPc, p.b.keyMinor)

    /** The opener as a [Transition] the engine runs like any other mix, with B playing from A's first beat. */
    fun plan(p: Pair, mode: MixMode): Transition {
        val a = p.a
        val b = p.b
        val bar = a.barSec
        val beat = a.beatSec
        val t0 = a.barTime(p.aStartBar)
        val drop = a.barTime(p.aDropBar)
        val rideEnd = drop + RIDE_BARS * bar
        val end = rideEnd + OUT_BARS * bar
        val entry = b.barTime(p.bStartBar)
        // B runs at A's tempo, so B's track time moves at this rate against A's.
        val ratio = b.beatSec / a.beatSec
        fun toB(x: Double) = entry + (x - t0) * ratio
        val click = 0.03

        val aPlan = FxPlan(
            a.beatSec, a.downbeatSec,
            Lane(listOf(Ramp(rideEnd, end, 1f, 0f, Law.CONSTANT_POWER)), 1f),
            // The bass swaps on the downbeat of the drop: A's goes as B's arrives.
            Lane(listOf(Ramp(drop - click, drop, 1f, 0f)), 1f),
            listOf(
                // A's voice makes way for B's over the beat into the drop.
                FxEvent(FxType.VOCAL_CUT, drop - beat, drop, 0f, 1f),
                FxEvent(FxType.VOCAL_CUT, drop, end, 1f, 1f),
                FxEvent(FxType.HIGH_PASS, rideEnd, end, 0f, 0.8f),
            ),
        )
        val trick = mode == MixMode.TRICKSTER
        val rise = drop - TRICK_RISE_BARS * bar
        // Both play at full level from the first beat. Trickster weaves B out and back in, one bar
        // in every four, until the riser takes it into the drop.
        val bGain = buildList {
            add(Ramp(entry, entry + click, 0f, 1f))
            if (trick) {
                var k = WEAVE_BARS - 1
                while (k < p.runUpBars - TRICK_RISE_BARS) {
                    val out = toB(t0 + k * bar)
                    val back = toB(t0 + (k + 1) * bar)
                    add(Ramp(out, out + click, 1f, 0f))
                    add(Ramp(back, back + click, 0f, 1f))
                    k += WEAVE_BARS
                }
            }
        }
        val bEvents = buildList {
            add(FxEvent(FxType.VOCAL_CUT, entry, toB(drop - beat), 1f, 1f))
            add(FxEvent(FxType.VOCAL_CUT, toB(drop - beat), toB(drop), 1f, 0f))
            if (trick) {
                add(FxEvent(FxType.HIGH_PASS, toB(rise), toB(drop), 0.6f, 0f))
                add(FxEvent(FxType.RISER, toB(rise), toB(drop), 0.8f, 0.8f))
            }
        }
        val bPlan = FxPlan(
            b.beatSec, b.downbeatSec,
            Lane(bGain, 1f),
            Lane(listOf(Ramp(toB(drop - click), toB(drop), 0f, 1f)), 1f),
            bEvents,
        )
        return Transition(
            recipe = Recipe.DOUBLE_START,
            startSec = t0,
            exitBar = p.aStartBar,
            exitSec = t0,
            endSec = end,
            // B is the main song from here: its own tricks are planned from where it is at the swap.
            entryBar = p.bDropBar + RIDE_BARS + OUT_BARS,
            entrySec = entry,
            tempoMultiple = 1.0,
            beatmatched = true,
            aPlan = aPlan,
            bPlan = bPlan,
            aSpeedRamp = null,
            handoverSec = end,
            bPitchSemis = 0,
            phases = listOf(
                Phase("Run-up", t0, drop),
                Phase("Double drop", drop, rideEnd),
                Phase("A out", rideEnd, end),
            ),
            description = "Double start · drops together on bar ${p.aDropBar - p.aStartBar + 1}, bass to B",
        )
    }
}
