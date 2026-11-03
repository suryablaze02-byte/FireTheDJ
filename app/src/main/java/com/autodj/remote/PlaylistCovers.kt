package com.autodj.remote

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Covers for the Spotify and Apple Music playlist cards. Neither catalogue carries pictures, so
 * each cover is looked up once, when its card first shows (Spotify's public oEmbed answer, Apple
 * Music's public playlist page), downloaded and kept as a file. After [MAX_AGE_MS] it's looked up
 * again, since both services refresh their covers now and then; until then, or offline, the kept
 * file is used. Blocking: call off the main thread.
 */
class PlaylistCovers(context: Context, private val http: HttpGet = UrlHttpGet(BROWSER_AGENT)) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** The cover of a Spotify playlist by its id, or null when there's none to be had. */
    fun spotify(id: String): File? = cover("spotify-$id") {
        val page = URLEncoder.encode("https://open.spotify.com/playlist/$id", "UTF-8")
        JSONObject(http.get("https://open.spotify.com/oembed?url=$page")).optString("thumbnail_url")
    }

    /** The cover of an Apple Music playlist by its "<name>/pl.<id>" path, or null when there's none to be had. */
    fun appleMusic(path: String): File? = cover("apple-${path.substringAfterLast('/')}") {
        AppleMusicProvider.parsePage(http.get("https://music.apple.com/in/playlist/$path")).coverUrl
    }

    private fun cover(key: String, lookUp: () -> String?): File? {
        val file = File(dir, "$key.jpg")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) return file
        val bytes = runCatching { lookUp()?.takeIf { it.startsWith("https://") }?.let(::download) }.getOrNull()
        if (bytes != null) return file.apply { writeBytes(bytes) }
        // An old cover beats none when the service can't be reached.
        return file.takeIf { it.exists() }
    }

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) return null
            return conn.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    }
}
