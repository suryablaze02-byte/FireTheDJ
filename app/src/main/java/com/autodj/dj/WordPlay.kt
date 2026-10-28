package com.autodj.dj

import com.autodj.lyrics.LyricWord
import com.autodj.lyrics.TimedLyrics
import com.autodj.lyrics.WordKey
import kotlin.math.abs
import kotlin.math.min

/** A word A sings that B answers: the same word ([rhyme] = false) or one that rhymes with it. */
class WordLink(val aWord: LyricWord, val bWord: LyricWord, val rhyme: Boolean, val score: Double) {
    /** "“party”", or "“love” → “above”" for a rhyme. */
    val label get() = if (rhyme) "“${aWord.text}” → “${bWord.text}”" else "“${aWord.text}”"
}

/**
 * Word play, the way DJs link songs through their lyrics: A sings a word, the mix cuts, and B
 * comes in on a line that starts with the same word (or a rhyme), so B seems to answer A.
 *
 * A's word must end a line or at least end inside the window where A may leave; B's word must
 * open a line (after at most one filler) inside the stretch of B that may be skipped to. The
 * best link is the rarest, longest word, sitting at the end of A's line, ideally a hook both
 * songs repeat, near the time A would normally hand over.
 */
object WordPlay {
    /** A link worth a transition: a clear same-word match, or a rhyme with everything going for it. */
    const val MIN_SCORE = 3.0

    fun find(
        a: TimedLyrics,
        b: TimedLyrics,
        aFromSec: Double,
        aToSec: Double,
        bFromSec: Double,
        bToSec: Double,
        /** When A would ideally hand over. */
        targetSec: Double,
        /** B's energy at a time, 0..1: a line that comes in over light drums enters more gently. */
        bEnergyAt: (Double) -> Double = { 0.5 },
    ): WordLink? {
        val openers = b.words.withIndex()
            .filter { (i, w) -> w.startSec in bFromSec..bToSec && WordKey.linkable(w.key) && opensLine(b.words, i) }
            .map { it.value }
        if (openers.isEmpty()) return null
        val byKey = openers.groupBy { it.key }
        val byRhyme = openers.filter { it.key.length >= 4 }.groupBy { it.key.takeLast(3) }
        var best: WordLink? = null
        for (w in a.words) {
            if (w.endSec !in aFromSec..aToSec || !WordKey.linkable(w.key)) continue
            val same = byKey[w.key].orEmpty().map { it to false }
            val rhymes = if (w.key.length >= 4) byRhyme[w.key.takeLast(3)].orEmpty().filter { it.key != w.key }.map { it to true } else emptyList()
            for ((bw, rhyme) in same + rhymes) {
                var s = if (rhyme) 2.0 else 3.0
                s += 0.1 * min(6, w.key.length - 3)
                if (w.lineEnd) s += 0.6
                // A hook both songs keep coming back to is the one people recognise.
                if ((a.counts[w.key] ?: 0) >= 3 && (b.counts[bw.key] ?: 0) >= 3) s += 0.8
                s -= abs(w.endSec - targetSec) / 60
                s -= (bw.startSec - bFromSec) / 120
                s += (0.7 - bEnergyAt(bw.startSec)) * 0.5
                if (s > (best?.score ?: Double.NEGATIVE_INFINITY)) best = WordLink(w, bw, rhyme, s)
            }
        }
        return best?.takeIf { it.score >= MIN_SCORE }
    }

    /**
     * The loop length for a word lasting [wordSec]: the shortest of a quarter, half, one or two
     * beats it fits in, so its repeats stay on the beat ("par-ty, par-ty") instead of drifting.
     */
    fun loopBeats(wordSec: Double, beatSec: Double): Double =
        LOOP_BEATS.firstOrNull { it * beatSec >= wordSec * 0.9 } ?: LOOP_BEATS.last()

    private val LOOP_BEATS = listOf(0.25, 0.5, 1.0, 2.0)

    /** The word at [i] starts its line, or follows only a filler that does ("oh, PARTY …"). */
    private fun opensLine(words: List<LyricWord>, i: Int): Boolean {
        val w = words[i]
        if (w.lineStart) return true
        val prev = words.getOrNull(i - 1) ?: return false
        return prev.line == w.line && prev.lineStart && !WordKey.linkable(prev.key)
    }

    /**
     * Quick check for choosing the next song: does a word A sings after its first
     * [TransitionPlanner.MIN_PLAY_SEC] open a line in the part of B that may be skipped to
     * (its first 40%)? The planner then looks for the exact moment.
     */
    fun hasLink(a: TimedLyrics?, b: TimedLyrics?, bDurationSec: Double): Boolean {
        if (a == null || b == null) return false
        val aKeys = a.words.filter { it.endSec >= TransitionPlanner.MIN_PLAY_SEC && WordKey.linkable(it.key) }.mapTo(HashSet()) { it.key }
        if (aKeys.isEmpty()) return false
        return b.words.withIndex().any { (i, w) -> w.startSec <= 0.4 * bDurationSec && w.key in aKeys && opensLine(b.words, i) }
    }
}
