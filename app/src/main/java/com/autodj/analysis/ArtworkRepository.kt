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
 * Finds cover art for a track: the picture embedded in the file first, then the iTunes
 * Search API (free, no key) by artist and title. Results are cached as files; a track with
 * neither gets a generated cover in the UI.
 */
class ArtworkRepository(private val context: Context) {
    private val dir = File(context.filesDir, "artwork").apply { mkdirs() }
    private val misses = File(dir, "misses.txt")

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
        val bytes = (if (artworkUrl != null) runCatching { get(artworkUrl) }.getOrNull() else null)
            ?: (if (remote) null else embedded(track.uri))
            ?: lookupOnline(track.artist, track.title)
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

    private fun lookupOnline(artist: String, title: String): ByteArray? {
        // Title-only searches match the wrong song too often; require an artist.
        if (artist.isBlank() || title.isBlank()) return null
        return try {
            val term = URLEncoder.encode("$artist $title", "UTF-8")
            val json = JSONObject(get("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=5").decodeToString())
            val results = json.getJSONArray("results")
            val wanted = normalize(artist)
            val match = (0 until results.length()).map { results.getJSONObject(it) }.firstOrNull {
                val found = normalize(it.optString("artistName"))
                found.isNotEmpty() && (found.contains(wanted) || wanted.contains(found))
            } ?: return null
            val art = match.optString("artworkUrl100").takeIf { it.isNotEmpty() } ?: return null
            get(art.replace("100x100bb", "600x600bb"))
        } catch (_: Exception) {
            null
        }
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

    private fun fileFor(uri: String) = File(dir, hash(uri))

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
