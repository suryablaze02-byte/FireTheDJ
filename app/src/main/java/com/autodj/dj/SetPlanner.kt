package com.autodj.dj

import com.autodj.analysis.Genres
import com.autodj.lyrics.TimedLyrics
import com.autodj.model.TrackInfo
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Chooses what plays next. Each step should be a small move in tempo, genre, sound and
 * energy; across genres it prefers neighbouring families (house → techno, hip-hop → drum &
 * bass at half/double time) and stays in a genre while it still has good matches, so a mixed
 * library plays as a journey rather than a shuffle. The playlist's own order is ignored; the
 * opener is random and near-equal choices are broken at random, so every session differs.
 */
object SetPlanner {
    fun transitionCost(a: TrackInfo, b: TrackInfo): Double {
        // The tempos that actually meet: A near its end, B at its start. A song with a slow
        // intro and a fast drop is not a match for a fast track just because its drop is.
        val meetPct = abs(TransitionPlanner.speedFor(a.endBpm, b.startBpm) - 1) * 100
        val mainPct = abs(TransitionPlanner.speedFor(a.bpm, b.bpm) - 1) * 100
        val tempoPct = maxOf(meetPct, mainPct)
        val tempo = when {
            tempoPct <= 8 -> tempoPct
            // Reachable with a Loop & ramp: costly, but not ruled out.
            tempoPct <= TransitionPlanner.MAX_RAMP * 100 -> 8 + (tempoPct - 8) * 0.6
            else -> 8 + (TransitionPlanner.MAX_RAMP * 100 - 8) * 0.6 + (tempoPct - TransitionPlanner.MAX_RAMP * 100) * 3
        }
        val genre = when (Genres.distance(a.genreFamily, b.genreFamily)) {
            0 -> (1 - Genres.similarity(a.genre, b.genre)) * 3
            1 -> 6.0
            else -> 14.0
        }
        val va = a.features.vector()
        val vb = b.features.vector()
        val sound = sqrt(va.indices.sumOf { (va[it] - vb[it]) * (va[it] - vb[it]) }) * 4
        // Bass weight on its own: a heavy track into a light one feels like the floor dropped out.
        val bass = abs(a.features.lowRatio - b.features.lowRatio) * 20
        val dE = b.loudnessDb - a.loudnessDb
        // Building energy is fine; dropping it suddenly kills a dance floor.
        val energy = if (dE >= 0) dE * 0.3 else -dE * 0.6
        // Harmonic mixing: keys that sit together on the Camelot wheel (a ±2 st shift allowed).
        // Stepping up the wheel into a track that's at least as big lifts the floor, so it's
        // sought out: one step costs less than staying put, two steps (played as a cut) are fine.
        val (shift, fits) = TransitionPlanner.keyShift(a, 0, b)
        val key = when (TransitionPlanner.keyLift(a, 0, b)) {
            1 -> -0.6
            2 -> 0.6
            else -> if (fits) abs(shift) * 0.4 else 2.5
        }
        return tempo + genre + sound + bass + energy + key
    }

    /** Any track can open the set, so the same playlist takes a different route every time. */
    fun opener(tracks: List<TrackInfo>, random: Random = Random.Default): TrackInfo = tracks.random(random)

    /**
     * The next track from [remaining] after [current]. With [random], picks among the tracks
     * that are nearly as good a match as the best one (within [VARIETY_MARGIN]), so sets vary
     * without taking a worse transition; without it, always the best.
     */
    fun pickNext(
        current: TrackInfo,
        remaining: List<TrackInfo>,
        random: Random? = null,
        /** Timed words per track: a song that can answer a word of [current] is a better next pick. */
        lyrics: (TrackInfo) -> TimedLyrics? = { null },
    ): TrackInfo? {
        if (remaining.isEmpty()) return null
        val mine = lyrics(current)
        val costs = remaining.associateWith {
            transitionCost(current, it) - if (mine != null && wordFriendly(current, it) && WordPlay.hasLink(mine, lyrics(it), it.durationSec)) WORD_LINK_BONUS else 0.0
        }
        val best = costs.values.min()
        if (random == null) return costs.minBy { it.value }.key
        return costs.filterValues { it <= best + VARIETY_MARGIN }.keys.random(random)
    }

    private const val VARIETY_MARGIN = 1.0

    /**
     * How much a shared word makes up for. The word comes first: it outweighs a tempo or groove
     * mismatch (the word-play mix bridges those), but only between songs that belong together.
     */
    private const val WORD_LINK_BONUS = 5.0

    /** The same or a neighbouring genre, and no big drop in loudness: a word never pulls in a stranger. */
    private fun wordFriendly(a: TrackInfo, b: TrackInfo) =
        Genres.distance(a.genreFamily, b.genreFamily) <= 1 && b.loudnessDb >= a.loudnessDb - 3.0

    /** The order the queue would play in from here, for display. */
    fun upcoming(current: TrackInfo, remaining: List<TrackInfo>): List<TrackInfo> {
        val left = remaining.toMutableList()
        val result = mutableListOf<TrackInfo>()
        var at = current
        while (left.isNotEmpty()) {
            val next = pickNext(at, left)!!
            left.remove(next)
            result += next
            at = next
        }
        return result
    }

    /** A whole set in advance: greedy from the first track, then 2-opt to remove bad steps. */
    fun order(tracks: List<TrackInfo>): List<TrackInfo> {
        if (tracks.size <= 2) return tracks
        return twoOpt((listOf(tracks.first()) + upcoming(tracks.first(), tracks.drop(1))).toMutableList())
    }

    private fun twoOpt(route: MutableList<TrackInfo>): List<TrackInfo> {
        fun cost(r: List<TrackInfo>) = r.zipWithNext { x, y -> transitionCost(x, y) }.sum()
        var best = cost(route)
        var improved = true
        var passes = 0
        while (improved && passes++ < 20) {
            improved = false
            for (i in 1 until route.size - 1) {
                for (j in i + 1 until route.size) {
                    val candidate = route.subList(0, i) + route.subList(i, j + 1).reversed() + route.subList(j + 1, route.size)
                    val c = cost(candidate)
                    if (c < best - 1e-9) {
                        best = c
                        route.clear()
                        route.addAll(candidate)
                        improved = true
                    }
                }
            }
        }
        return route
    }
}
