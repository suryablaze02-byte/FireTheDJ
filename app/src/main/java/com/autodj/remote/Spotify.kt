package com.autodj.remote

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs

/** One song of a Spotify playlist or album, as its public page lists it. */
data class SpotifySong(val title: String, val artists: String, val durationSec: Double?)

/** A Spotify playlist or album: its name, cover and songs. */
data class SpotifyList(val name: String, val coverUrl: String?, val songs: List<SpotifySong>)

/**
 * Spotify playlists and albums, played from YouTube: `https://open.spotify.com/playlist/…`,
 * `/album/…` or a `spotify.link` short link. Nothing is played from Spotify and no one signs
 * in: the public embed page gives the songs' names, artists and lengths (the first 100), and
 * each song is looked up on YouTube Music and streamed from there like any YouTube track.
 * Songs with no upload of about the same length are left out.
 */
class SpotifyProvider(
    private val http: HttpGet = UrlHttpGet(BROWSER_AGENT),
    private val matcher: SongMatcher = YouTubeSongMatcher(),
) : StreamProvider {
    override val id = "spotify"
    override val displayName = "Spotify"
    override val urlRule = PlaylistUrlRule(
        hosts = setOf("open.spotify.com", "spotify.link", "spotify.app.link"),
        // open.spotify.com/(intl-xx/)playlist/<id>, or a short link's code.
        pathPattern = Regex("""/(?:intl-[A-Za-z-]+/)?((?:playlist|album)/[A-Za-z0-9]{22}|[A-Za-z0-9]{6,20})/?"""),
        idPattern = Regex("""(?:playlist|album)/[A-Za-z0-9]{22}|[A-Za-z0-9]{6,20}"""),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        val path = if ('/' in ref.playlistId) ref.playlistId else resolveShortLink(ref.link)
        val list = parseEmbed(http.get("https://open.spotify.com/embed/$path"))
        if (list.songs.isEmpty()) throw IOException("Spotify lists no songs in that ${path.substringBefore('/')}")
        progress.value = 0 to list.songs.size
        try {
            val found = matcher.matchAll(list.songs) { done -> progress.value = done to list.songs.size }
            val tracks = list.songs.zip(found).mapNotNull { (song, match) ->
                match ?: return@mapNotNull null
                RemoteTrack(
                    title = song.title,
                    artist = song.artists,
                    streamUrl = YouTubeLinks.watchUrl(match.videoId),
                    providerId = id,
                    providerTrackId = match.videoId,
                    artworkUrl = match.thumbnailUrl ?: list.coverUrl,
                    durationSec = match.durationSec ?: song.durationSec,
                    // Streamed from YouTube, which doesn't let apps keep copies.
                    storable = false,
                )
            }.distinctBy { it.providerTrackId }
            // One link per playlist, whatever share code was pasted with it.
            return RemotePlaylist("https://open.spotify.com/$path", id, path, list.name, tracks, missing = list.songs.size - tracks.size)
        } finally {
            progress.value = null
        }
    }

    /** A `spotify.link` code → "playlist/<id>", from the redirect (or the page it lands on). */
    private fun resolveShortLink(link: String): String {
        var url = link
        repeat(5) {
            SPOTIFY_PATH.find(url)?.let { return it.value.trimStart('/') }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", BROWSER_AGENT)
            try {
                val next = conn.getHeaderField("Location")
                if (next != null && conn.responseCode in 300..399) {
                    url = URL(URL(url), next).toString()
                } else {
                    val body = conn.inputStream.use { s -> s.readBytes() }.decodeToString()
                    return SPOTIFY_PATH.find(body)?.value?.trimStart('/') ?: throw IOException("That short link doesn't lead to a playlist or album")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("That short link redirects too many times")
    }

    companion object {
        private val SPOTIFY_PATH = Regex("""/(?:playlist|album)/[A-Za-z0-9]{22}""")
        private const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

        private val progress = MutableStateFlow<Pair<Int, Int>?>(null)

        /** Songs looked up on YouTube so far, of how many, while a Spotify list is being added. */
        val matching: StateFlow<Pair<Int, Int>?> = progress

        /** The playlist or album on an embed page: the page's own data, as the embedded player reads it. */
        fun parseEmbed(html: String): SpotifyList {
            val json = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: throw IOException("Spotify's page has changed: no song list found")
            val entity = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
                ?.optJSONObject("data")?.optJSONObject("entity") ?: throw IOException("Spotify's page has changed: no song list found")
            val rows = entity.optJSONArray("trackList")
            val songs = (0 until (rows?.length() ?: 0)).mapNotNull { i ->
                val t = rows!!.optJSONObject(i) ?: return@mapNotNull null
                if (t.optString("entityType", "track") != "track" || !t.optBoolean("isPlayable", true)) return@mapNotNull null
                val title = t.optString("title").trim().ifEmpty { return@mapNotNull null }
                SpotifySong(title, t.optString("subtitle").trim(), t.optLong("duration", 0L).takeIf { it > 0 }?.let { it / 1000.0 })
            }
            val cover = entity.optJSONObject("coverArt")?.optJSONArray("sources")?.let { s ->
                (0 until s.length()).mapNotNull { s.optJSONObject(it) }.maxByOrNull { it.optInt("width") }?.optString("url")
            }?.takeIf { it.startsWith("https://") }
            val name = entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { "Spotify playlist" }
            return SpotifyList(name, cover, songs)
        }
    }
}

/** A song's YouTube upload. */
data class SongMatch(val videoId: String, val durationSec: Double?, val thumbnailUrl: String?)

/** Finds songs on a service that can stream them. */
fun interface SongMatcher {
    /** One match (or null) per song, in order; [onProgress] counts songs done. Blocking. */
    fun matchAll(songs: List<SpotifySong>, onProgress: (Int) -> Unit): List<SongMatch?>
}

/**
 * Looks each song up on YouTube Music ("title artist", songs only), then on YouTube, and takes
 * the first result within a few seconds of the song's length: a cover, a live take or a
 * one-minute clip is rarely the same length. A few songs are looked up at once.
 */
class YouTubeSongMatcher(private val parallel: Int = 6) : SongMatcher {
    override fun matchAll(songs: List<SpotifySong>, onProgress: (Int) -> Unit): List<SongMatch?> {
        YouTubeStreams.requireSupported()
        val pool = Executors.newFixedThreadPool(parallel)
        var done = 0
        try {
            val jobs = songs.map { song ->
                pool.submit(Callable {
                    val match = runCatching { match(song) }.onFailure { Log.w("AutoDJ", "No YouTube match for ${song.artists} - ${song.title}: ${it.message}") }.getOrNull()
                    synchronized(this) { onProgress(++done) }
                    match
                })
            }
            return jobs.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun match(song: SpotifySong): SongMatch? {
        val firstArtist = song.artists.substringBefore(',').trim()
        val query = "${song.title} $firstArtist".trim()
        return pick(search(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), song.durationSec, CLOSE_SEC)
            ?: pick(search("$firstArtist - ${song.title}", null), song.durationSec, LOOSE_SEC)
    }

    private fun search(query: String, filter: String?): List<StreamInfoItem> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOfNotNull(filter), "").apply { fetchPage() }
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>().take(RESULTS)
    }

    private fun pick(items: List<StreamInfoItem>, songSec: Double?, slack: Double): SongMatch? {
        val item = items.firstOrNull { songSec == null || (it.duration > 0 && abs(it.duration - songSec) <= slack) } ?: return null
        val videoId = YouTubeLinks.videoId(item.url) ?: return null
        val thumb = item.thumbnails.maxByOrNull { it.width.toLong() * it.height }?.url?.takeIf { it.startsWith("https://") }
        return SongMatch(videoId, item.duration.takeIf { it > 0 }?.toDouble(), thumb)
    }

    private companion object {
        const val RESULTS = 6
        const val CLOSE_SEC = 8.0
        const val LOOSE_SEC = 15.0
    }
}
