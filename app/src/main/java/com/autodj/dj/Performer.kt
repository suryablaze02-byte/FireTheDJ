package com.autodj.dj

import com.autodj.analysis.Genres
import com.autodj.dj.fx.Drum
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxType
import com.autodj.dj.fx.Lane
import com.autodj.dj.fx.Ramp
import com.autodj.lyrics.LyricWord
import com.autodj.lyrics.TimedLyrics
import com.autodj.lyrics.WordKey
import com.autodj.model.TrackInfo
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * When the playhead reaches bar [atBar], jump back to bar [toBar] (played again on the other
 * deck): a crossfade for a hook replay, a hard [cut] for a rewind.
 */
class Replay(val atBar: Int, val toBar: Int, val cut: Boolean = false)

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
 *   Every other drop gets half a beat of silence before it lands.
 * - **Hook replay:** after a sung hook's first pass, jump back and play it again.
 * - **Rewind:** otherwise, in hip-hop, bass, Latin and Indian music, the first drop is pulled
 *   up: a spinback at the end of its first 8 bars and straight back to the top of the drop.
 * - **Phrase fills:** at the end of nearly every 8-bar phrase, a scratch, roll, echo throw,
 *   gate stutter, laser zaps, brake, spinback, whoosh, crash, gunshot or air horn, rotating by
 *   style, at a level that follows the energy there. Scratches are slow baby scratches over the
 *   live beat. Indian music gets its region's drums (dappu, marfa, thavil or dhol), sirens,
 *   horns and a nadaswaram sting in the rotation.
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
        DAPPU("Dappu fill"),
        MARFA("Marfa roll"),
        DHOL("Dhol fill"),
        THAVIL("Thavil roll"),
        REED("Nadaswaram"),
        SIREN("Siren"),
    }

    private val SOUTH_FILLS = listOf(
        Fill.DAPPU, Fill.HORN, Fill.ECHO_THROW, Fill.MARFA, Fill.REED, Fill.SIREN, Fill.DAPPU, Fill.ROLL, Fill.THAVIL, Fill.ECHO_THROW,
    )
    private val NORTH_FILLS = listOf(
        Fill.DHOL, Fill.HORN, Fill.ECHO_THROW, Fill.SIREN, Fill.DHOL, Fill.ROLL, Fill.BRAKE, Fill.GUNSHOT, Fill.ECHO_THROW,
    )
    private val INDIAN_FILLS = listOf(
        Fill.DAPPU, Fill.HORN, Fill.DHOL, Fill.ECHO_THROW, Fill.MARFA, Fill.REED, Fill.SIREN, Fill.ROLL,
    )

    /** Styles where pulling a drop back up is part of the culture. */
    private val REWIND_FAMILIES = setOf(Genres.HIPHOP, Genres.LATIN, Genres.BASS, Genres.DNB)

    /**
     * Each genre's phrase fills, in rotation. Scratching is hip-hop's: anywhere else it breaks
     * the flow, so no other genre has it. House and trance stay smooth (rolls, echoes, sweeps),
     * techno minimal, bass music and drum & bass loud, chill barely there.
     */
    private val FILLS: Map<String, List<Fill>> = mapOf(
        Genres.HIPHOP to listOf(
            Fill.SCRATCH, Fill.ECHO_THROW, Fill.HORN, Fill.BRAKE, Fill.SCRATCH, Fill.GUNSHOT, Fill.SPINBACK, Fill.WHOOSH, Fill.ECHO_THROW, Fill.LASER,
        ),
        Genres.HOUSE to listOf(Fill.ROLL, Fill.ECHO_THROW, Fill.WHOOSH, Fill.CRASH, Fill.GATE, Fill.ROLL, Fill.ECHO_THROW),
        Genres.TECHNO to listOf(Fill.GATE, Fill.ROLL, Fill.ECHO_THROW, Fill.ROLL, Fill.GATE),
        Genres.TRANCE to listOf(Fill.ROLL, Fill.WHOOSH, Fill.CRASH, Fill.ECHO_THROW, Fill.GATE),
        Genres.DNB to listOf(Fill.ROLL, Fill.ECHO_THROW, Fill.HORN, Fill.SPINBACK, Fill.LASER),
        Genres.BASS to listOf(Fill.LASER, Fill.ROLL, Fill.GUNSHOT, Fill.SPINBACK, Fill.ECHO_THROW, Fill.HORN),
        Genres.LATIN to listOf(Fill.HORN, Fill.ECHO_THROW, Fill.ROLL, Fill.WHOOSH, Fill.SPINBACK),
        Genres.POP to listOf(Fill.ROLL, Fill.ECHO_THROW, Fill.WHOOSH, Fill.CRASH, Fill.GATE),
        Genres.CHILL to listOf(Fill.ECHO_THROW),
        Genres.ROCK to listOf(Fill.ECHO_THROW, Fill.CRASH, Fill.BRAKE),
    )

    /** Genres where a fill every other phrase would be too busy: every fourth instead. */
    private val SPARSE = setOf(Genres.CHILL, Genres.ROCK)

    /** Phrase fills per track at most. */
    private const val MAX_FILLS = 4

    fun plan(
        t: TrackInfo,
        entryBar: Int,
        startSpeed: Double,
        masterBpm: Double,
        allowReplay: Boolean = true,
        mode: MixMode = MixMode.SMOOTH,
        /** The track's timed words, for word loops into drops; null plays the plain rolls. */
        lyrics: TimedLyrics? = null,
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
        val pushed = min(target * (1 + push(mode)), 1 + MAX_STRETCH)
        // A track already stretched as far as it goes has no room left to push.
        if (pushStart != null && pushed / target - 1 >= 0.005) {
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
            // The last word sung before the drop, looped and tightening into it; else a plain roll.
            val word = lyrics?.let { wordBeforeDrop(t, it, dSec) }
            events += if (word != null) {
                wordLoop(t, word, dSec)
            } else {
                listOf(
                    FxEvent(FxType.LOOP_ROLL, dSec - 2 * bar, dSec - bar, p1 = 1f),
                    FxEvent(FxType.LOOP_ROLL, dSec - bar, dSec - bar / 2, p1 = 0.5f),
                    FxEvent(FxType.LOOP_ROLL, dSec - bar / 2, dSec - bar / 4, p1 = 0.25f),
                    FxEvent(FxType.LOOP_ROLL, dSec - bar / 4, dSec, p1 = 0.125f),
                )
            }
            events += FxEvent(FxType.HIGH_PASS, dSec - 2 * bar, dSec, 0f, 0.55f)
            // Build as long as the quiet run-up, landing with a hit sized to the drop.
            events += SfxDressing.build(t, d, SfxDressing.buildBars(t, d), dressTurn + n)
            events += SfxDressing.hit(t, d, dressTurn + n)
            // Every other drop: half a beat of nothing, so the drop hits harder.
            val gap = Math.floorMod(dressTurn + n, 2) == 0
            if (gap) events += FxEvent(FxType.MUTE, dSec - beat / 2, dSec)
            val into = if (word != null) "Word loop “${word.text}”" else "Roll"
            tricks += Trick(if (gap) "$into · gap · drop" else "$into into drop", dSec - 2 * bar, dSec + beat)
        }

        // Hook replay: needs room after it, and must not collide with a roll build. Jumping back
        // mid-lyric sounds like a skip, so only where the hook is a sung chorus.
        val h = t.hookBar
        val hookReplay = if (
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

        // Rewind: spin the first big drop back and drop it again (its hit fires again too).
        val indian = SfxDressing.indian(t)
        val rewindable = indian || t.genreFamily in REWIND_FAMILIES
        val rewindBar = if (hookReplay == null && allowReplay && rewindable) {
            drops.firstOrNull { d -> d + 8 + 24 <= t.outroStartBar && t.energyIn(d, d + 8) >= 0.8 && drops.none { it in d + 7..d + 10 } }
        } else {
            null
        }
        val replay = hookReplay ?: rewindBar?.let { d ->
            val jump = t.barTime(d + 8)
            events += FxEvent(FxType.BACKSPIN, jump - 2 * beat, jump, p1 = 3f)
            tricks += Trick("Rewind", jump - 2 * beat, jump + beat)
            Replay(atBar = d + 8, toBar = d, cut = true)
        }

        // A fill at the end of every other phrase (every fourth for sparse styles), rotating through
        // the style's tricks: a few well-placed ones, never over the singing, keep the track flowing.
        val busy = drops.flatMap { it - 2..it + 1 } +
            (if (hookReplay != null) listOf(h, h + 8, h + 9) else emptyList()) +
            (rewindBar?.let { listOf(it + 8, it + 9) } ?: emptyList())
        val fills = when (SfxDressing.region(t)) {
            Genres.Region.SOUTH -> SOUTH_FILLS
            Genres.Region.NORTH -> NORTH_FILLS
            Genres.Region.INDIAN -> INDIAN_FILLS
            null -> FILLS[t.genreFamily] ?: FILLS.getValue(Genres.POP)
        }
        val sparse = SfxDressing.region(t) == null && t.genreFamily in SPARSE
        var turn = Math.floorMod(t.uri.hashCode(), fills.size)
        var phrase = 0
        var placed = 0
        for (j in entryBar + 16 until t.outroStartBar - 8) {
            if (!t.isPhraseStart(j) || j in busy) continue
            if (phrase++ % (if (sparse) 4 else 2) != 1 || placed >= MAX_FILLS) continue
            if (t.vocalIn(j - 1, j) >= 0.5) continue
            placed++
            val fill = fills[turn++ % fills.size]
            val pe = t.barTime(j)
            events += fillEvents(t, fill, pe, bar, beat, SfxDressing.level(t, j, peak = 0.8f))
            val (from, to) = when (fill) {
                Fill.HORN, Fill.CRASH, Fill.REED -> pe to pe + bar
                Fill.GUNSHOT -> pe to pe + 2 * beat
                Fill.WHOOSH -> pe - 2 * beat to pe
                Fill.LASER -> pe - 2 * bar to pe
                Fill.DAPPU, Fill.MARFA, Fill.DHOL -> pe - bar to pe + 3 * beat
                else -> pe - bar to pe
            }
            tricks += Trick(fill.label, from, to)
        }
        return Performance(events, Lane(speedRamps, startSpeed.toFloat()), replay, tricks.sortedBy { it.startSec }, target)
    }

    /**
     * The word to loop into the drop at [dSec]: sung in the two bars before it, at least a beat
     * and a half ahead, opening its line (where lyric timing is reliable) and no longer than a
     * beat. A word that opens its line wins, then the song's hook word, then the latest. Null if there's none.
     */
    private fun wordBeforeDrop(t: TrackInfo, l: TimedLyrics, dSec: Double): LyricWord? {
        val beat = t.beatSec
        val fits = l.words.withIndex().filter { (i, w) ->
            w.startSec >= dSec - 2 * t.barSec && w.startSec <= dSec - 1.5 * beat &&
                w.endSec - w.startSec <= beat * 1.05 && WordKey.linkable(w.key) &&
                (w.lineStart || l.words.getOrNull(i - 1)?.let { p -> p.line == w.line && p.lineStart } == true)
        }.map { it.value }
        // A line's first word is timed by the lyrics themselves; later ones are estimates.
        return fits.maxWithOrNull(compareBy<LyricWord>({ it.lineStart }, { (l.counts[it.key] ?: 0) >= 3 }, { it.startSec }))
    }

    /**
     * The word repeating into the drop: a loop the length of the word (a quarter, half or whole
     * beat) from the quarter beat it starts on, then tightening over the last two beats to a
     * half, a quarter and an eighth of a beat ("Kissik, Kissik, Kiss-Kiss, Ki-Ki-Ki").
     * The loops share one capture, so every repeat is the start of the same word.
     */
    private fun wordLoop(t: TrackInfo, w: LyricWord, dSec: Double): List<FxEvent> {
        val beat = t.beatSec
        val quarter = beat / 4
        val start = t.downbeatSec + floor((w.startSec - t.downbeatSec) / quarter) * quarter
        val len = min(1.0, WordPlay.loopBeats(w.endSec - start, beat))
        // The last two beats tighten; before them the word plays at its own length.
        val tiers = listOf(dSec - 2 * beat to 0.5, dSec - beat to 0.25, dSec - beat / 2 to 0.125)
            .filter { (_, l) -> l < len }
        val out = mutableListOf<FxEvent>()
        var from = start
        var l = len
        for ((at, tighter) in tiers) {
            if (at > from) {
                out += FxEvent(FxType.LOOP_ROLL, from, at, p1 = l.toFloat())
                from = at
            }
            l = tighter
        }
        out += FxEvent(FxType.LOOP_ROLL, from, dSec, p1 = l.toFloat())
        return out
    }

    /**
     * The automation for one fill ending at the phrase boundary [pe] (horn, crash and shots
     * start on it). [level] is the sound-effect level that fits the music there.
     */
    private fun fillEvents(t: TrackInfo, fill: Fill, pe: Double, bar: Double, beat: Double, level: Float): List<FxEvent> = when (fill) {
        // The drum fills into the phrase and punctuates its first beats.
        Fill.DAPPU -> listOf(FxEvent(FxType.DAPPU, pe - bar, pe + bar, level * 0.75f, level, p1 = Drum.FILL_AND_HIT))
        Fill.MARFA -> listOf(FxEvent(FxType.MARFA, pe - bar, pe + bar, level * 0.75f, level, p1 = Drum.FILL_AND_HIT))
        Fill.DHOL -> listOf(FxEvent(FxType.DHOL, pe - bar, pe + bar, level * 0.75f, level, p1 = Drum.FILL_AND_HIT))
        Fill.THAVIL -> listOf(FxEvent(FxType.THAVIL, pe - bar, pe, level * 0.6f, level * 0.9f, p1 = Drum.FILL))
        // A short reed flourish welcoming the new phrase, in the track's key.
        Fill.REED -> listOf(SfxDressing.reed(t, pe, pe + bar, level * 0.8f))
        Fill.SIREN -> listOf(FxEvent(FxType.SIREN, pe - bar, pe, level * 0.5f, level * 0.85f, p1 = 4f))
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
