package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicTest {
    /** A playlist page as Apple Music serves it, cut down to the parts read. */
    private fun page(tracks: String, name: String = "Telugu Hits") = """
        <html><head></head><body><div id="app"></div>
        <script type="application/json" id="serialized-server-data">{"data":[{"intent":{},"data":{"sections":[
          {"id":"playlist-detail-header-section","itemKind":"containerDetailHeaderLockup","items":[{"title":"$name","trackCount":3,
            "artwork":{"dictionary":{"url":"https://is1-ssl.mzstatic.example/image/thumb/cover.png/{w}x{h}bb.{f}","width":3000}}}]},
          {"id":"track-list","itemKind":"trackLockup","items":[$tracks]},
          {"id":"artist - featured-artists","itemKind":"bubbleLockup","items":[{"title":"Not a song"}]}
        ]}}],"userTokenHash":""}</script></body></html>
    """.trimIndent()

    private val threeSongs = """
        {"title":"Chuttamalle","artistName":"Anirudh Ravichander & Shilpa Rao","duration":224000,"contentDescriptor":{"kind":"song"}},
        {"title":"A video","artistName":"Someone","duration":180500,"contentDescriptor":{"kind":"musicVideo"}},
        {"title":"Kissik","artistName":"Devi Sri Prasad","duration":0,"contentDescriptor":{"kind":"song"}}
    """

    @Test
    fun readsSongsFromThePlaylistPage() {
        val list = AppleMusicProvider.parsePage(page(threeSongs))
        assertEquals("Telugu Hits", list.name)
        assertEquals("https://is1-ssl.mzstatic.example/image/thumb/cover.png/600x600bb.jpg", list.coverUrl)
        // The music video is left out, and so is everything outside the song list; a missing length stays unknown.
        assertEquals(listOf("Chuttamalle", "Kissik"), list.songs.map { it.title })
        assertEquals("Anirudh Ravichander & Shilpa Rao", list.songs[0].artists)
        assertEquals(224.0, list.songs[0].durationSec!!, 0.001)
        assertNull(list.songs[1].durationSec)
    }

    @Test
    fun aChangedPageFailsWithAReason() {
        val e = runCatching { AppleMusicProvider.parsePage("<html><body>new layout</body></html>") }.exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("changed"))
    }

    private val noNetwork = HttpGet { error("no network in this test") }
    private val validator = PlaylistUrlValidator(listOf(SpotifyProvider(noNetwork), AppleMusicProvider(noNetwork), WebPlaylistProvider(noNetwork)))
    private fun ref(link: String) = (validator.check(link) as? UrlCheck.Valid ?: error("expected valid: $link → ${validator.check(link)}")).ref

    @Test
    fun takesPlaylistLinksWithOrWithoutTheirName() {
        assertEquals("in/playlist/telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63",
            ref("https://music.apple.com/in/playlist/telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63").playlistId)
        assertEquals("us/playlist/pl.f4d106fed2bd41149aaacabb233eb5eb", ref("https://music.apple.com/us/playlist/pl.f4d106fed2bd41149aaacabb233eb5eb?l=en").playlistId)
        assertEquals("applemusic", ref("https://music.apple.com/in/playlist/telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63").providerId)
        // A song, an album or an artist isn't a playlist.
        assertTrue(validator.check("https://music.apple.com/in/song/kissik/1785196700") is UrlCheck.Invalid)
        assertTrue(validator.check("https://music.apple.com/in/artist/devi-sri-prasad/219488592") is UrlCheck.Invalid)
    }

    @Test
    fun importsMatchedSongsAsYouTubeStreamsAndCountsTheRest() {
        val html = page(threeSongs)
        val provider = AppleMusicProvider(
            http = HttpGet { url -> assertEquals("https://music.apple.com/in/playlist/telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63", url); html },
            // The first song is found, the second isn't.
            matcher = SongMatcher { songs, progress ->
                songs.mapIndexed { i, _ -> progress(i + 1); if (i == 0) SongMatch("dQw4w9WgXcQ", 225.0, null) else null }
            },
        )
        val playlist = provider.fetchPlaylist(ref("https://music.apple.com/in/playlist/telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63"))
        assertEquals("https://music.apple.com/in/playlist/telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63", playlist.link)
        assertEquals("pl.1be89625ddd94a80a1dff804b41efd63", playlist.playlistId)
        assertEquals("Telugu Hits", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertEquals(1, playlist.missing)
        val t = playlist.tracks.single()
        assertEquals("Chuttamalle", t.title)
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", t.streamUrl)
        // No YouTube thumbnail: the playlist's cover stands in.
        assertEquals("https://is1-ssl.mzstatic.example/image/thumb/cover.png/600x600bb.jpg", t.artworkUrl)
        assertEquals(false, t.storable)
        assertNull(SpotifyProvider.matching.value)
    }

    @Test
    fun catalogueSearchMatchesNamesSectionsAndTags() {
        assertTrue(AppleMusicCatalogue.search("telugu").any { it.title == "Telugu Hits" })
        assertTrue(AppleMusicCatalogue.search("kannada").all { it.section == "India" })
        assertTrue(AppleMusicCatalogue.search("party").any { it.title == "danceXL" })
        assertTrue(AppleMusicCatalogue.search("zzqx").isEmpty())
        assertEquals("Apple Music › India › Tamil Hits", AppleMusicCatalogue.all.first { it.title == "Tamil Hits" }.origin)
        // Every card's link is one the importer takes, and no playlist is listed twice.
        AppleMusicCatalogue.all.forEach { assertEquals("applemusic", ref(it.link).providerId) }
        assertEquals(AppleMusicCatalogue.all.size, AppleMusicCatalogue.all.map { it.path }.toSet().size)
    }
}
