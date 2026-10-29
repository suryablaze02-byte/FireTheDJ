package com.autodj.dj

import com.autodj.lyrics.LyricWord
import com.autodj.lyrics.TimedLyrics
import com.autodj.lyrics.WordKey
import kotlin.math.abs
import kotlin.math.min

/** A word A sings that B answers: the same word ([rhyme] = false) or one that rhymes with it. */
class WordLink(val aWord: LyricWord, val bWord: LyricWord, val rhyme: Boolean, val score: Double, val kind: Kind = if (rhyme) Kind.RHYME else Kind.SAME) {
    /** "“party”", "“night” ~ “knight”" for words that sound alike, "“fire” → “higher”" for a rhyme. */
    val label get() = when (kind) {
        Kind.SAME -> "“${aWord.text}”"
        Kind.SOUNDS_ALIKE -> "“${aWord.text}” ~ “${bWord.text}”"
        Kind.RHYME -> "“${aWord.text}” → “${bWord.text}”"
    }
}

/** How B's word answers A's, strongest first: [base] is where its score starts. */
enum class Kind(val base: Double) {
    SAME(3.0),

    /** Spelled differently, sounds the same ("night", "knight"; "tonight", "tonite"). */
    SOUNDS_ALIKE(2.7),
    RHYME(2.0),
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

    /** Trickster takes a looser link than [MIN_SCORE]: word play is one of its tricks. */
    const val TRICKSTER_MIN_SCORE = 2.5

    private const val TITLE_BONUS = 1.0

    /** The linkable words of a song title, as keys: "If I Lose Myself (Alesso vs OneRepublic)" → lose, myself. */
    fun titleKeys(title: String): Set<String> =
        title.substringBefore(" (").substringBefore(" - ").split(Regex("""[^\p{L}\p{N}']+"""))
            .map(WordKey::of).filter(WordKey::linkable).toSet()

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
    ): WordLink? = search(a, b, aFromSec, aToSec, bFromSec, bToSec, targetSec, bEnergyAt).link?.takeIf { it.score >= MIN_SCORE }

    /**
     * What a search found, good enough or not, for saying why a mix did or didn't use word play:
     * the best link, how many of A's words could end it, and how many of B's line openers could answer.
     */
    class Search(val link: WordLink?, val aWords: Int, val bOpeners: Int)

    fun search(
        a: TimedLyrics,
        b: TimedLyrics,
        aFromSec: Double,
        aToSec: Double,
        bFromSec: Double,
        bToSec: Double,
        targetSec: Double,
        bEnergyAt: (Double) -> Double = { 0.5 },
        /** Keys of the words in both songs' titles (see [titleKeys]). */
        titleWords: Set<String> = emptySet(),
    ): Search {
        val openers = b.words.withIndex()
            .filter { (i, w) -> w.startSec in bFromSec..bToSec && WordKey.linkable(w.key) && opensLine(b.words, i) }
            .map { it.value }
        val aWords = a.words.count { it.endSec in aFromSec..aToSec && WordKey.linkable(it.key) }
        if (openers.isEmpty()) return Search(null, aWords, 0)
        val byKey = openers.groupBy { it.key }
        val bySound = openers.groupBy { WordKey.sound(it.text) }
        val byRhyme = openers.groupBy { WordKey.rhyme(WordKey.sound(it.text)) }
        var best: WordLink? = null
        for (w in a.words) {
            if (w.endSec !in aFromSec..aToSec || !WordKey.linkable(w.key)) continue
            val sound = WordKey.sound(w.text)
            val rhymeKey = WordKey.rhyme(sound)
            // The same word; then one that sounds the same, spelled otherwise; then a rhyme.
            val same = byKey[w.key].orEmpty().map { it to Kind.SAME }
            val alike = bySound[sound].orEmpty().filter { it.key != w.key }.map { it to Kind.SOUNDS_ALIKE }
            val rhymes = if (rhymeKey != null && sound.length >= 3) {
                byRhyme[rhymeKey].orEmpty().filter { it.key != w.key && WordKey.sound(it.text) != sound }.map { it to Kind.RHYME }
            } else {
                emptyList()
            }
            for ((bw, kind) in same + alike + rhymes) {
                val rhyme = kind == Kind.RHYME
                var s = kind.base
                s += 0.1 * min(6, w.key.length - 3)
                if (w.lineEnd) s += 0.6
                // A hook both songs keep coming back to is the one people recognise; one song's hook still helps.
                val aHook = (a.counts[w.key] ?: 0) >= 3
                val bHook = (b.counts[bw.key] ?: 0) >= 3
                if (aHook && bHook) s += 0.8 else if (aHook || bHook) s += 0.4
                // A word from either song's title ("lose" into "If I Lose Myself") is the one the crowd knows.
                if (w.key in titleWords || bw.key in titleWords) s += TITLE_BONUS
                s -= abs(w.endSec - targetSec) / 60
                s -= (bw.startSec - bFromSec) / 120
                s += (0.7 - bEnergyAt(bw.startSec)) * 0.5
                if (s > (best?.score ?: Double.NEGATIVE_INFINITY)) best = WordLink(w, bw, rhyme, s, kind)
            }
        }
        return Search(best, aWords, openers.size)
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
     * Quick check for choosing the next song: does a word A sings (or one that sounds like it) after its first
     * [TransitionPlanner.MIN_PLAY_SEC] open a line in the part of B that may be skipped to
     * (its first 40%)? The planner then looks for the exact moment.
     */
    fun hasLink(a: TimedLyrics?, b: TimedLyrics?, bDurationSec: Double): Boolean {
        if (a == null || b == null) return false
        val aWords = a.words.filter { it.endSec >= TransitionPlanner.MIN_PLAY_SEC && WordKey.linkable(it.key) }
        if (aWords.isEmpty()) return false
        val aKeys = aWords.mapTo(HashSet()) { it.key }
        val aSounds = aWords.mapTo(HashSet()) { WordKey.sound(it.text) }
        return b.words.withIndex().any { (i, w) ->
            w.startSec <= 0.4 * bDurationSec && (w.key in aKeys || WordKey.sound(w.text) in aSounds) && opensLine(b.words, i)
        }
    }
}
