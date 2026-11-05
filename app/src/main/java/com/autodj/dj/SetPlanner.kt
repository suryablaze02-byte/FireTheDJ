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

    /**
     * Any of the set's quieter tracks can open it (with a [shape]: its lowest-energy third), so
     * the same playlist takes a different route every time but never peaks in the first song.
     */
    fun opener(tracks: List<TrackInfo>, random: Random = Random.Default, shape: SetShape? = null): TrackInfo {
        if (shape == null) return tracks.random(random)
        val calm = tracks.sortedBy { shape.energyOf(it) }.take(maxOf(1, tracks.size / 3))
        return calm.random(random)
    }

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
        /** The set's arc, and how far through the set the next track plays (0..1). */
        shape: SetShape? = null,
        progress: Double = 0.0,
        /** The local hour (0..23) the set is playing at, for the arc; -1 = ignore the clock. */
        hour: Int = -1,
        /** Songs the listener just skipped in a row: the floor wants something else, so steer away. */
        avoid: List<TrackInfo> = emptyList(),
    ): TrackInfo? {
        if (remaining.isEmpty()) return null
        val mine = lyrics(current)
        val target = shape?.let { SetShape.target(progress, hour) }
        val away = changeOfCourse(avoid, shape)
        val costs = remaining.associateWith {
            transitionCost(current, it) - (if (mine != null && wordFriendly(current, it) && WordPlay.hasLink(mine, lyrics(it), it.durationSec)) WORD_LINK_BONUS else 0.0) +
                (if (shape != null && target != null) SHAPE_WEIGHT * abs(shape.energyOf(it) - target) else 0.0) +
                (if (shape != null && shape.heldForFinale(it, progress)) FINALE_HOLD else 0.0) +
                away(it)
        }
        val best = costs.values.min()
        if (random == null) return costs.minBy { it.value }.key
        return costs.filterValues { it <= best + VARIETY_MARGIN }.keys.random(random)
    }

    private const val VARIETY_MARGIN = 1.0

    /** How hard the set's arc pulls: a track a whole energy range off target costs about a key clash. */
    private const val SHAPE_WEIGHT = 3.0

    /** What it costs to play a finale track early: more than any ordinary mismatch. */
    private const val FINALE_HOLD = 4.0

    /** Skips in a row that mean the floor isn't having it. */
    const val SKIPS_TO_CHANGE = 3

    /**
     * After [SKIPS_TO_CHANGE] skips in a row, a cost for more of the same (the skipped songs'
     * genre, and their energy as ranked by [shape], or their loudness in a set without one), and
     * the usual pull to stay in a genre is relaxed: staying is what wasn't working.
     */
    private fun changeOfCourse(avoid: List<TrackInfo>, shape: SetShape?): (TrackInfo) -> Double {
        if (avoid.size < SKIPS_TO_CHANGE) return { 0.0 }
        val family = avoid.groupingBy { it.genreFamily }.eachCount().maxBy { it.value }.key
        val sameEnergy: (TrackInfo) -> Double = if (shape != null) {
            val e = avoid.map { shape.energyOf(it) }.average();
            { t -> maxOf(0.0, 1 - abs(shape.energyOf(t) - e) / 0.3) }
        } else {
            val db = avoid.map { it.loudnessDb }.average();
            { t -> maxOf(0.0, 1 - abs(t.loudnessDb - db) / 3.0) }
        }
        return { t -> (if (t.genreFamily == family) CHANGE_GENRE else -LEAVE_GENRE) + CHANGE_ENERGY * sameEnergy(t) }
    }

    private const val CHANGE_GENRE = 2.5
    private const val CHANGE_ENERGY = 3.0

    /** Taken off a genre change after the skips (a neighbouring genre normally costs 6). */
    private const val LEAVE_GENRE = 4.0

    /**
     * How much a shared word makes up for. The word comes first: it outweighs a tempo or groove
     * mismatch (the word-play mix bridges those), but only between songs that belong together.
     */
    private const val WORD_LINK_BONUS = 5.0

    /** The same or a neighbouring genre, and no big drop in loudness: a word never pulls in a stranger. */
    private fun wordFriendly(a: TrackInfo, b: TrackInfo) =
        Genres.distance(a.genreFamily, b.genreFamily) <= 1 && b.loudnessDb >= a.loudnessDb - 3.0

    /** The order the queue would play in from here, for display. */
    /**
     * The order the queue would play in from here, for display: with the set's [shape], the same
     * arc and finale hold as the real picks. [atIndex] is [current]'s place in a set of [setSize].
     */
    fun upcoming(
        current: TrackInfo,
        remaining: List<TrackInfo>,
        shape: SetShape? = null,
        atIndex: Int = 0,
        setSize: Int = 0,
        hour: Int = -1,
    ): List<TrackInfo> {
        val left = remaining.toMutableList()
        val result = mutableListOf<TrackInfo>()
        var at = current
        while (left.isNotEmpty()) {
            val progress = (atIndex + result.size + 1).toDouble() / maxOf(1, setSize - 1)
            val next = pickNext(at, left, shape = shape, progress = progress, hour = hour)!!
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

/**
 * The arc of a set, the way a DJ builds a night: a calm opening that lets people arrive, a build,
 * the peak through the middle-to-late part, a release that gives the floor room to breathe, and a
 * big finale. Each track's energy (loudness, tempo, how busy and bright it is) is ranked against
 * the rest of the set, 0 = its calmest track, 1 = its biggest, so the arc fits any crate.
 */
class SetShape(tracks: List<TrackInfo>) {
    private val energy: Map<String, Double> = rank(tracks)

    /** 0..1 within the set; a track the set didn't start with sits in the middle. */
    fun energyOf(t: TrackInfo): Double = energy[t.uri] ?: 0.5

    /**
     * The set's biggest tracks (its top tenth), held back for the finale: a DJ keeps a record in
     * the bag for the close instead of spending everything at the peak.
     */
    private val finale: Set<String> = energy.entries.sortedByDescending { it.value }
        .take(maxOf(1, (energy.size + 9) / 10)).mapTo(HashSet()) { it.key }

    fun heldForFinale(t: TrackInfo, progress: Double) = progress < FINALE_FROM && t.uri in finale

    companion object {
        /** Sets shorter than this have no room for an arc. */
        const val MIN_TRACKS = 6

        /** Where the finale starts, as a share of the set. */
        const val FINALE_FROM = 0.9

        fun forSet(tracks: List<TrackInfo>): SetShape? = if (tracks.size >= MIN_TRACKS) SetShape(tracks) else null

        /**
         * The energy the set wants [progress] of the way through (0..1), at the local [hour]
         * (-1 = any time): the same crowd wants a gentler peak at 8 pm than at 1 am, and less
         * again once it's nearly morning.
         */
        fun target(progress: Double, hour: Int = -1): Double {
            val p = progress.coerceIn(0.0, 1.0)
            val arc = when {
                p < 0.2 -> 0.25 // opening: let the room arrive
                p < 0.5 -> 0.3 + (p - 0.2) / 0.3 * 0.5 // build
                p < 0.75 -> 0.9 // peak
                p < 0.9 -> 0.55 // release
                else -> 0.85 // finale
            }
            return when (hour) {
                in 18..21 -> minOf(arc, 0.7) // early evening: people are still arriving
                in 3..5 -> arc * 0.8 // the small hours: winding down
                else -> arc
            }
        }

        private fun rank(tracks: List<TrackInfo>): Map<String, Double> {
            if (tracks.isEmpty()) return emptyMap()
            fun z(values: List<Double>): List<Double> {
                val mean = values.average()
                val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size).takeIf { it > 1e-9 } ?: 1.0
                return values.map { (it - mean) / sd }
            }
            val loud = z(tracks.map { it.loudnessDb })
            val tempo = z(tracks.map { it.bpm })
            val busy = z(tracks.map { it.features.onsetRate })
            val bright = z(tracks.map { it.features.centroidHz })
            val raw = tracks.indices.map { loud[it] + 0.7 * tempo[it] + 0.7 * busy[it] + 0.3 * bright[it] }
            val order = raw.indices.sortedBy { raw[it] }
            val result = HashMap<String, Double>()
            order.forEachIndexed { rank, i -> result[tracks[i].uri] = if (tracks.size == 1) 0.5 else rank.toDouble() / (tracks.size - 1) }
            return result
        }
    }
}
