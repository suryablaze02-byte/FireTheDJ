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
    /**
     * [title] and [artist] already cleaned of upload notes; [durationSec] is our track's length;
     * [version] the remix or edit it is ("gaullin"), whose lyrics must be that version's, or null.
     */
    fun find(title: String, artist: String, durationSec: Double, version: String?): FoundLyrics?
}

object LyricsSources {
    /** Versions whose length differs more than this are a different edit, timed differently. */
    const val MAX_LENGTH_GAP_SEC = 25.0

    private val USER_AGENT = "FireTheDJ (https://github.com/suryablaze02-byte/FireTheDJ)"

    /** Tried first to last. LRCLIB is an open, community-built catalogue made for apps like this. */
    val chain: List<LyricsSource> = listOf(Lrclib(::httpGet))

    /** No source could be asked (offline, timed out, the catalogue overloaded): not the same as "no lyrics". */
    class LookupFailed(cause: Throwable) : java.io.IOException(cause.message, cause)

    /**
     * The first timed lyrics any source has for the song, or null when the sources answered and
     * none has it. Throws [LookupFailed] when no source could answer, so the song is asked about
     * again later instead of being written off.
     */
    fun find(title: String, artist: String, durationSec: Double): FoundLyrics? {
        val version = versionOf(title)
        val t = cleanTitle(title)
        val a = cleanArtist(artist)
        if (t.isBlank()) return null
        var failure: Throwable? = null
        var answered = false
        for (source in chain) {
            val result = runCatching { source.find(t, a, durationSec, version) }
            result.getOrNull()?.let { return it }
            if (result.isSuccess) answered = true else failure = result.exceptionOrNull()
        }
        if (!answered && failure != null) throw LookupFailed(failure)
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

    /** Notes that keep the original arrangement: its lyrics still line up. */
    private val SAME_ARRANGEMENT = Regex("""\b(radio|original|extended|clean|explicit|single|album|official)\b""", RegexOption.IGNORE_CASE)

    /** A bracketed or dashed note naming a remix, bootleg, edit or other rework, with the words before it. */
    private val VERSION_NOTE = Regex("""[(\[]([^)\]]*?)\s*\b(remix|rmx|bootleg|flip|rework|edit|mix|vip|mashup|cover|remake)\b[^)\]]*[)\]]|\s-\s(.*?)\s*\b(remix|rmx|bootleg|flip|rework|edit|mix|vip)\b""", RegexOption.IGNORE_CASE)

    /**
     * The remix or rework a title names, as the word its lyrics must carry too: "Sweater
     * Weather (Gaullin Remix)" → "gaullin", "Titanium (David Guetta & MORTEN Future Rave Remix)"
     * → "guetta". Null for the original arrangement ("Radio Edit", "Original Mix", "Extended Mix").
     */
    fun versionOf(title: String): String? {
        val m = VERSION_NOTE.find(title) ?: return null
        val note = m.value
        if (SAME_ARRANGEMENT.containsMatchIn(note)) return null
        val who = (m.groups[1]?.value ?: m.groups[3]?.value).orEmpty()
        // A word of the remixer's name: the longest in the note once styles ("Future Rave") are left out.
        val words = who.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 && it !in VERSION_STOP }
        return words.maxByOrNull { it.length } ?: m.groups[2]?.value?.lowercase() ?: m.groups[4]?.value?.lowercase()
    }

    /** Words in a remix note that aren't the remixer's name. */
    private val VERSION_STOP = setOf("feat", "featuring", "the", "and", "future", "rave", "house", "club", "dance", "deep", "tech", "remix", "edit", "mix", "version", "live")

    /** Record labels and "- Topic" channels aren't the artist; leave those out of the search. */
    fun cleanArtist(artist: String): String {
        val a = artist.replace(Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE), "").trim()
        return if (Regex("""\b(music|records|series|official|films|movies|entertainment)\b""", RegexOption.IGNORE_CASE).containsMatchIn(a)) "" else a
    }

    /** When the last request went out: requests from songs analysed side by side are spaced out, to go easy on a free catalogue. */
    private var lastRequest = 0L
    private val pace = Any()
    private const val REQUEST_GAP_MS = 400L

    /** Spacing after the catalogue said it was busy, kept for [BUSY_FOR_MS]. */
    private const val BUSY_GAP_MS = 1500L
    private const val BUSY_FOR_MS = 60_000L
    private var busyUntil = 0L

    /** Waits before each retry of a request the catalogue turned away. */
    private val RETRY_WAITS_MS = longArrayOf(2_000, 5_000, 10_000)

    private fun httpGet(url: String): String? {
        var attempt = 0
        while (true) {
            try {
                return request(url)
            } catch (e: java.io.IOException) {
                // A busy catalogue ("503 overloaded") or a dropped connection: slow down, wait, try again.
                if (attempt >= RETRY_WAITS_MS.size || e.message == "HTTP 400") throw e
                busyUntil = System.currentTimeMillis() + BUSY_FOR_MS
                Thread.sleep(RETRY_WAITS_MS[attempt++])
            }
        }
    }

    private fun request(url: String): String? {
        synchronized(pace) {
            val gap = if (System.currentTimeMillis() < busyUntil) BUSY_GAP_MS else REQUEST_GAP_MS
            val wait = lastRequest + gap - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            lastRequest = System.currentTimeMillis()
        }
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", USER_AGENT)
            // 404 is the catalogue saying it hasn't got the song; any other error is a failed request.
            when (val code = c.responseCode) {
                200 -> c.inputStream.bufferedReader().readText()
                404 -> null
                else -> throw java.io.IOException("HTTP $code")
            }
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
    override fun find(title: String, artist: String, durationSec: Double, version: String?): FoundLyrics? {
        val base = "https://lrclib.net/api"
        val pick = { body: String -> pick(JSONArray(body), durationSec, title, artist, version) }
        if (artist.isNotBlank()) {
            get("$base/get?track_name=${LyricsSources.enc(title)}&artist_name=${LyricsSources.enc(artist)}&duration=${durationSec.toInt()}")
                ?.let { pick(JSONArray().put(JSONObject(it)), durationSec, title, artist, version) }?.let { return it }
            get("$base/search?track_name=${LyricsSources.enc(title)}&artist_name=${LyricsSources.enc(artist)}")?.let(pick)?.let { return it }
        }
        return get("$base/search?q=${LyricsSources.enc(title)}")?.let(pick)
    }

    /** The timed result for our song closest in length to ours, within [LyricsSources.MAX_LENGTH_GAP_SEC]. */
    fun pick(results: JSONArray, durationSec: Double, title: String, artist: String, version: String? = null): FoundLyrics? {
        var best: FoundLyrics? = null
        var bestGap = Double.MAX_VALUE
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val lrc = o.optString("syncedLyrics", "")
            if (lrc.isBlank() || lrc == "null") continue
            val theirs = o.optString("trackName", "")
            if (!sameTitle(title, theirs)) continue
            // A remix is arranged differently: only lyrics timed to that remix line up with it.
            if (version != null && !theirs.lowercase().contains(version)) continue
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
