package com.autodj.dj

import com.autodj.analysis.Genres
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxType
import com.autodj.model.TrackInfo
import kotlin.math.max

/**
 * Picks the sound effects for a moment in a track and sizes them to fit it, the way a DJ
 * rides the effects fader: effects follow the music's energy instead of a fixed level.
 *
 * - **Level** follows the energy where the effect plays: a whisper over a breakdown, full
 *   over a drop (see [level]).
 * - **Length** follows the run-up: a build is as long as the quiet stretch before the drop
 *   (2, 4 or 8 bars), and hits ring longer on bigger drops.
 * - **Choice** follows the style: snare rolls, risers, reverse cymbals, crashes and sub drops
 *   for electronic music; sirens, horns, gunshots and impacts for hip-hop, bass and Latin.
 *   The options rotate, so two drops in a set don't get the same dressing.
 */
object SfxDressing {
    private val ELECTRONIC = setOf(Genres.HOUSE, Genres.TECHNO, Genres.TRANCE, Genres.POP, Genres.CHILL, Genres.ROCK)

    fun electronic(t: TrackInfo) = t.genreFamily in ELECTRONIC

    /** Energy around [bar] (two bars either side), 0..1. */
    private fun energyAround(t: TrackInfo, bar: Int) = t.energyIn(max(0, bar - 2), bar + 2).coerceIn(0.0, 1.0)

    /**
     * Effect level for an effect at [bar]: from 35% of [peak] over a quiet passage up to [peak]
     * over the loudest parts.
     */
    fun level(t: TrackInfo, bar: Int, peak: Float = 0.9f): Float =
        (peak * (0.35 + 0.65 * energyAround(t, bar))).toFloat()

    /** How many quiet bars lead into [bar], rounded down to a build length of 2, 4 or 8. */
    fun buildBars(t: TrackInfo, bar: Int): Int {
        var quiet = 0
        while (quiet < 8 && bar - quiet - 1 >= 0 && t.energyIn(bar - quiet - 1, bar - quiet) < 0.7) quiet++
        return when {
            quiet >= 8 -> 8
            quiet >= 4 -> 4
            else -> 2
        }
    }

    /**
     * The build into a hit on [bar] of [t]: [bars] long, swelling to the hit. [turn] rotates
     * the choice.
     */
    fun build(t: TrackInfo, bar: Int, bars: Int, turn: Int, peak: Float = 0.9f): List<FxEvent> {
        val hit = t.barTime(bar)
        val from = hit - bars * t.barSec
        val top = level(t, bar, peak)
        val low = top * 0.35f
        val electronic = electronic(t)
        return when (Math.floorMod(turn, 3)) {
            0 -> if (electronic) {
                // Noise riser all the way, snare roll over the last half.
                listOf(
                    FxEvent(FxType.RISER, from, hit, low, top),
                    FxEvent(FxType.SNARE_ROLL, hit - max(1, bars / 2) * t.barSec, hit, low, top * 0.8f),
                )
            } else {
                listOf(FxEvent(FxType.SIREN, from, hit, low, top * 0.85f, p1 = if (bars >= 4) 2.5f else 4f))
            }
            1 -> if (electronic) {
                // Snare roll the whole way, a reverse cymbal sucking into the hit.
                listOf(
                    FxEvent(FxType.SNARE_ROLL, from, hit, low, top * 0.8f),
                    FxEvent(FxType.REVERSE_CYMBAL, hit - t.barSec, hit, top * 0.8f, top * 0.8f),
                )
            } else {
                listOf(
                    FxEvent(FxType.RISER, from, hit, low, top * 0.8f),
                    FxEvent(FxType.REVERSE_CYMBAL, hit - t.barSec, hit, top * 0.7f, top * 0.7f),
                )
            }
            else -> listOf(
                FxEvent(FxType.RISER, from, hit, low, top),
                FxEvent(FxType.REVERSE_CYMBAL, hit - t.barSec, hit, top * 0.75f, top * 0.75f),
            )
        }
    }

    /** What lands on the downbeat of [bar]: sized by how big that moment is. */
    fun hit(t: TrackInfo, bar: Int, turn: Int, peak: Float = 0.9f): List<FxEvent> {
        val at = t.barTime(bar)
        val energy = energyAround(t, bar + 2)
        val top = level(t, bar + 2, peak)
        // Bigger drops ring longer.
        val ring = 2.0 + 3.0 * energy
        val electronic = electronic(t)
        return when (Math.floorMod(turn, 3)) {
            0 -> if (electronic) {
                listOf(FxEvent(FxType.CRASH, at, at + ring, top * 0.8f, top * 0.8f), FxEvent(FxType.SUB_DROP, at, at + ring, top, top))
            } else {
                listOf(FxEvent(FxType.HORN, at, at + t.barSec, top, top), FxEvent(FxType.IMPACT, at, at + ring, top, top))
            }
            1 -> if (electronic) {
                listOf(FxEvent(FxType.CRASH, at, at + ring, top * 0.8f, top * 0.8f), FxEvent(FxType.IMPACT, at, at + ring, top, top))
            } else {
                // "Pull up": three shots on the beat, then the bass.
                listOf(
                    FxEvent(FxType.GUNSHOT, at, at + 3 * t.beatSec, top * 0.8f, top * 0.8f, p1 = 1f),
                    FxEvent(FxType.SUB_DROP, at, at + ring, top, top),
                )
            }
            else -> if (electronic) {
                listOf(FxEvent(FxType.CRASH, at, at + ring, top * 0.8f, top * 0.8f), FxEvent(FxType.DOWNLIFTER, at, at + 2 * t.barSec, top * 0.6f, top * 0.6f))
            } else {
                listOf(FxEvent(FxType.HORN, at, at + t.barSec, top, top), FxEvent(FxType.CRASH, at, at + ring, top * 0.7f, top * 0.7f))
            }
        }
    }
}
