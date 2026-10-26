package com.autodj.remote

import android.os.Build
import android.util.Log
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * YouTube Music (and YouTube) playlists and albums: `https://music.youtube.com/playlist?list=…`
 * or a `watch?v=…&list=…` link. Each track keeps a stable watch link as its [RemoteTrack.streamUrl];
 * the playable audio URL is signed, expires within hours and is tied to the client that asked
 * for it, so it is only worked out when the track is analysed or played (see [YouTubeStreams]).
 * Tracks are never stored: YouTube doesn't let apps keep copies.
 */
class YouTubeMusicProvider(private val browse: YtMusicBrowse = YtMusicBrowse()) : StreamProvider {
    override val id = "youtube"
    override val displayName = "YouTube Music"
    override val urlRule = PlaylistUrlRule(
        hosts = setOf("music.youtube.com", "youtube.com", "m.youtube.com"),
        idParam = "list",
        idPattern = Regex("""[A-Za-z0-9_\-]{2,64}"""),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        // "RD…" ids are YouTube Music's curated playlists (and mixes): see YtMusicBrowse.playlist.
        if (ref.playlistId.startsWith("RD")) return fromYtMusic(ref)
        YouTubeStreams.requireSupported()
        val extractor = try {
            ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=${ref.playlistId}").apply { fetchPage() }
        } catch (e: LinkageError) {
            throw IOException("YouTube can't run on this device: $e", e)
        }
        val items = mutableListOf<StreamInfoItem>()
        var page = extractor.initialPage
        while (true) {
            items += page.items
            // Mixes ("RD…") never end; a set doesn't need more than this anyway.
            if (items.size >= MAX_TRACKS || !page.hasNextPage()) break
            page = extractor.getPage(page.nextPage)
        }
        val tracks = items.asSequence()
            .filter { it.streamType != StreamType.LIVE_STREAM && it.streamType != StreamType.AUDIO_LIVE_STREAM }
            .mapNotNull { item ->
                val videoId = YouTubeLinks.videoId(item.url) ?: return@mapNotNull null
                val (artist, title) = YouTubeLinks.artistAndTitle(item.uploaderName.orEmpty(), item.name.orEmpty())
                RemoteTrack(
                    title = title,
                    artist = artist,
                    streamUrl = YouTubeLinks.watchUrl(videoId),
                    providerId = id,
                    providerTrackId = videoId,
                    artworkUrl = item.thumbnails.maxByOrNull { it.width.toLong() * it.height }?.url?.takeIf { it.startsWith("https://") },
                    durationSec = item.duration.takeIf { it > 0 }?.toDouble(),
                    storable = false,
                )
            }
            .distinctBy { it.providerTrackId }
            .take(MAX_TRACKS)
            .toList()
        val name = runCatching { extractor.name }.getOrNull()?.ifBlank { null } ?: "YouTube playlist"
        return RemotePlaylist(ref.link, id, ref.playlistId, name, tracks)
    }

    private fun fromYtMusic(ref: PlaylistRef): RemotePlaylist {
        val playlist = browse.playlist(ref.playlistId, MAX_TRACKS)
        if (playlist.tracks.isEmpty()) throw IOException("YouTube Music has no playable tracks in that playlist")
        val tracks = playlist.tracks.map { t ->
            RemoteTrack(
                title = YouTubeLinks.cleanTitle(t.title),
                artist = t.artist,
                streamUrl = YouTubeLinks.watchUrl(t.videoId),
                providerId = id,
                providerTrackId = t.videoId,
                artworkUrl = t.thumbnailUrl,
                durationSec = t.durationSec,
                storable = false,
            )
        }
        return RemotePlaylist(ref.link, id, ref.playlistId, playlist.name, tracks)
    }

    private companion object {
        const val MAX_TRACKS = 500
    }
}

/** Link handling for YouTube tracks; no network. */
object YouTubeLinks {
    private val HOSTS = setOf("music.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")
    private val VIDEO_ID = Regex("""[A-Za-z0-9_\-]{11}""")

    /** Words in brackets that describe the upload, not the song: "(Official Video)", "[Lyrics]". */
    private val UPLOAD_NOTE = Regex(
        """\s*[(\[](?:official\s*)?(?:music\s*)?(?:video|audio|lyrics?|lyric\s*video|visualizer|hd|hq|4k)[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    /** The stable link a track is known by in the library: what [videoId] reads back. */
    fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    /** The video id in a YouTube link (`watch?v=…`, `youtu.be/…`), or null for anything else. */
    fun videoId(link: String): String? {
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val id = if (host == "youtu.be") {
            uri.rawPath?.removePrefix("/")
        } else {
            if (uri.rawPath != "/watch") return null
            PlaylistUrlValidator.queryParams(uri.rawQuery)["v"]
        }
        return id?.takeIf { VIDEO_ID.matches(it) }
    }

    /**
     * Artist and title from a playlist entry. YouTube Music's own uploads come from
     * "<Artist> - Topic" channels with the plain song title; other videos are usually named
     * "Artist - Title" and come from the artist's (or a label's) channel.
     */
    /** The song title without notes about the upload ("(Official Video)", "[Lyrics]"). */
    fun cleanTitle(name: String) = name.replace(UPLOAD_NOTE, "").trim().ifEmpty { name.trim() }

    fun artistAndTitle(uploader: String, name: String): Pair<String, String> {
        val title = cleanTitle(name)
        if (uploader.endsWith(" - Topic")) return uploader.removeSuffix(" - Topic").trim() to title
        val parts = title.split(" - ", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) return parts[0].trim() to parts[1].trim()
        return uploader.removeSuffix("VEVO").trim() to title
    }
}

/** A playable audio URL for a YouTube track and the headers its fetches must carry. */
class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    /** Whole file size in bytes, from the URL's `clen`; null if it doesn't say. */
    val contentLength: Long?,
    val mimeType: String,
)

/**
 * Turns a YouTube video id into a playable audio URL with NewPipeExtractor, which solves the
 * signature cipher and the `n` throttling parameter by running YouTube's player JavaScript.
 *
 * Extractions are run one at a time: the player-JS step is CPU work over state NewPipe shares
 * process-wide, and several at once each take many times longer. Results are kept for a while
 * so a deck's seeks and reopens don't extract again; a refused URL is dropped with [forgetUrl].
 */
object YouTubeStreams {
    private const val TAG = "AutoDJ"
    private const val ATTEMPTS = 3
    private const val TTL_MS = 20 * 60 * 1000L
    private const val MAX_REMEMBERED = 64

    private val gate = Any()
    private val recent = ConcurrentHashMap<String, Pair<ResolvedStream, Long>>()

    private val init by lazy { NewPipe.init(NewPipeDownloader(), Localization.DEFAULT) }

    /**
     * NewPipe 0.26 encodes URLs with `URLEncoder.encode(String, Charset)`, which Android only
     * has from 13 on; below that it fails with an Error that would take the app down.
     */
    fun requireSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) throw IOException("YouTube Music needs Android 13 or newer")
        init
    }

    /** Blocking; run off the main thread. Throws with a reason worth showing. */
    fun resolve(videoId: String): ResolvedStream {
        cached(videoId)?.let { return it }
        requireSupported()
        synchronized(gate) {
            // Someone else may have extracted it while this call waited.
            cached(videoId)?.let { return it }
            var failure: Exception? = null
            repeat(ATTEMPTS) { attempt ->
                if (attempt > 0) Thread.sleep(1000L * attempt)
                try {
                    val t0 = System.currentTimeMillis()
                    val stream = extract(videoId)
                    Log.i(TAG, "resolved YouTube $videoId: ${stream.mimeType}, ${stream.contentLength?.div(1024)} KB in ${System.currentTimeMillis() - t0} ms")
                    remember(videoId, stream)
                    return stream
                } catch (e: ContentNotAvailableException) {
                    // Age-restricted, region-blocked, private, removed: asking again won't help.
                    throw IOException(e.message ?: "This track isn't available", e)
                } catch (e: LinkageError) {
                    throw IOException("YouTube can't run on this device: $e", e)
                } catch (e: Exception) {
                    Log.w(TAG, "YouTube $videoId: attempt ${attempt + 1} of $ATTEMPTS failed: ${e.message}")
                    failure = e
                }
            }
            throw IOException("Couldn't get audio from YouTube: ${failure?.message}", failure)
        }
    }

    /** Drops a URL the server refused, so the next open extracts a fresh one. */
    fun forgetUrl(url: String) {
        recent.entries.removeAll { it.value.first.url == url }
    }

    private fun cached(videoId: String): ResolvedStream? =
        recent[videoId]?.takeIf { System.currentTimeMillis() - it.second < TTL_MS }?.first

    private fun remember(videoId: String, stream: ResolvedStream) {
        if (recent.size >= MAX_REMEMBERED) {
            val cutoff = System.currentTimeMillis() - TTL_MS
            recent.entries.removeAll { it.value.second < cutoff }
            if (recent.size >= MAX_REMEMBERED) recent.clear()
        }
        recent[videoId] = stream to System.currentTimeMillis()
    }

    private fun extract(videoId: String): ResolvedStream {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        // Progressive only: DASH and HLS entries are manifests, not files.
        val candidates = extractor.audioStreams.filter {
            it.isUrl && !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        // AAC in MP4 first: its index makes the decks' many seeks land exactly. Opus in WebM otherwise.
        val pick = best(candidates, MediaFormat.M4A) ?: best(candidates, MediaFormat.WEBMA_OPUS)
            ?: throw IOException("No playable audio for this track")
        val url = pick.content
        return ResolvedStream(
            url = url,
            headers = mediaHeaders(url),
            contentLength = URI(url).rawQuery?.let { PlaylistUrlValidator.queryParams(it)["clen"] }?.toLongOrNull(),
            mimeType = pick.format?.mimeType.orEmpty(),
        )
    }

    private fun best(streams: List<AudioStream>, format: MediaFormat) =
        streams.filter { it.format == format }.maxByOrNull { it.averageBitrate }

    /**
     * googlevideo compares the client named in the URL (`c=`) with the request fetching the
     * bytes, and throttles or refuses a mismatch, so media requests dress as that client.
     */
    private fun mediaHeaders(url: String): Map<String, String> {
        val client = URI(url).rawQuery?.let { PlaylistUrlValidator.queryParams(it)["c"] }?.uppercase().orEmpty()
        val loc = NewPipe.getPreferredLocalization()
        return when {
            client.startsWith("IOS") -> mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(loc))
            client == "VISIONOS" -> mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(loc))
            client.startsWith("ANDROID") -> mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(loc))
            client == "WEB_REMIX" -> webHeaders("https://music.youtube.com")
            else -> webHeaders("https://www.youtube.com")
        }
    }

    private fun webHeaders(origin: String) = mapOf("User-Agent" to NewPipeDownloader.BROWSER_AGENT, "Origin" to origin, "Referer" to "$origin/")
}

/** NewPipe's network access, over HttpURLConnection. */
private class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        // "What plays next": NewPipe asks for it while building every stream extractor, it is the
        // slowest call of the lot (it can hang for seconds), and nothing here reads it. Answered
        // empty but well-formed; NewPipe rejects JSON bodies under 50 characters.
        if (NEXT_ENDPOINT in request.url()) return Response(200, "OK", emptyMap(), EMPTY_NEXT, request.url())

        val conn = URL(request.url()).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10_000
        conn.requestMethod = request.httpMethod()
        var hasAgent = false
        for ((name, values) in request.headers()) {
            if (name.equals("User-Agent", ignoreCase = true) && values.isNotEmpty()) hasAgent = true
            values.forEachIndexed { i, v -> if (i == 0) conn.setRequestProperty(name, v) else conn.addRequestProperty(name, v) }
        }
        // A desktop Chrome agent: with others NewPipe can fail to parse the player JS it is served.
        if (!hasAgent) conn.setRequestProperty("User-Agent", BROWSER_AGENT)
        try {
            request.dataToSend()?.let { body ->
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code == 429) throw ReCaptchaException("YouTube is rate limiting this device, try again later", request.url())
            val raw = (if (code >= 400) conn.errorStream else conn.inputStream)
            val body = raw?.let { s ->
                val input = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(s) else s
                input.use { it.readBytes() }.decodeToString()
            }
            // The status line comes back under a null key.
            val headers = conn.headerFields.entries.mapNotNull { (k, v) -> k?.let { it to v } }.toMap()
            return Response(code, conn.responseMessage, headers, body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        private const val NEXT_ENDPOINT = "/youtubei/v1/next"
        private const val EMPTY_NEXT = """{"responseContext":{},"contents":{},"currentVideoEndpoint":{},"trackingParams":""}"""
    }
}
