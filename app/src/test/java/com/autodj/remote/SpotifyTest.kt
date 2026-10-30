package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTest {
    /** An embed page as Spotify serves it, cut down to the parts read. */
    private fun embed(tracks: String, name: String = "Hot Hits Telugu") = """
        <html><head></head><body><div id="__next"></div>
        <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{
          "type":"playlist","name":"$name","title":"$name","subtitle":"Spotify",
          "coverArt":{"sources":[{"url":"https://image-cdn.example/small","width":60},{"url":"https://image-cdn.example/big","width":640}]},
          "trackList":[$tracks]}}}}}}</script></body></html>
    """.trimIndent()

    private val threeSongs = """
        {"uri":"spotify:track:1","title":"Chuttamalle","subtitle":"Anirudh Ravichander, Shilpa Rao","duration":224000,"isPlayable":true,"entityType":"track"},
        {"uri":"spotify:track:2","title":"Gone","subtitle":"Someone","duration":180500,"isPlayable":false,"entityType":"track"},
        {"uri":"spotify:track:3","title":"Kissik","subtitle":"Devi Sri Prasad","duration":0,"isPlayable":true,"entityType":"track"}
    """

    @Test
    fun readsSongsFromTheEmbedPage() {
        val list = SpotifyProvider.parseEmbed(embed(threeSongs))
        assertEquals("Hot Hits Telugu", list.name)
        assertEquals("https://image-cdn.example/big", list.coverUrl)
        // The unplayable song is left out; a missing length stays unknown.
        assertEquals(listOf("Chuttamalle", "Kissik"), list.songs.map { it.title })
        assertEquals("Anirudh Ravichander, Shilpa Rao", list.songs[0].artists)
        assertEquals(224.0, list.songs[0].durationSec!!, 0.001)
        assertNull(list.songs[1].durationSec)
    }

    @Test
    fun aChangedPageFailsWithAReason() {
        val e = runCatching { SpotifyProvider.parseEmbed("<html><body>new layout</body></html>") }.exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("changed"))
    }

    private val noNetwork = HttpGet { error("no network in this test") }
    private val validator = PlaylistUrlValidator(listOf(SpotifyProvider(noNetwork), WebPlaylistProvider(noNetwork)))
    private fun ref(link: String) = (validator.check(link) as? UrlCheck.Valid ?: error("expected valid: $link → ${validator.check(link)}")).ref

    @Test
    fun takesPlaylistAlbumAndShortLinks() {
        assertEquals("playlist/37i9dQZF1DX6XE7HRLM75P", ref("https://open.spotify.com/playlist/37i9dQZF1DX6XE7HRLM75P?si=abc123").playlistId)
        assertEquals("album/4aawyAB9vmqN3uQ7FjRGTy", ref("https://open.spotify.com/intl-en/album/4aawyAB9vmqN3uQ7FjRGTy").playlistId)
        assertEquals("spotify", ref("https://spotify.link/Ab3dEf9Hij").providerId)
        // A song or an artist isn't a playlist.
        assertTrue(validator.check("https://open.spotify.com/track/7bxaFZ1O3cHkgLKMsdC3xR") is UrlCheck.Invalid)
        assertTrue(validator.check("https://open.spotify.com/artist/0TnOYISbd1XYRBk9myaseg") is UrlCheck.Invalid)
    }

    @Test
    fun importsMatchedSongsAsYouTubeStreamsAndCountsTheRest() {
        val page = embed(threeSongs)
        val provider = SpotifyProvider(
            http = HttpGet { url -> assertEquals("https://open.spotify.com/embed/playlist/37i9dQZF1DX6XE7HRLM75P", url); page },
            // The first song is found, the second isn't.
            matcher = SongMatcher { songs, progress ->
                songs.mapIndexed { i, _ -> progress(i + 1); if (i == 0) SongMatch("dQw4w9WgXcQ", 225.0, "https://i.ytimg.example/t.jpg") else null }
            },
        )
        val playlist = provider.fetchPlaylist(ref("https://open.spotify.com/playlist/37i9dQZF1DX6XE7HRLM75P?si=xyz"))
        // One link per playlist, whatever share code came with it.
        assertEquals("https://open.spotify.com/playlist/37i9dQZF1DX6XE7HRLM75P", playlist.link)
        assertEquals("Hot Hits Telugu", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertEquals(1, playlist.missing)
        val t = playlist.tracks.single()
        assertEquals("Chuttamalle", t.title)
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", t.streamUrl)
        assertEquals(false, t.storable)
        assertNull(SpotifyProvider.matching.value)
    }

    @Test
    fun catalogueSearchMatchesNamesSectionsAndTags() {
        assertTrue(SpotifyCatalogue.search("telugu").any { it.title == "Hot Hits Telugu" })
        assertTrue(SpotifyCatalogue.search("bolly").all { it.section == "India" })
        assertEquals("EDM Hits", SpotifyCatalogue.search("edm").first().title)
        assertTrue(SpotifyCatalogue.search("gym").any { it.title == "Gym Hits" })
        assertTrue(SpotifyCatalogue.search("zzqx").isEmpty())
        assertTrue(SpotifyCatalogue.search("  ").isEmpty())
        // Every card names its section, so its language reaches the set.
        assertEquals("Spotify › India › Hot Hits Tamil", SpotifyCatalogue.all.first { it.title == "Hot Hits Tamil" }.origin)
        assertEquals(SpotifyCatalogue.all.size, SpotifyCatalogue.all.map { it.id }.toSet().size)
    }
}
