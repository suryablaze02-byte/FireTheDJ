package com.autodj.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Apple Music playlists, played from YouTube: `https://music.apple.com/<country>/playlist/<name>/pl.…`.
 * Nothing is played from Apple Music and no one signs in: the public playlist page carries the
 * songs' names, artists and lengths, and each song is looked up on YouTube Music and streamed
 * from there, as for Spotify. The first [MAX_SONGS] are taken; songs with no upload of about the
 * same length are left out.
 */
class AppleMusicProvider(
    private val http: HttpGet = UrlHttpGet(BROWSER_AGENT),
    private val matcher: SongMatcher = YouTubeSongMatcher(),
) : StreamProvider {
    override val id = "applemusic"
    override val displayName = "Apple Music"
    override val urlRule = PlaylistUrlRule(
        hosts = setOf("music.apple.com", "embed.music.apple.com"),
        // music.apple.com/<country>/playlist/(<name>/)pl.<id>
        pathPattern = Regex("""/([a-z]{2}/playlist/(?:[^/]+/)?pl\.[A-Za-z0-9.-]+)/?"""),
        idPattern = Regex("""[a-z]{2}/playlist/(?:[^/]+/)?pl\.[A-Za-z0-9.-]+"""),
    )

    override fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist {
        val list = parsePage(http.get("https://music.apple.com/${ref.playlistId}"))
        val songs = list.songs.take(MAX_SONGS)
        if (songs.isEmpty()) throw IOException("Apple Music lists no songs in that playlist")
        val progress = SpotifyProvider.progress
        progress.value = 0 to songs.size
        try {
            val found = matcher.matchAll(songs) { done -> progress.value = done to songs.size }
            val tracks = songs.zip(found).mapNotNull { (song, match) ->
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
            // One link per playlist, whichever country or name it was pasted with.
            val playlistId = ref.playlistId.substringAfterLast('/')
            return RemotePlaylist("https://music.apple.com/${ref.playlistId}", id, playlistId, list.name, tracks, missing = songs.size - tracks.size)
        } finally {
            progress.value = null
        }
    }

    companion object {
        /** As many songs as a Spotify list gives: each is one YouTube search. */
        const val MAX_SONGS = 100

        private const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

        /**
         * The playlist on its public page, from the data the page is drawn from: the header's name
         * and cover, and every song row's title, artist and length (milliseconds).
         */
        fun parsePage(html: String): SpotifyList {
            val json = Regex("""<script type="application/json" id="serialized-server-data">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: throw IOException("Apple Music's page has changed: no song list found")
            val root = JSONObject(json)
            val page = (root.optJSONArray("data") ?: throw IOException("Apple Music's page has changed: no song list found"))
                .optJSONObject(0)?.optJSONObject("data")
            val sections = page?.optJSONArray("sections") ?: throw IOException("Apple Music's page has changed: no song list found")
            var name = "Apple Music playlist"
            var cover: String? = null
            val songs = ArrayList<SpotifySong>()
            for (s in sections.objects()) {
                val items = s.optJSONArray("items") ?: continue
                when (s.optString("itemKind")) {
                    "containerDetailHeaderLockup" -> items.optJSONObject(0)?.let { h ->
                        h.optString("title").trim().takeIf { it.isNotEmpty() }?.let { name = it }
                        cover = h.optJSONObject("artwork")?.optJSONObject("dictionary")?.optString("url")?.let(::artworkUrl)
                    }
                    "trackLockup" -> for (t in items.objects()) {
                        val kind = t.optJSONObject("contentDescriptor")?.optString("kind")
                        if (kind != null && kind != "song") continue
                        val title = t.optString("title").trim()
                        if (title.isEmpty()) continue
                        val ms = t.optLong("duration", 0L)
                        songs += SpotifySong(title, t.optString("artistName").trim(), ms.takeIf { it > 0 }?.let { it / 1000.0 })
                    }
                }
            }
            return SpotifyList(name, cover, songs)
        }

        /** Apple's artwork template ("…/{w}x{h}bb.{f}") at a cover's size; null when it isn't https. */
        fun artworkUrl(template: String, size: Int = 600): String? =
            template.replace("{w}", "$size").replace("{h}", "$size").replace("{f}", "jpg").replace("{c}", "bb")
                .takeIf { it.startsWith("https://") }

        private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

/** One of Apple Music's own playlists, as a card: imported from YouTube like a pasted link. */
data class AppleMusicCard(
    val title: String,
    /** "<name>/pl.<id>", as in the playlist's link. */
    val path: String,
    val section: String,
    /** Extra words it's found by ("telugu", "party"). */
    val tags: String = "",
) {
    val link get() = "https://music.apple.com/in/playlist/$path"

    /** Where it was picked, kept with the playlist: names its language ("Apple Music › India › Telugu Hits"). */
    val origin get() = "Apple Music › $section › $title"
}

/** A titled row of Apple Music playlists. */
data class AppleMusicSection(val title: String, val cards: List<AppleMusicCard>)

/**
 * Apple Music's curated playlists FireTheDJ offers, by section. Like Spotify, Apple Music has no
 * public way to list them without signing in, so they're a fixed list: each was checked against
 * its public page in the Indian store (2026-11-04). Apple refreshes their songs; a retired
 * playlist fails to import and should be dropped here.
 */
object AppleMusicCatalogue {
    val sections: List<AppleMusicSection> = listOf(
        section(
            "Top & trending",
            "Today's Hits" to "todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb",
            "Top 100: India" to "top-100-india/pl.c0e98d2423e54c39b3df955c24df3cc5",
            "Top 100: Global" to "top-100-global/pl.d25f5d1181894928af76c85c967f8f31",
            "A-List Pop" to "a-list-pop/pl.5ee8333dbe944d9f9151e97d92d1ead9",
            "Hip-Hop Hits" to "hip-hop-hits/pl.87c7af5767764860a0e3368d0bef9a6f",
            tags = "pop hits new chart trending top rap hip hop",
        ),
        section(
            "India",
            "Telugu Hits" to "telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63",
            "Telugu Romance" to "telugu-romance/pl.309ff6f19ad74c638af2d8d7caa3b464",
            "Tamil Hits" to "tamil-hits/pl.c8d5311e407f42c89d0fce075b5aaa43",
            "Tamil Dance" to "tamil-dance/pl.ccb9971c508346e395190031891192da",
            "Bollywood Hits" to "bollywood-hits/pl.d60caf02fcce4d7e9788fe01243b7c2c",
            "Hindi Pop Hits" to "hindi-pop-hits/pl.08ab4f270ff940db917eb0ad4f0da865",
            "Punjabi Hits" to "punjabi-hits/pl.35b8520d9d6a486d9bf5c1b3331165b2",
            "Kannada Hits" to "kannada-hits/pl.2b8d95e7f7354658aa575ec9f15b5dca",
            "Malayalam Hits" to "malayalam-hits/pl.60dd84ecb1e14bf4b4ac9994fb18882f",
            tags = "india indian desi bollywood tollywood kollywood hindi telugu tamil punjabi kannada malayalam",
        ),
        section(
            "Party & dance",
            "danceXL" to "dancexl/pl.6bf4415b83ce4f3789614ac4c3675740",
            "EDM Essentials" to "edm-essentials/pl.733395f7caac4866b61dc281ac50bb2d",
            "Dance Throwback" to "dance-throwback/pl.5d157a16b512431e93d514d4446db344",
            "Bollywood Throwback" to "bollywood-throwback/pl.01da0a6be98747b1ae82598c7c346b65",
            "Punjabi Workout" to "punjabi-workout/pl.02d529bcbbbe4f449539acc2d20dc086",
            "Tamil Workout" to "tamil-workout/pl.21bb36fa780844f4868b8a95b21a062a",
            tags = "party dance edm club electronic house workout gym throwback",
        ),
        section(
            "Chill",
            "Today's Chill" to "todays-chill/pl.2bb29727dbc34a63936787297305c37c",
            "Telugu Chill" to "telugu-chill/pl.f03fc63713b7451abb1a67a7e8e40a5e",
            "Bollywood Chill" to "bollywood-chill/pl.322309345de044cdb07921938347e36e",
            tags = "chill relax calm lofi telugu bollywood hindi",
        ),
    )

    val all: List<AppleMusicCard> get() = sections.flatMap { it.cards }

    /**
     * The playlists whose name, section or tags hold every word of [query] (a word may be the
     * start of one: "bolly" finds Bollywood), names that start with the query first.
     */
    fun search(query: String): List<AppleMusicCard> {
        val words = query.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.filter { card ->
            val text = "${card.title} ${card.section} ${card.tags}".lowercase().split(Regex("""[^\p{L}\p{N}&']+"""))
            words.all { w -> text.any { it.startsWith(w) } }
        }.sortedByDescending { it.title.lowercase().startsWith(words.first()) }
    }

    private fun section(title: String, vararg cards: Pair<String, String>, tags: String) =
        AppleMusicSection(title, cards.map { (name, path) -> AppleMusicCard(name, path, title, tags) })
}
