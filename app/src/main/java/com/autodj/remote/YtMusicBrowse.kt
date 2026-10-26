package com.autodj.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One of YouTube Music's "Moods & genres" buttons: Party, Workout, Dance & electronic, … */
data class YtCategory(val title: String, val params: String, val color: Long?)

/** A titled group of categories ("Moods & moments", "Genres"). */
data class YtCategorySection(val title: String, val categories: List<YtCategory>)

/** A curated YouTube Music playlist, as a card. */
data class YtPlaylistCard(val title: String, val subtitle: String, val playlistId: String, val thumbnailUrl: String?) {
    /** The link it is imported by, the same one a user would paste. */
    val link get() = "https://music.youtube.com/playlist?list=$playlistId"
}

/** A row of playlists in a category ("Party today", "Throwback party"). */
data class YtShelf(val title: String, val playlists: List<YtPlaylistCard>)

/** One track of a YouTube Music playlist. */
data class YtTrack(val videoId: String, val title: String, val artist: String, val durationSec: Double?, val thumbnailUrl: String?)

/** A playlist's name and tracks, as YouTube Music's own playlist page lists them. */
data class YtPlaylist(val name: String, val tracks: List<YtTrack>)

/**
 * YouTube Music's curated playlists, from the "Moods & genres" pages of its web app: the
 * categories first, then the playlist shelves behind one. Read-only, signed out. The country
 * decides which categories and playlists YouTube offers, so the phone's own is sent.
 */
class YtMusicBrowse(
    private val post: (url: String, body: String) -> String = ::httpPost,
    private val locale: Locale = Locale.getDefault(),
) {
    /** Blocking; run off the main thread. */
    fun categories(): List<YtCategorySection> = parseCategories(JSONObject(browse(MOODS_AND_GENRES, null)))

    /** Blocking; run off the main thread. */
    fun shelves(category: YtCategory): List<YtShelf> = parseShelves(JSONObject(browse(CATEGORY, category.params)))

    /**
     * A playlist's tracks, following its pages up to [maxTracks]. Blocking; run off the main thread.
     *
     * YouTube Music's curated playlists have "RDCLAK…" ids, which NewPipe takes for endless
     * auto-generated mixes (and, in some countries, refuses without a cookie-consent cookie);
     * the web app's playlist page lists them as the fixed playlists they are.
     */
    fun playlist(playlistId: String, maxTracks: Int = 500): YtPlaylist {
        val first = JSONObject(browse("VL$playlistId", null))
        val name = playlistName(first)
        // Without the visitor id page 1 handed out, a signed-out continuation serves page 1 again.
        val visitor = first.optJSONObject("responseContext")?.optString("visitorData")?.ifBlank { null }
        val tracks = mutableListOf<YtTrack>()
        var page: JSONObject? = first
        var pages = 0
        while (page != null && tracks.size < maxTracks && pages++ < MAX_PAGES) {
            val rows = parseTracks(page)
            // A page with nothing new means the continuation went wrong; stop rather than loop.
            if (tracks.isNotEmpty() && rows.all { r -> tracks.any { it.videoId == r.videoId } }) break
            tracks += rows
            val token = continuation(page) ?: break
            page = JSONObject(request(JSONObject().put("continuation", token), visitor))
        }
        return YtPlaylist(name, tracks.distinctBy { it.videoId }.take(maxTracks))
    }

    private fun browse(browseId: String, params: String?): String {
        val body = JSONObject().put("browseId", browseId)
        params?.let { body.put("params", it) }
        return request(body)
    }

    private fun request(body: JSONObject, visitorData: String? = null): String {
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", locale.language.ifBlank { "en" })
            .apply { locale.country.takeIf { it.length == 2 }?.let { put("gl", it) } }
            .apply { visitorData?.let { put("visitorData", it) } }
        body.put("context", JSONObject().put("client", client))
        return post("$API/browse?prettyPrint=false", body.toString())
    }

    companion object {
        private const val API = "https://music.youtube.com/youtubei/v1"
        private const val MOODS_AND_GENRES = "FEmusic_moods_and_genres"
        private const val CATEGORY = "FEmusic_moods_and_genres_category"

        /** The web app's version; YouTube keeps answering older ones for a long time. */
        private const val CLIENT_VERSION = "1.20251020.01.00"

        fun parseCategories(root: JSONObject): List<YtCategorySection> =
            sections(root).mapNotNull { section ->
                val grid = section.optJSONObject("gridRenderer") ?: return@mapNotNull null
                val title = runs(grid.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")?.optJSONObject("title"))
                val categories = objects(grid.optJSONArray("items")).mapNotNull { item ->
                    val button = item.optJSONObject("musicNavigationButtonRenderer") ?: return@mapNotNull null
                    val endpoint = (button.optJSONObject("clickCommand") ?: button.optJSONObject("navigationEndpoint"))
                        ?.optJSONObject("browseEndpoint") ?: return@mapNotNull null
                    if (endpoint.optString("browseId") != CATEGORY) return@mapNotNull null
                    val params = endpoint.optString("params").ifBlank { return@mapNotNull null }
                    val label = runs(button.optJSONObject("buttonText")).ifBlank { return@mapNotNull null }
                    val color = button.optJSONObject("solid")?.optLong("leftStripeColor", -1L)?.takeIf { it >= 0 }
                    YtCategory(label, params, color)
                }
                if (title.isBlank() || categories.isEmpty()) null else YtCategorySection(title, categories)
            }

        fun parseShelves(root: JSONObject): List<YtShelf> =
            sections(root).mapNotNull { section ->
                val shelf = section.optJSONObject("musicCarouselShelfRenderer") ?: section.optJSONObject("gridRenderer")
                    ?: return@mapNotNull null
                val header = shelf.optJSONObject("header")
                val title = runs(
                    header?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")
                        ?: header?.optJSONObject("gridHeaderRenderer")?.optJSONObject("title"),
                )
                val playlists = objects(shelf.optJSONArray("contents") ?: shelf.optJSONArray("items"))
                    .mapNotNull { it.optJSONObject("musicTwoRowItemRenderer")?.let(::playlistCard) }
                    .distinctBy { it.playlistId }
                if (playlists.isEmpty()) null else YtShelf(title.ifBlank { "Playlists" }, playlists)
            }

        private const val MAX_PAGES = 10

        /** The playlist page's title, from its header. */
        fun playlistName(root: JSONObject): String {
            var name = ""
            walk(root) { node ->
                if (name.isEmpty()) {
                    (node.optJSONObject("musicResponsiveHeaderRenderer") ?: node.optJSONObject("musicDetailHeaderRenderer"))
                        ?.let { name = runs(it.optJSONObject("title")) }
                }
            }
            return name.ifBlank { "YouTube Music playlist" }
        }

        /** The track rows on a playlist page or one of its continuations, in order. */
        fun parseTracks(root: JSONObject): List<YtTrack> {
            val out = mutableListOf<YtTrack>()
            walk(root) { node -> node.optJSONObject("musicResponsiveListItemRenderer")?.let(::track)?.let(out::add) }
            return out
        }

        /** The token for the next page of tracks, if there is one. */
        fun continuation(root: JSONObject): String? {
            var token: String? = null
            walk(root) { node ->
                if (token == null) {
                    token = node.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")?.optString("token")?.ifBlank { null }
                }
            }
            return token
        }

        /** A playable row: unavailable tracks carry no video id and are skipped. */
        private fun track(row: JSONObject): YtTrack? {
            val videoId = row.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.length == 11 } ?: return null
            val columns = objects(row.optJSONArray("flexColumns")).map {
                runs(it.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
            }
            val title = columns.getOrNull(0).orEmpty().ifBlank { return null }
            val duration = objects(row.optJSONArray("fixedColumns")).firstNotNullOfOrNull {
                seconds(runs(it.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")))
            }
            val thumbs = row.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            return YtTrack(
                videoId = videoId,
                title = title,
                artist = columns.getOrNull(1).orEmpty(),
                durationSec = duration,
                thumbnailUrl = objects(thumbs).maxByOrNull { it.optInt("width") }?.optString("url")?.takeIf { it.startsWith("https://") },
            )
        }

        /** "4:57" or "1:02:03" → seconds. */
        private fun seconds(text: String): Double? {
            val parts = text.trim().split(':').map { it.toIntOrNull() ?: return null }
            if (parts.isEmpty() || parts.size > 3) return null
            return parts.fold(0) { acc, p -> acc * 60 + p }.toDouble()
        }

        private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
            when (node) {
                is JSONObject -> {
                    visit(node)
                    node.keys().forEach { walk(node.opt(it), visit) }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
            }
        }

        /** A two-row card that opens a playlist; albums, artists and videos aren't. */
        private fun playlistCard(card: JSONObject): YtPlaylistCard? {
            val browseId = card.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (!browseId.startsWith("VL")) return null
            val playlistId = browseId.removePrefix("VL").takeIf { PLAYLIST_ID.matches(it) } ?: return null
            val thumbs = card.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnail = objects(thumbs).maxByOrNull { it.optInt("width") }?.optString("url")?.takeIf { it.startsWith("https://") }
            return YtPlaylistCard(
                title = runs(card.optJSONObject("title")).trim(),
                subtitle = runs(card.optJSONObject("subtitle")),
                playlistId = playlistId,
                thumbnailUrl = thumbnail,
            )
        }

        private val PLAYLIST_ID = Regex("""[A-Za-z0-9_\-]{2,64}""")

        private fun sections(root: JSONObject): List<JSONObject> {
            val tab = root.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")
            return objects(tab?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents"))
        }

        private fun runs(text: JSONObject?): String =
            objects(text?.optJSONArray("runs")).joinToString("") { it.optString("text") }

        private fun objects(a: JSONArray?): List<JSONObject> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

        private fun httpPost(url: String, body: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Origin", "https://music.youtube.com")
            conn.setRequestProperty("X-YouTube-Client-Name", "67")
            conn.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            conn.setRequestProperty("User-Agent", BROWSER_AGENT)
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} from YouTube Music")
                return conn.inputStream.use { it.readBytes() }.decodeToString()
            } finally {
                conn.disconnect()
            }
        }

        private const val BROWSER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    }
}
