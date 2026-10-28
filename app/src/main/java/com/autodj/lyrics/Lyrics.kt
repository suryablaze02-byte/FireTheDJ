package com.autodj.lyrics

import com.autodj.model.TrackInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One sung word, timed in the track's own seconds. [key] is the spelling-proof form used for
 * matching (see [WordKey]); [text] is how the lyrics wrote it, for showing.
 */
class LyricWord(
    val key: String,
    val text: String,
    val startSec: Double,
    val endSec: Double,
    /** First word of its line. */
    val lineStart: Boolean,
    /** Last word of its line. */
    val lineEnd: Boolean,
    /** Index of the line, to tell lines apart. */
    val line: Int,
)

/** A track's timed words, already shifted onto the audio ([offsetSec] was added to the lyric times). */
class TimedLyrics(val words: List<LyricWord>, val source: String, val offsetSec: Double) {
    /** How often each key is sung in the whole track: hooks and chants repeat. */
    val counts: Map<String, Int> = words.groupingBy { it.key }.eachCount()
}

/**
 * Turns any spelling of a word into one key, so the same word matches across songs whether it
 * was written in English, transliterated Telugu ("nuvvu", "nuvu") or a script converted to
 * Latin letters: lower case, letters only, long vowels shortened ("aa" → "a", "ee" → "i",
 * "oo" → "u"), aspirates dropped ("th" → "t", "dh" → "d"), doubled letters single, "w" → "v".
 */
object WordKey {
    fun of(word: String): String {
        var s = word.lowercase().filter { it in 'a'..'z' }
        if (s.isEmpty()) return s
        s = s.replace("ee", "i").replace("oo", "u").replace("ph", "f").replace('w', 'v').replace('z', 'j')
        s = s.replace(Regex("([bcdgjkpt])h"), "$1")
        val out = StringBuilder()
        for (c in s) if (out.isEmpty() || out.last() != c) out.append(c)
        return out.toString()
    }

    /** Words too common, or too short, to make a link on their own (keys). */
    private val STOP = setOf(
        // English
        "the", "and", "you", "your", "yor", "me", "my", "it", "its", "is", "in", "on", "to", "of", "we", "us", "oh", "yeah",
        "hey", "baby", "uh", "ah", "ohh", "ya", "yea", "but", "for", "with", "that", "this", "all", "are", "was", "be", "so",
        "do", "dont", "can", "cant", "get", "got", "just", "like", "now", "she", "he", "her", "him", "they", "them", "what",
        "when", "go", "na", "la", "da", "ho", "ye", "no", "not", "one", "out", "up", "down", "im", "ive", "let", "make",
        // Telugu, Tamil and Hindi fillers and particles
        "ra", "ga", "re", "ani", "idi", "adi", "ne", "nu", "na", "nan", "e", "o", "a", "le", "lo", "ki", "ke", "ka", "ko",
        "hai", "hain", "mein", "main", "tu", "tum", "ham", "hum", "se", "ki", "ke", "ka", "de", "di", "ra", "ri", "ro",
        "ena", "enna", "inka", "ika", "ante", "kada", "kani", "mari", "ela", "ila", "ala", "aye", "oye", "hoy",
    )

    /** True when [key] can carry a word-play link. */
    fun linkable(key: String) = key.length >= 3 && key !in STOP
}

/** Reads LRC lyrics ("[01:02.34] words", optionally with "<01:02.50>" word stamps). */
object Lrc {
    private val LINE_TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private fun sec(m: MatchResult): Double {
        val (mm, ss, frac) = m.destructured
        val f = if (frac.isEmpty()) 0.0 else frac.toDouble() / Math.pow(10.0, frac.length.toDouble())
        return mm.toInt() * 60 + ss.toInt() + f
    }

    /**
     * The words of [lrc] with their times. [toLatin] turns any script into Latin letters
     * (Telugu, Devanagari…); words still not in Latin letters after it are dropped.
     * Without word stamps, a line's words are spread over the time it is sung: about half a
     * second a word, never past the next line.
     */
    fun parse(lrc: String, toLatin: (String) -> String = { it }): List<LyricWord> {
        class Line(val start: Double, val text: String)
        val lines = mutableListOf<Line>()
        for (raw in lrc.lines()) {
            val tags = LINE_TAG.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim()
            for (t in tags) lines += Line(sec(t), text)
        }
        lines.sortBy { it.start }
        val out = mutableListOf<LyricWord>()
        for ((i, line) in lines.withIndex()) {
            if (line.text.isBlank()) continue
            val next = lines.getOrNull(i + 1)?.start ?: (line.start + 6.0)
            val stamped = WORD_TAG.findAll(line.text).toList()
            // (start, word) pairs.
            val timed: List<Pair<Double?, String>> = if (stamped.isNotEmpty()) {
                stamped.mapIndexed { k, m ->
                    val end = stamped.getOrNull(k + 1)?.range?.first ?: line.text.length
                    sec(m) to line.text.substring(m.range.last + 1, end)
                }.flatMap { (t, chunk) -> chunk.trim().split(Regex("\\s+")).mapIndexed { j, w -> (if (j == 0) t else null) to w } }
            } else {
                line.text.split(Regex("\\s+")).map { null to it }
            }
            // Shown as sung: "no," and "(mhm)" lose their punctuation, "might've" keeps its apostrophe.
            val words = timed.map { (t, w) -> t to toLatin(w).trim { !it.isLetterOrDigit() } }.filter { (_, w) -> WordKey.of(w).isNotEmpty() }
            if (words.isEmpty()) continue
            val span = min(next - line.start, max(1.0, words.size * 0.5))
            val each = span / words.size
            for ((k, pair) in words.withIndex()) {
                val (t, w) = pair
                val start = t ?: (line.start + k * each)
                val end = words.getOrNull(k + 1)?.first ?: (line.start + (k + 1) * each)
                out += LyricWord(WordKey.of(w), w, start, max(start + 0.1, min(end, next)), k == 0, k == words.size - 1, i)
            }
        }
        return out
    }
}

/**
 * Puts the lyric times on our audio. LRCLIB times lyrics to a recording to a fraction of a
 * second, so they are used exactly as timed; nothing is nudged by the analysis's singing
 * detector, which works in whole bars and would put the words a line out.
 *
 * The one question is which recording ours is, from the lengths ([versionGapSec] = our length
 * − theirs): within [SAME_VERSION_SEC] it is the same one, used as is. A longer or shorter one
 * (a video with an intro, an edit) has its extra seconds either at the start (shift by the
 * whole difference) or at the end (no shift); the singing picks between exactly those two, and
 * if it can't tell them apart clearly the lyrics aren't trusted (null): no lyrics beats wrong ones.
 */
object LyricsAligner {
    const val MAX_SHIFT_SEC = 20.0

    fun offset(words: List<LyricWord>, t: TrackInfo, versionGapSec: Double? = null): Double? {
        if (words.size < 8 || t.barCount < 16) return null
        val gap = versionGapSec?.takeIf { abs(it) <= MAX_SHIFT_SEC }
        fun score(shift: Double): Double {
            val sung = BooleanArray(t.barCount)
            for (w in words) {
                val bar = t.barAt(w.startSec + shift)
                if (bar in 0 until t.barCount) sung[bar] = true
            }
            var inSum = 0.0; var inN = 0; var outSum = 0.0; var outN = 0
            for (b in 0 until t.barCount) {
                if (sung[b]) { inSum += t.vocalness[b]; inN++ } else { outSum += t.vocalness[b]; outN++ }
            }
            if (inN < 4 || outN < 4) return Double.NEGATIVE_INFINITY
            // Contrast between sung and unsung bars.
            return inSum / inN - outSum / outN
        }
        return when {
            // The same recording (or no length to compare): as timed, if the words fall on singing at all.
            versionGapSec == null -> 0.0.takeIf { score(0.0) >= MIN_CONTRAST }
            abs(versionGapSec) <= SAME_VERSION_SEC -> 0.0
            gap == null -> null
            else -> {
                // Extra seconds at the start or at the end: whichever the singing clearly agrees with.
                val start = score(gap)
                val end = score(0.0)
                when {
                    start >= MIN_CONTRAST && start - end >= CLEAR_MARGIN -> gap
                    end >= MIN_CONTRAST && end - start >= CLEAR_MARGIN -> 0.0
                    else -> null
                }
            }
        }
    }

    /** How much more "sung" the lyric bars must sound than the rest. */
    private const val MIN_CONTRAST = 0.04

    /** How much more one placement must agree with the singing to be trusted over the other. */
    private const val CLEAR_MARGIN = 0.03

    /** Lengths this close are the same recording. */
    private const val SAME_VERSION_SEC = 4.0

    /**
     * Where the lyrics sit on our audio, found from the track's own singing: [vocal] is how
     * much singing there is, [fps] values a second (see BeatAnalyzer.vocalCurve). The lyrics
     * are slid over it a tenth of a second at a time, up to [MAX_SHIFT_SEC] either way, and the
     * place where the sung lines land on singing and the gaps between them on the band is
     * kept. A video's intro, a radio edit's shorter start or a few seconds of silence are all
     * found this way, however long our version is. The lyrics' own timing (no shift) or the
     * whole length difference are preferred unless another place fits clearly better; null
     * when nothing fits well enough to trust.
     */
    fun fineOffset(words: List<LyricWord>, vocal: FloatArray, fps: Double, versionGapSec: Double? = null): Double? {
        if (words.size < 8 || vocal.size < fps * 30) return null
        val n = vocal.size
        // Singing against the seconds around it, so a quiet verse still counts against its gaps.
        val half = (3 * fps).toInt()
        val sums = DoubleArray(n + 1)
        for (i in 0 until n) sums[i + 1] = sums[i] + vocal[i]
        val v = DoubleArray(n) {
            val lo = max(0, it - half)
            val hi = min(n, it + half + 1)
            vocal[it] - (sums[hi] - sums[lo]) / (hi - lo)
        }
        val mean = v.average()
        val sd = kotlin.math.sqrt(v.sumOf { (it - mean) * (it - mean) } / n).takeIf { it > 1e-9 } ?: return null
        for (i in 0 until n) v[i] = (v[i] - mean) / sd
        // Which frames the lyrics say are sung, from the first word to the last.
        val first = (words.first().startSec * fps).roundToInt()
        val last = (words.maxOf { it.endSec } * fps).roundToInt()
        if (last - first < 20 * fps) return null
        val sung = BooleanArray(last - first + 1)
        for (w in words) {
            for (f in (w.startSec * fps).roundToInt()..(w.endSec * fps).roundToInt()) {
                if (f in first..last) sung[f - first] = true
            }
        }
        fun score(shiftFrames: Int): Double {
            var inSum = 0.0; var inN = 0; var outSum = 0.0; var outN = 0
            for (j in sung.indices) {
                val f = first + j + shiftFrames
                if (f !in 0 until n) continue
                if (sung[j]) { inSum += v[f]; inN++ } else { outSum += v[f]; outN++ }
            }
            if (inN < 10 * fps || outN < 3 * fps) return Double.NEGATIVE_INFINITY
            return inSum / inN - outSum / outN
        }
        val maxShift = (MAX_SHIFT_SEC * fps).toInt()
        val scores = (-maxShift..maxShift).associateWith(::score)
        val (best, bestScore) = scores.maxBy { it.value }
        if (bestScore < MIN_FINE) return null
        // The lyrics' own timing, or our version's extra length all at the start, when either fits about as well.
        val priors = listOfNotNull(0, versionGapSec?.takeIf { abs(it) <= MAX_SHIFT_SEC }?.let { (it * fps).roundToInt() })
        val prior = priors.maxBy { scores[it] ?: Double.NEGATIVE_INFINITY }
        val chosen = if (bestScore - (scores[prior] ?: Double.NEGATIVE_INFINITY) <= PRIOR_MARGIN) prior else best
        return chosen / fps
    }

    /** How much more the sung frames must sound like singing than the gaps (in standard deviations). */
    private const val MIN_FINE = 0.25

    /** How close the lyrics' own timing must fit to be kept over a slightly better fitting shift. */
    private const val PRIOR_MARGIN = 0.06

    fun align(words: List<LyricWord>, t: TrackInfo, source: String, versionGapSec: Double? = null, vocal: FloatArray? = null, fps: Double = 10.0): TimedLyrics? {
        val off = vocal?.let { fineOffset(words, it, fps, versionGapSec) } ?: offset(words, t, versionGapSec) ?: return null
        return TimedLyrics(
            words.map { LyricWord(it.key, it.text, it.startSec + off, it.endSec + off, it.lineStart, it.lineEnd, it.line) },
            source, off,
        )
    }
}
