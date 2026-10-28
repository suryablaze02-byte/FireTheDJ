package com.autodj.dj.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Crossfade gain laws. */
enum class Law {
    LINEAR,
    /** sin/cos: two decks crossing keep the same total power. */
    CONSTANT_POWER,
    /** x²: slow start, fast finish. */
    POWER,
}

/** One automation move from [from] to [to] between [start] and [end] (track seconds). */
class Ramp(val start: Double, val end: Double, val from: Float, val to: Float, val law: Law = Law.LINEAR) {
    fun value(t: Double): Float {
        if (t <= start) return from
        if (t >= end) return to
        val x = ((t - start) / (end - start)).toFloat()
        val shaped = when (law) {
            Law.LINEAR -> x
            Law.CONSTANT_POWER -> if (to >= from) sin(x * PI / 2).toFloat() else (1 - cos(x * PI / 2)).toFloat()
            Law.POWER -> x * x
        }
        return from + (to - from) * shaped
    }
}

/** A parameter over time: holds the first ramp's start value before it, then follows the ramps. */
class Lane(ramps: List<Ramp>, private val default: Float) {
    private val ramps = ramps.sortedBy { it.start }

    fun at(t: Double): Float {
        if (ramps.isEmpty()) return default
        if (t < ramps[0].start) return ramps[0].from
        var current = ramps[0]
        for (r in ramps) if (r.start <= t) current = r else break
        return current.value(t)
    }

    fun movingAt(t: Double) = ramps.any { t >= it.start && t < it.end && it.from != it.to }

    val hasRamps get() = ramps.isNotEmpty()
}

enum class FxType(val label: String) {
    HIGH_PASS("Filter"),
    LOW_PASS("Filter"),
    ECHO("Echo"),
    REVERB("Reverb"),
    LOOP_ROLL("Loop roll"),
    SCRATCH("Scratch"),
    BACKSPIN("Backspin"),
    GATE("Gate"),
    TREMOLO("Tremolo"),
    FLANGER("Flanger"),
    PHASER("Phaser"),
    LOFI("Lo-fi"),
    VOCAL_CUT("Vocal cut"),
    VOCAL_ISOLATE("Vocal isolate"),

    /** Turntable stop: the platter slows to a halt (pitch falls with it). */
    BRAKE("Brake"),

    /** Silence: the music (and effects) drop out, the gap before a drop. Echo and reverb tails ring on. */
    MUTE("Gap"),

    // Synthesised DJ sound effects, mixed on top of the deck.
    SIREN("Siren"),
    HORN("Air horn"),
    LASER("Laser"),
    RISER("Riser"),
    IMPACT("Impact"),
    SNARE_ROLL("Snare roll"),
    REVERSE_CYMBAL("Reverse cymbal"),
    CRASH("Crash"),
    SUB_DROP("Sub drop"),
    DOWNLIFTER("Downlifter"),
    WHOOSH("Whoosh"),
    GUNSHOT("Gunshot"),

    // Indian regional percussion and reed (see [SfxSynth]).
    DAPPU("Dappu"),
    MARFA("Marfa"),
    DHOL("Dhol"),
    THAVIL("Thavil"),
    NADASWARAM("Nadaswaram"),

    ;

    val isSfx get() = ordinal >= SIREN.ordinal
}

/**
 * One effect switched on between [start] and [end] (track seconds). [from]/[to] ramp its
 * main amount; [p1]/[p2] are effect-specific:
 *
 * | effect | amount | p1 | p2 |
 * |---|---|---|---|
 * | HIGH_PASS / LOW_PASS | cutoff position 0..1 (exponential) | | |
 * | ECHO | send | delay, beats | feedback g |
 * | REVERB | send | RT60, seconds | |
 * | LOOP_ROLL | | loop length, beats | |
 * | SCRATCH | | captured segment, beats | stroke period, beats |
 * | BACKSPIN | | initial reverse speed | |
 * | GATE / TREMOLO | depth | period, beats | |
 * | FLANGER / PHASER | wet mix | LFO rate, Hz | |
 * | LOFI | intensity | lowest bit depth | highest decimation |
 * | VOCAL_CUT | how much centre above ~180 Hz is removed | | |
 * | VOCAL_ISOLATE | blend to centre-only 250 Hz–5 kHz | | |
 * | BRAKE, MUTE | | | |
 * | SIREN, HORN, LASER, RISER, IMPACT, SNARE_ROLL, … | level | rate (siren LFO Hz; laser zaps, gunshots: beats apart) | |
 * | DAPPU, MARFA, DHOL, THAVIL | level | [Drum.FILL], [Drum.HIT] or [Drum.FILL_AND_HIT] | |
 * | NADASWARAM | level | tonic, Hz | 1 = minor key |
 */
class FxEvent(
    val type: FxType,
    val start: Double,
    val end: Double,
    val from: Float = 1f,
    val to: Float = 1f,
    val p1: Float = 0f,
    val p2: Float = 0f,
) {
    fun isActive(t: Double) = t >= start && t < end
    fun amount(t: Double) = from + (to - from) * ((t - start) / (end - start)).toFloat().coerceIn(0f, 1f)
}

/** What a percussion event ([FxType.DAPPU], [FxType.MARFA], [FxType.DHOL], [FxType.THAVIL]) plays, as its p1. */
object Drum {
    /** The groove, then a fill ending on the event's end (the bar line). */
    const val FILL = 0f

    /** Only the accents that land on the bar line: "teen maar" three hits, or one unison hit. */
    const val HIT = 1f

    /** Fill into the bar line, then the accents on it: one bar before, a bar after. */
    const val FILL_AND_HIT = 2f
}

/** Everything one deck does during a transition, timed against that deck's own track. */
class FxPlan(
    val beatSec: Double,
    val downbeatSec: Double,
    /** Deck output gain (the crossfade). */
    val gain: Lane,
    /** Bass EQ: 1 = full, 0 = killed. */
    val bass: Lane,
    val events: List<FxEvent>,
) {
    fun active(type: FxType, t: Double): FxEvent? = events.firstOrNull { it.type == type && it.isActive(t) }

    fun uses(type: FxType) = events.any { it.type == type }

    val hasSfx = events.any { it.type.isSfx }

    fun withEvents(extra: List<FxEvent>) = FxPlan(beatSec, downbeatSec, gain, bass, events + extra)

    companion object {
        val EMPTY = FxPlan(0.5, 0.0, Lane(emptyList(), 1f), Lane(emptyList(), 1f), emptyList())

        /** Exponential cutoff mapping: equal steps of the amount sound like equal steps in pitch. */
        fun highPassHz(amount: Float) = 20.0 * (2000.0 / 20.0).pow(amount.toDouble())
        fun lowPassHz(amount: Float) = 20000.0 * (250.0 / 20000.0).pow(amount.toDouble())
    }
}
