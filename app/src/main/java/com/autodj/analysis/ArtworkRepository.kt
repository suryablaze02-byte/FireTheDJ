package com.autodj.analysis

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.autodj.model.TrackInfo
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Finds cover art for a track: the picture embedded in the file first, then the album cover
 * from the iTunes Search API (free, no key) by artist and title. A streamed song's provider
 * thumbnail (often a frame of its music video) is the last resort. Results are cached as files;
 * a track with none gets a generated cover in the UI.
 */
class ArtworkRepository(private val context: Context) {
    private val dir = File(context.filesDir, "artwork").apply { mkdirs() }
    private val misses = File(dir, "misses.txt")

    init {
        // 2.0.1: streamed songs had cached video thumbnails; look again for their album covers, once.
        val mark = File(dir, ".album-covers")
        if (!mark.exists()) {
            clear()
            mark.createNewFile()
        }
    }

    /** When the last iTunes lookup went out: they're spaced so a big library isn't refused. */
    private var lastLookup = 0L

    fun cached(uri: String): File? = fileFor(uri).takeIf { it.exists() }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Blocking; call off the main thread. Returns the artwork file, or null if none was found.
     * [artworkUrl] is cover art a streaming provider supplied; it is used instead of reading
     * the (remote) file.
     */
    fun fetch(track: TrackInfo, artworkUrl: String? = null): File? {
        cached(track.uri)?.let { return it }
        val key = hash(track.uri)
        if (misses.exists() && misses.readLines().contains(key)) return null
        val remote = track.uri.startsWith("https://")
        val bytes = (if (remote) null else embedded(track.uri))
            ?: lookupOnline(track.artist, track.title)
            ?: (if (artworkUrl != null) runCatching { get(artworkUrl) }.getOrNull() else null)
        if (bytes == null) {
            misses.appendText(key + "\n")
            return null
        }
        return fileFor(track.uri).apply { writeBytes(bytes) }
    }

    private fun embedded(uri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.embeddedPicture
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun lookupOnline(artist: String, rawTitle: String): ByteArray? {
        // Title-only searches match the wrong song too often; require an artist.
        val title = cleanTitle(rawTitle, artist)
        if (artist.isBlank() || title.isBlank()) return null
        return try {
            val term = URLEncoder.encode("$artist $title", "UTF-8")
            val json = JSONObject(search("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=10").decodeToString())
            val results = json.getJSONArray("results")
            val wanted = normalize(artist)
            val song = normalize(title)
            // The same artist and the same song: an artist match alone can bring another album's cover.
            val match = (0 until results.length()).map { results.getJSONObject(it) }.firstOrNull {
                val found = normalize(it.optString("artistName"))
                val name = normalize(cleanTitle(it.optString("trackName"), ""))
                found.isNotEmpty() && (found.contains(wanted) || wanted.contains(found)) &&
                    name.isNotEmpty() && (name.contains(song) || song.contains(name))
            } ?: return null
            val art = match.optString("artworkUrl100").takeIf { it.isNotEmpty() } ?: return null
            get(art.replace("100x100bb", "600x600bb"))
        } catch (_: Exception) {
            null
        }
    }

    /** An iTunes search, at most about 20 a minute (its limit); one refused search is retried after a minute. */
    @Synchronized
    private fun search(url: String): ByteArray {
        repeat(2) { attempt ->
            val wait = lastLookup + LOOKUP_GAP_MS - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            lastLookup = System.currentTimeMillis()
            try {
                return get(url)
            } catch (e: IllegalStateException) {
                // 403 or 429: too many searches. Wait it out once, then give up for this song.
                if (attempt == 0 && (e.message == "HTTP 403" || e.message == "HTTP 429")) Thread.sleep(60_000) else throw e
            }
        }
        error("unreachable")
    }

    private fun get(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 8000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    internal companion object {
        private const val LOOKUP_GAP_MS = 3_200L

        /**
         * A video or listing title down to the song's name: no "(Official Video)" or "[Lyrics]",
         * no "ft. Someone", and no "Artist - " in front.
         */
        fun cleanTitle(title: String, artist: String): String {
            var t = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
            t = t.replace(Regex("""\s+(ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            val dash = t.indexOf(" - ")
            if (dash > 0 && (artist.isBlank() || t.substring(0, dash).contains(artist, ignoreCase = true))) t = t.substring(dash + 3)
            return t.trim()
        }
    }

    private fun fileFor(uri: String) = File(dir, hash(uri))

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
