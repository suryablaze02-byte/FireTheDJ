package com.autodj.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** The providers AutoDJ ships with; add new third-party clients here. Order = matching priority. */
object StreamProviders {
    fun default(http: HttpGet = UrlHttpGet(), jamendoClientId: String = ""): List<StreamProvider> = listOf(
        AudiusProvider(http),
        JamendoProvider(http, jamendoClientId),
        InternetArchiveProvider(http),
        YouTubeMusicProvider(),
        SpotifyProvider(),
        WebPlaylistProvider(http),
    )
}

/** [HttpGet] over HttpURLConnection, following redirects. */
class UrlHttpGet(private val userAgent: String = "FireTheDJ/1.0") : HttpGet {
    override fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "application/json, audio/x-mpegurl, */*")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} from ${URL(url).host}")
            return conn.inputStream.use { it.readBytes() }.decodeToString()
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Audius (open music platform, public API, no key): playlists and albums at
 * `https://audius.co/<artist>/playlist/<name>` or `/album/<name>`. Only tracks the artist
 * made freely streamable are taken; stream URLs point at the API, which redirects to
 * freshly signed audio each time.
 */
class AudiusProvider(private val http: HttpGet, private val appName: String = "autodj") : StreamProvider {
    override val id = "audius"
    override val displayName = "Audius"
    override val urlRule = PlaylistUrlRule(
        hosts = setOf("audius.co"),
        pathPattern = Regex("""/([^/]+/(?:playlist|album)/[^/]+)/?"""),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        val pageUrl = enc("https://audius.co/${ref.playlistId}")
        val resolved = JSONObject(http.get("$API/resolve?url=$pageUrl&app_name=$appName"))
        // The playlist lookup answers with a one-element array; be lenient about either shape.
        val playlist = when (val data = resolved.get("data")) {
            is JSONArray -> data.optJSONObject(0)
            is JSONObject -> data
            else -> null
        } ?: throw IOException("Audius has no playlist at that link")
        val playlistId = playlist.getString("id")
        val tracks = JSONObject(http.get("$API/playlists/$playlistId/tracks?app_name=$appName")).getJSONArray("data")
        val out = (0 until tracks.length()).map { tracks.getJSONObject(it) }
            .filter { streamable(it) }
            .map { t ->
                RemoteTrack(
                    title = t.optString("title"),
                    artist = t.optJSONObject("user")?.optString("name").orEmpty(),
                    streamUrl = "$API/tracks/${t.getString("id")}/stream?app_name=$appName",
                    providerId = id,
                    providerTrackId = t.getString("id"),
                    artworkUrl = t.optJSONObject("artwork")?.optString("480x480")?.takeIf { it.startsWith("https://") },
                    durationSec = t.optDouble("duration").takeIf { !it.isNaN() },
                    genre = t.optString("genre").takeIf { it.isNotBlank() },
                    storable = t.optJSONObject("access")?.optBoolean("download", false) ?: t.optBoolean("is_downloadable", false),
                )
            }
        return RemotePlaylist(ref.link, id, playlistId, playlist.optString("playlist_name", "Audius playlist"), out)
    }

    private fun streamable(t: JSONObject): Boolean =
        t.optBoolean("is_streamable", true) && !t.optBoolean("is_stream_gated", false) &&
            t.optJSONObject("access")?.optBoolean("stream", true) != false

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val API = "https://api.audius.co/v1"
    }
}

/**
 * Plain web links: an M3U playlist of audio URLs (`#EXTINF:<sec>,Artist - Title` lines are
 * read for names), or a single audio file. For music you host or are allowed to stream.
 */
class WebPlaylistProvider(private val http: HttpGet) : StreamProvider {
    override val id = "web"
    override val displayName = "M3U or audio link"
    override val urlRule = PlaylistUrlRule(
        hosts = emptySet(),
        pathPattern = Regex("""(.+\.(?:m3u|mp3|m4a|aac|flac|ogg|opus|wav))""", RegexOption.IGNORE_CASE),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        val file = decodePath(ref.playlistId.substringAfterLast('/'))
        val name = file.substringBeforeLast('.')
        if (!file.endsWith(".m3u", ignoreCase = true)) {
            return RemotePlaylist(ref.link, id, ref.link, name, listOf(track(ref.link, name, null)))
        }
        val base = URI(ref.link)
        val tracks = mutableListOf<RemoteTrack>()
        var pending: Pair<String, Double?>? = null
        for (raw in http.get(ref.link).lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val info = line.substringAfter(':')
                    pending = info.substringAfter(',', "").trim() to info.substringBefore(',').trim().toDoubleOrNull()?.takeIf { it > 0 }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val url = runCatching { base.resolve(line).toString() }.getOrNull()
                    if (url != null && url.startsWith("https://")) {
                        val label = pending?.first?.ifBlank { null }
                            ?: decodePath(url.substringBefore('?').substringAfterLast('/')).substringBeforeLast('.')
                        tracks += track(url, label, pending?.second)
                    }
                    pending = null
                }
            }
        }
        if (tracks.isEmpty()) throw IOException("No https audio links in that playlist")
        return RemotePlaylist(ref.link, id, ref.link, name, tracks)
    }

    /** Percent-decodes a path segment; unlike form encoding, "+" stays a plus. */
    private fun decodePath(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    /** "Artist - Title" labels are split the same way as untagged file names. */
    private fun track(url: String, label: String, durationSec: Double?): RemoteTrack {
        val parts = label.split(" - ", limit = 2)
        val (artist, title) = if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to label.trim()
        return RemoteTrack(title, artist, url, id, url, durationSec = durationSec)
    }
}
