package com.autodj.dj

import com.autodj.analysis.Genres
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Ramp
import com.autodj.model.TrackInfo
import kotlin.math.max
import kotlin.math.min

/** When the playhead reaches bar [atBar], jump back to bar [toBar] (played again on the other deck). */
class Replay(val atBar: Int, val toBar: Int)

/** A trick shown to the listener, in the track's own seconds. */
class Trick(val label: String, val startSec: Double, val endSec: Double)

/** What AutoDJ does *inside* one track, apart from the transitions at either end. */
class Performance(
    /** Effects in the track's own time. */
    val events: List<FxEvent>,
    /** Absolute deck speed over track time (key-locked). */
    val speed: Lane,
    val replay: Replay?,
    val tricks: List<Trick>,
    /** The speed the set's master tempo asks of this track. */
    val targetSpeed: Double,
)

/**
 * Plans what happens inside a track from its structure.
 *
 * Both modes:
 * - **Master tempo:** after a mix the track glides to the set's master tempo rather than back
 *   to its own BPM, so the set keeps a tempo arc.
 * - **Tempo push:** through the main section the deck speeds up (key-locked) to lift energy:
 *   4% in Smooth, 2.5% in Trickster.
 *
 * Trickster only:
 * - **Loop-roll builds:** the two bars before a drop become a roll tightening from one beat to
 *   an eighth under a rising high-pass, dressed with sound effects by [SfxDressing]: a build as
 *   long as the quiet run-up and a hit sized to the drop.
 * - **Hook replay:** after a sung hook's first pass, jump back and play it again.
 * - **Phrase fills:** at the end of nearly every 8-bar phrase, a scratch, roll, echo throw,
 *   gate stutter, laser zaps, brake, spinback, whoosh, crash, gunshot or air horn, rotating by
 *   style, at a level that follows the energy there. Scratches are slow baby scratches over the
 *   live beat.
 */
object Performer {
    /** How far a track may be pushed from its own tempo. */
    const val MAX_STRETCH = 0.06
    private const val LOUD = 0.7

    /** Tempo push across the main section: Smooth leans on it for energy, Trickster on tricks. */
    private fun push(mode: MixMode) = if (mode == MixMode.SMOOTH) 0.04 else 0.025

    /** Phrase-end tricks, in rotation, by style. */
    private enum class Fill(val label: String) {
        SCRATCH("Scratch fill"),
        ROLL("Roll fill"),
        ECHO_THROW("Echo throw"),
        GATE("Gate stutter"),
        LASER("Laser zaps"),
        BRAKE("Brake"),
        SPINBACK("Spinback"),
        HORN("Air horn"),
        WHOOSH("Whoosh"),
        GUNSHOT("Gunshot"),
        CRASH("Crash"),
    }

    private val HIPHOP_FILLS = listOf(
        Fill.SCRATCH, Fill.ECHO_THROW, Fill.HORN, Fill.BRAKE, Fill.SCRATCH, Fill.GUNSHOT, Fill.SPINBACK, Fill.WHOOSH, Fill.ECHO_THROW, Fill.LASER,
    )
    private val CLUB_FILLS = listOf(
        Fill.ROLL, Fill.WHOOSH, Fill.ECHO_THROW, Fill.GATE, Fill.CRASH, Fill.LASER, Fill.SCRATCH, Fill.HORN, Fill.BRAKE, Fill.ROLL,
    )

    fun plan(
        t: TrackInfo,
        entryBar: Int,
        startSpeed: Double,
        masterBpm: Double,
        allowReplay: Boolean = true,
        mode: MixMode = MixMode.SMOOTH,
    ): Performance {
        val trickster = mode == MixMode.TRICKSTER
        val bar = t.barSec
        val beat = t.beatSec
        val target = TransitionPlanner.speedFor(masterBpm, t.bpm).coerceIn(1 - MAX_STRETCH, 1 + MAX_STRETCH)
        val events = mutableListOf<FxEvent>()
        val tricks = mutableListOf<Trick>()
        val speedRamps = mutableListOf(Ramp(t.barTime(entryBar), t.barTime(entryBar + 8), startSpeed.toFloat(), target.toFloat()))
        if (kotlin.math.abs(target - startSpeed) > 0.003) {
            tricks += Trick("Glide to set tempo", t.barTime(entryBar), t.barTime(entryBar + 8))
        }

        // Tempo push across the first full main section after the glide.
        val pushStart = (max(entryBar + 8, t.introEndBar)..t.outroStartBar - 32)
            .firstOrNull { t.isPhraseStart(it) && t.energyIn(it, it + 8) >= LOUD }
        if (pushStart != null) {
            val pushed = min(target * (1 + push(mode)), 1 + MAX_STRETCH)
            speedRamps += Ramp(t.barTime(pushStart), t.barTime(pushStart + 32), target.toFloat(), pushed.toFloat())
            tricks += Trick("Tempo push +${"%.1f".format((pushed / target - 1) * 100)}%", t.barTime(pushStart), t.barTime(pushStart + 32))
        }

        // Smooth mode stops here: glide and tempo push only, nothing that interrupts the track.
        if (!trickster) return Performance(events, Lane(speedRamps, startSpeed.toFloat()), null, tricks, target)

        // Roll builds into drops: quiet run-up, then the energy lands on a phrase start, with a
        // siren or riser over the build and a horn and impact on the drop.
        val drops = (entryBar + 8 until t.outroStartBar)
            .filter { t.isPhraseStart(it) && t.energyIn(it - 4, it) < 0.65 && t.energyIn(it, it + 4) >= 0.85 }
            .take(2)
        val dressTurn = Math.floorMod(t.uri.hashCode(), 3)
        for ((n, d) in drops.withIndex()) {
            val dSec = t.barTime(d)
            events += listOf(
                FxEvent(FxType.LOOP_ROLL, dSec - 2 * bar, dSec - bar, p1 = 1f),
                FxEvent(FxType.LOOP_ROLL, dSec - bar, dSec - bar / 2, p1 = 0.5f),
                FxEvent(FxType.LOOP_ROLL, dSec - bar / 2, dSec - bar / 4, p1 = 0.25f),
                FxEvent(FxType.LOOP_ROLL, dSec - bar / 4, dSec, p1 = 0.125f),
                FxEvent(FxType.HIGH_PASS, dSec - 2 * bar, dSec, 0f, 0.55f),
            )
            // Build as long as the quiet run-up, landing with a hit sized to the drop.
            events += SfxDressing.build(t, d, SfxDressing.buildBars(t, d), dressTurn + n)
            events += SfxDressing.hit(t, d, dressTurn + n)
            tricks += Trick("Roll into drop", dSec - 2 * bar, dSec + beat)
        }

        // Hook replay: needs room after it, and must not collide with a roll build. Jumping back
        // mid-lyric sounds like a skip, so only where the hook is a sung chorus.
        val h = t.hookBar
        val replay = if (
            allowReplay && t.vocalIn(h, h + 8) >= 0.5 &&
            h >= entryBar && h + 8 + 24 <= t.outroStartBar &&
            t.energyIn(h, h + 8) >= 0.8 && drops.none { it in h + 7..h + 10 }
        ) {
            val jump = t.barTime(h + 8)
            events += FxEvent(FxType.ECHO, jump - beat, jump, 0.7f, 0.7f, p1 = 0.5f, p2 = 0.45f)
            tricks += Trick("Hook replay", jump - bar, jump + bar)
            Replay(atBar = h + 8, toBar = h)
        } else {
            null
        }

        // A fill at the end of (almost) every phrase, rotating through the style's tricks.
        val busy = drops.flatMap { it - 2..it + 1 } + (if (replay != null) listOf(h, h + 8, h + 9) else emptyList())
        val fills = if (t.genreFamily == Genres.HIPHOP || t.genreFamily == Genres.LATIN) HIPHOP_FILLS else CLUB_FILLS
        var turn = Math.floorMod(t.uri.hashCode(), fills.size)
        for (j in entryBar + 16 until t.outroStartBar - 8) {
            if (!t.isPhraseStart(j) || j in busy) continue
            val fill = fills[turn++ % fills.size]
            val pe = t.barTime(j)
            events += fillEvents(fill, pe, bar, beat, SfxDressing.level(t, j, peak = 0.8f))
            val (from, to) = when (fill) {
                Fill.HORN, Fill.CRASH -> pe to pe + bar
                Fill.GUNSHOT -> pe to pe + 2 * beat
                Fill.WHOOSH -> pe - 2 * beat to pe
                Fill.LASER -> pe - 2 * bar to pe
                else -> pe - bar to pe
            }
            tricks += Trick(fill.label, from, to)
        }
        return Performance(events, Lane(speedRamps, startSpeed.toFloat()), replay, tricks.sortedBy { it.startSec }, target)
    }

    /**
     * The automation for one fill ending at the phrase boundary [pe] (horn, crash and shots
     * start on it). [level] is the sound-effect level that fits the music there.
     */
    private fun fillEvents(fill: Fill, pe: Double, bar: Double, beat: Double, level: Float): List<FxEvent> = when (fill) {
        // Catch half a beat, then slow one-beat baby-scratch strokes over the live beat.
        Fill.SCRATCH -> listOf(FxEvent(FxType.SCRATCH, pe - bar, pe, p1 = 0.5f, p2 = 1f))
        Fill.ROLL -> listOf(
            FxEvent(FxType.LOOP_ROLL, pe - bar, pe - bar / 2, p1 = 1f),
            FxEvent(FxType.LOOP_ROLL, pe - bar / 2, pe - bar / 4, p1 = 0.5f),
            FxEvent(FxType.LOOP_ROLL, pe - bar / 4, pe, p1 = 0.25f),
        )
        // Send the last beat into a 3/4-beat echo that rings over into the next phrase.
        Fill.ECHO_THROW -> listOf(FxEvent(FxType.ECHO, pe - beat, pe, 1f, 1f, p1 = 0.75f, p2 = 0.5f))
        Fill.GATE -> listOf(FxEvent(FxType.GATE, pe - bar, pe, 1f, 1f, p1 = 0.25f))
        Fill.LASER -> listOf(FxEvent(FxType.LASER, pe - 2 * bar, pe, level * 0.6f, level, p1 = 0.5f))
        Fill.BRAKE -> listOf(FxEvent(FxType.BRAKE, pe - 2 * beat, pe))
        Fill.SPINBACK -> listOf(FxEvent(FxType.BACKSPIN, pe - 2 * beat, pe, p1 = 3f))
        Fill.HORN -> listOf(FxEvent(FxType.HORN, pe, pe + bar, level, level))
        Fill.WHOOSH -> listOf(FxEvent(FxType.WHOOSH, pe - 2 * beat, pe + beat, level * 0.8f, level * 0.8f))
        Fill.GUNSHOT -> listOf(FxEvent(FxType.GUNSHOT, pe, pe + 2 * beat, level * 0.8f, level * 0.8f, p1 = 1f))
        Fill.CRASH -> listOf(FxEvent(FxType.CRASH, pe, pe + 2 * bar, level * 0.7f, level * 0.7f))
    }
}
