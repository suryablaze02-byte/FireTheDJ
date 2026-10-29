package com.autodj.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where remote tracks enter the mixing pipeline. The sink adds them to the library as a
 * source; from there they are analysed (BPM, key, structure, genre) and mixed exactly like
 * local files, streaming from [RemoteTrack.streamUrl].
 */
fun interface TrackSink {
    fun ingest(playlist: RemotePlaylist)
}

sealed interface ImportResult {
    data class Imported(val playlist: RemotePlaylist) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/** Link → validated ref → provider fetch → [TrackSink]. Blocking; run off the main thread. */
class PlaylistImporter(private val validator: PlaylistUrlValidator, private val sink: TrackSink) {
    fun import(link: String): ImportResult {
        val check = validator.check(link)
        if (check is UrlCheck.Invalid) return ImportResult.Failed(check.reason)
        check as UrlCheck.Valid
        val playlist = try {
            check.provider.fetchPlaylist(check.ref)
        } catch (e: Exception) {
            return ImportResult.Failed("${check.provider.displayName}: ${e.message ?: e.javaClass.simpleName}")
        }
        if (playlist.tracks.isEmpty()) return ImportResult.Failed("That playlist has no streamable tracks")
        sink.ingest(playlist)
        return ImportResult.Imported(playlist)
    }
}

/**
 * Imported playlists, kept as JSON so their tracks keep names, artwork and their source
 * across launches without going back to the network.
 */
class RemoteLibrary(dir: File) {
    private val file = File(dir, "remote-playlists.json")
    /** Why the saved playlists couldn't be read at startup, if they couldn't. */
    var loadError: String? = null
        private set

    private var playlists: List<RemotePlaylist> = load()

    @Synchronized
    fun playlists(): List<RemotePlaylist> = playlists

    /** Metadata for a library uri that came from a remote playlist. */
    @Synchronized
    fun track(uri: String): RemoteTrack? = playlists.firstNotNullOfOrNull { p -> p.tracks.firstOrNull { it.streamUrl == uri } }

    /** Adds or replaces (same link) a playlist. */
    @Synchronized
    fun put(p: RemotePlaylist) {
        playlists = playlists.filterNot { it.link == p.link } + p
        write()
    }

    @Synchronized
    fun remove(link: String) {
        playlists = playlists.filterNot { it.link == link }
        write()
    }

    @Synchronized
    fun clear() {
        playlists = emptyList()
        file.delete()
    }

    private fun load(): List<RemotePlaylist> = try {
        if (!file.exists()) emptyList() else fromJson(JSONArray(file.readText()))
    } catch (e: Exception) {
        loadError = "${e.javaClass.simpleName}: ${e.message}"
        emptyList() // a damaged store just means re-importing
    }

    private fun write() {
        file.writeText(toJson(playlists).toString())
    }

    companion object {
        fun toJson(list: List<RemotePlaylist>): JSONArray = JSONArray().apply {
            for (p in list) put(
                JSONObject()
                    .put("link", p.link).put("providerId", p.providerId).put("playlistId", p.playlistId).put("name", p.name)
                    .putOpt("origin", p.origin)
                    .put("tracks", JSONArray().apply {
                        for (t in p.tracks) put(
                            JSONObject()
                                .put("title", t.title).put("artist", t.artist).put("streamUrl", t.streamUrl)
                                .put("providerId", t.providerId).put("providerTrackId", t.providerTrackId)
                                .putOpt("artworkUrl", t.artworkUrl).putOpt("durationSec", t.durationSec).putOpt("genre", t.genre)
                                .put("storable", t.storable),
                        )
                    }),
            )
        }

        fun fromJson(a: JSONArray): List<RemotePlaylist> = (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            val tracks = p.getJSONArray("tracks")
            RemotePlaylist(
                link = p.getString("link"),
                providerId = p.getString("providerId"),
                playlistId = p.getString("playlistId"),
                name = p.getString("name"),
                origin = p.optString("origin").ifBlank { null },
                tracks = (0 until tracks.length()).map { j ->
                    val t = tracks.getJSONObject(j)
                    RemoteTrack(
                        title = t.getString("title"),
                        artist = t.getString("artist"),
                        streamUrl = t.getString("streamUrl"),
                        providerId = t.getString("providerId"),
                        providerTrackId = t.getString("providerTrackId"),
                        artworkUrl = t.optString("artworkUrl").ifBlank { null },
                        durationSec = t.optDouble("durationSec").takeIf { !it.isNaN() },
                        genre = t.optString("genre").ifBlank { null },
                        storable = t.optBoolean("storable", true),
                    )
                },
            )
        }
    }
}
