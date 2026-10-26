package com.autodj.analysis

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Asks the iTunes Search API (free, no key) for a song's genre by artist and title, for
 * tracks whose tags and audio left them unclassified. Songs the store doesn't know are
 * remembered so they are not looked up again on every launch; network errors are not.
 */
class GenreLookup(context: Context) {
    private val misses = File(context.filesDir, "genre-misses.txt")

    fun clear() {
        misses.delete()
    }

    /**
     * Blocking; call off the main thread. The store's genre ("Hip-Hop/Rap", "Dance"…),
     * or null when the song wasn't found or the device is offline.
     */
    fun lookup(uri: String, artist: String, title: String): String? {
        val song = searchTitle(title)
        if (song.isBlank()) return null
        if (misses.exists() && misses.readLines().contains(uri)) return null
        val found = try {
            search(artist, song)
        } catch (_: IOException) {
            return null // offline: try again next time
        } catch (_: Exception) {
            null
        }
        if (found == null) misses.appendText(uri + "\n")
        return found
    }

    private fun search(artist: String, song: String): String? {
        val term = URLEncoder.encode("$artist $song".trim(), "UTF-8")
        val json = JSONObject(get("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=10"))
        val results = json.getJSONArray("results")
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
        val wantedArtist = normalize(artist)
        val wantedSong = normalize(song)
        val match = candidates.firstOrNull { r ->
            val a = normalize(r.optString("artistName"))
            val t = normalize(searchTitle(r.optString("trackName")))
            val artistOk = wantedArtist.isEmpty() || (a.isNotEmpty() && (a.contains(wantedArtist) || wantedArtist.contains(a)))
            // Without an artist only an exact title is trusted; with one, a close title will do.
            val titleOk = if (wantedArtist.isEmpty()) t == wantedSong else t.contains(wantedSong) || wantedSong.contains(t)
            artistOk && titleOk && t.isNotEmpty()
        } ?: return null
        return match.optString("primaryGenreName").takeIf { it.isNotBlank() }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 8000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }.decodeToString()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** "You (Original mix)", "Take Me Up (ft. X)" → the song name the store lists. */
        fun searchTitle(title: String): String = title
            .replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""\s+(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE), "")
            .trim()

        fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    }
}
