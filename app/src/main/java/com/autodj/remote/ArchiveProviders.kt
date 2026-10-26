package com.autodj.remote

import com.autodj.analysis.Genres
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Jamendo (Creative Commons music, public API with a free client id): playlists and albums at
 * `https://www.jamendo.com/playlist/<id>/…` or `/album/<id>/…`. Tracks are kept only where
 * the artist allows downloads; the rest stream each time.
 */
class JamendoProvider(private val http: HttpGet, private val clientId: String) : StreamProvider {
    override val id = "jamendo"
    override val displayName = "Jamendo"
    override val urlRule = PlaylistUrlRule(
        hosts = setOf("jamendo.com"),
        // Optional language prefix ("/en/album/…"); the id keeps its kind: "album/123".
        pathPattern = Regex("""(?:/[a-z]{2})?/((?:playlist|album)/\d+)(?:/.*)?"""),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        if (clientId.isBlank()) {
            throw IOException("add your free Jamendo client id (devportal.jamendo.com) as jamendo.clientId in local.properties")
        }
        val (kind, number) = ref.playlistId.split('/', limit = 2)
        val endpoint = if (kind == "album") "albums" else "playlists"
        val body = JSONObject(http.get("$API/$endpoint/tracks/?client_id=$clientId&format=json&id=$number&audioformat=mp32&limit=1"))
        val headers = body.optJSONObject("headers")
        if (headers?.optString("status") == "failed") throw IOException(headers.optString("error_message", "request failed"))
        val list = body.optJSONArray("results")?.optJSONObject(0) ?: throw IOException("Jamendo has no $kind $number")
        val albumArtist = list.optString("artist_name")
        val albumImage = list.optString("image").takeIf { it.startsWith("https://") }
        val tracks = list.optJSONArray("tracks") ?: JSONArray()
        val out = (0 until tracks.length()).map { tracks.getJSONObject(it) }
            .filter { it.optString("audio").startsWith("https://") }
            .map { t ->
                RemoteTrack(
                    title = t.optString("name"),
                    artist = t.optString("artist_name").ifBlank { albumArtist },
                    streamUrl = t.getString("audio"),
                    providerId = id,
                    providerTrackId = t.optString("id"),
                    artworkUrl = (t.optString("image").takeIf { it.startsWith("https://") } ?: albumImage),
                    durationSec = t.optString("duration").toDoubleOrNull(),
                    storable = t.optBoolean("audiodownload_allowed", false),
                )
            }
        return RemotePlaylist(ref.link, id, ref.playlistId, list.optString("name", "Jamendo $kind"), out)
    }

    private companion object {
        const val API = "https://api.jamendo.com/v3.0"
    }
}

/**
 * Internet Archive items (`https://archive.org/details/<identifier>`): netlabel releases,
 * live sets and other freely licensed audio. Each recording is taken once, in the best
 * format players handle well (MP3, else Ogg, else FLAC), in track order.
 */
class InternetArchiveProvider(private val http: HttpGet) : StreamProvider {
    override val id = "archive"
    override val displayName = "Internet Archive"
    override val urlRule = PlaylistUrlRule(
        hosts = setOf("archive.org"),
        pathPattern = Regex("""/details/([A-Za-z0-9._\-]+)(?:/.*)?"""),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        val item = JSONObject(http.get("https://archive.org/metadata/${ref.playlistId}"))
        val meta = item.optJSONObject("metadata") ?: throw IOException("archive.org has no item \"${ref.playlistId}\"")
        val creator = text(meta.opt("creator"))
        val genre = genreFrom(text(meta.opt("subject")))
        val files = item.optJSONArray("files") ?: JSONArray()
        val audio = (0 until files.length()).map { files.getJSONObject(it) }.filter { formatRank(it.optString("format")) != null }
        // An original (say FLAC) and its derived MP3 are the same recording: keep one per original.
        val recordings = audio.groupBy { it.optString("original").ifBlank { it.getString("name") } }
            .map { (_, versions) -> versions.minWith(compareBy({ formatRank(it.optString("format"))!! }, { it.optString("source") != "original" })) }
            .sortedWith(compareBy({ trackNumber(it.optString("track")) }, { it.getString("name") }))
        if (recordings.isEmpty()) throw IOException("that item has no playable audio (collections aren't supported, open one release)")
        val art = "https://archive.org/services/img/${ref.playlistId}"
        val tracks = recordings.map { f ->
            val name = f.getString("name")
            RemoteTrack(
                title = f.optString("title").ifBlank { name.substringAfterLast('/').substringBeforeLast('.') },
                artist = f.optString("artist").ifBlank { f.optString("creator") }.ifBlank { creator },
                streamUrl = "https://archive.org/download/${ref.playlistId}/${encodePath(name)}",
                providerId = id,
                providerTrackId = "${ref.playlistId}/$name",
                artworkUrl = art,
                durationSec = f.optString("length").toDoubleOrNull(),
                genre = genre,
            )
        }
        return RemotePlaylist(ref.link, id, ref.playlistId, text(meta.opt("title")).ifBlank { ref.playlistId }, tracks)
    }

    /** Lower is better; null = not audio we play. */
    private fun formatRank(format: String): Int? {
        val f = format.lowercase()
        return when {
            "mp3" in f -> 0
            "ogg" in f -> 1
            "flac" in f -> 2
            else -> null
        }
    }

    /** "3", "3/12", "03" → 3; missing → after everything numbered. */
    private fun trackNumber(s: String) = s.substringBefore('/').trim().toIntOrNull() ?: Int.MAX_VALUE

    /** Metadata values are a string or an array of strings. */
    private fun text(v: Any?): String = when (v) {
        is JSONArray -> (0 until v.length()).joinToString("; ") { v.optString(it) }
        null, JSONObject.NULL -> ""
        else -> v.toString()
    }

    /** The first subject term that names a genre family ("minimal; techno; house" → "techno"). */
    private fun genreFrom(subjects: String): String? =
        subjects.split(';', ',').map { it.trim() }.firstOrNull { it.isNotEmpty() && Genres.family(it) != null && !Genres.isGeneric(it) }

    private fun encodePath(name: String) = name.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
