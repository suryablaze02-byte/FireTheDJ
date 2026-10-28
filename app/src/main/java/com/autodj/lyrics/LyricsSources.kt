package com.autodj.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Timed lyrics one source found for a song: LRC text and the length of the version it was timed to. */
class FoundLyrics(val lrc: String, val source: String, val durationSec: Double?)

/**
 * A place timed lyrics can come from. Sources are tried in order until one has the song
 * ([LyricsSources.chain]), the way lyrics players combine many catalogues; adding a catalogue
 * means adding one of these.
 */
fun interface LyricsSource {
    /** [title] and [artist] already cleaned of upload notes; [durationSec] is our track's length. */
    fun find(title: String, artist: String, durationSec: Double): FoundLyrics?
}

object LyricsSources {
    /** Versions whose length differs more than this are a different edit, timed differently. */
    const val MAX_LENGTH_GAP_SEC = 25.0

    private val USER_AGENT = "FireTheDJ (https://github.com/suryablaze02-byte/FireTheDJ)"

    /** Tried first to last. LRCLIB is an open, community-built catalogue made for apps like this. */
    val chain: List<LyricsSource> = listOf(Lrclib(::httpGet))

    /** The first timed lyrics any source has for the song, or null. */
    fun find(title: String, artist: String, durationSec: Double): FoundLyrics? {
        val t = cleanTitle(title)
        val a = cleanArtist(artist)
        if (t.isBlank()) return null
        for (source in chain) {
            val found = runCatching { source.find(t, a, durationSec) }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    /**
     * The song's name out of an upload title: "Time To Party Full Video Song | Attarintiki
     * Daredi || Pawan Kalyan" → "Time To Party"; "Chuttamalle Lyrical | Devara" → "Chuttamalle";
     * "Kissik (From \"Pushpa 2\")" → "Kissik".
     */
    fun cleanTitle(title: String): String = title
        .replace(Regex("""#\w+"""), " ")
        .split('|', '｜').first()
        .replace(Regex("""\(\s*from\b.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(full\s+)?(video|audio|lyrical|lyric|lyrics)(\s+(song|video))?\b.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[(\[].*?[)\]]"""), "")
        .replace(Regex("""\s+-\s+.*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Record labels and "- Topic" channels aren't the artist; leave those out of the search. */
    fun cleanArtist(artist: String): String {
        val a = artist.replace(Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE), "").trim()
        return if (Regex("""\b(music|records|series|official|films|movies|entertainment)\b""", RegexOption.IGNORE_CASE).containsMatchIn(a)) "" else a
    }

    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", USER_AGENT)
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
/**
 * LRCLIB (lrclib.net): an exact lookup by title, artist and length first, then searches by
 * title and artist and by title alone. Every result must be the same song: its title must
 * match ours, and its artist must too whenever we know ours (many songs share a title:
 * "Finesse" is several different songs). When our artist is unknown (an upload by a label),
 * the length must match closely instead. Of the results that pass, the closest in length wins.
 */
class Lrclib(private val get: (String) -> String?) : LyricsSource {
    override fun find(title: String, artist: String, durationSec: Double): FoundLyrics? {
        val base = "https://lrclib.net/api"
        val pick = { body: String -> pick(JSONArray(body), durationSec, title, artist) }
        if (artist.isNotBlank()) {
            get("$base/get?track_name=${LyricsSources.enc(title)}&artist_name=${LyricsSources.enc(artist)}&duration=${durationSec.toInt()}")
                ?.let { pick(JSONArray().put(JSONObject(it)), durationSec, title, artist) }?.let { return it }
            get("$base/search?track_name=${LyricsSources.enc(title)}&artist_name=${LyricsSources.enc(artist)}")?.let(pick)?.let { return it }
        }
        return get("$base/search?q=${LyricsSources.enc(title)}")?.let(pick)
    }

    /** The timed result for our song closest in length to ours, within [LyricsSources.MAX_LENGTH_GAP_SEC]. */
    fun pick(results: JSONArray, durationSec: Double, title: String, artist: String): FoundLyrics? {
        var best: FoundLyrics? = null
        var bestGap = Double.MAX_VALUE
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val lrc = o.optString("syncedLyrics", "")
            if (lrc.isBlank() || lrc == "null") continue
            if (!sameTitle(title, o.optString("trackName", ""))) continue
            val len = o.optDouble("duration", Double.NaN)
            val gap = if (len.isNaN()) LyricsSources.MAX_LENGTH_GAP_SEC else abs(len - durationSec)
            val known = artist.isNotBlank()
            if (known && !sameArtist(artist, o.optString("artistName", ""))) continue
            // Without an artist to check, only a near-identical length vouches for it.
            val maxGap = if (known) LyricsSources.MAX_LENGTH_GAP_SEC else UNKNOWN_ARTIST_GAP_SEC
            if (gap <= maxGap && gap < bestGap) {
                bestGap = gap
                best = FoundLyrics(lrc, "LRCLIB", len.takeUnless { it.isNaN() })
            }
        }
        return best
    }

    private companion object {
        const val UNKNOWN_ARTIST_GAP_SEC = 3.0

        fun words(s: String) = s.lowercase()
            .replace(Regex("""[(\[].*?[)\]]"""), " ") // "(feat. …)", "[Remix]"
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), " ")
            .split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

        /**
         * The same title, ignoring case, punctuation, "(feat. …)", "[Remix]" and " - Radio Edit"
         * style notes. Anything more ("Finesse Freestyle") is a different song.
         */
        fun sameTitle(ours: String, theirs: String): Boolean {
            val a = words(ours.substringBefore(" - ")).joinToString(" ")
            val b = words(theirs.substringBefore(" - ")).joinToString(" ")
            return a.isNotEmpty() && a == b
        }

        /** At least one artist name in common ("Bruno Mars, Cardi B" and "Bruno Mars" share one). */
        fun sameArtist(ours: String, theirs: String): Boolean {
            val names = { s: String -> s.lowercase().split(Regex("""\s*(,|&|\band\b|\bx\b|\bfeat\.?|\bft\.?|\bwith\b)\s*""")).map { it.trim() }.filter { it.length >= 2 } }
            val a = names(ours)
            val b = names(theirs)
            return a.any { x -> b.any { y -> x == y || x.contains(y) || y.contains(x) } }
        }
    }
}
