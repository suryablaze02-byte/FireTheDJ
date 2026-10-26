package com.autodj.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Fixtures are real YouTube Music responses (October 2026), trimmed to a few items each. */
class YtMusicBrowseTest {
    private fun fixture(name: String) = javaClass.getResource("/ytmusic/$name")!!.readText()

    @Test
    fun readsTheMoodAndGenreCategories() {
        val sections = YtMusicBrowse.parseCategories(JSONObject(fixture("moods_and_genres.json")))
        assertEquals(listOf("Moods & moments", "Genres"), sections.map { it.title })
        val chill = sections[0].categories[0]
        assertEquals("Chill", chill.title)
        assertTrue(chill.params.isNotBlank())
        assertTrue(chill.color != null)
        assertEquals(3, sections[1].categories.size)
    }

    @Test
    fun readsPlaylistShelvesOfACategory() {
        val shelves = YtMusicBrowse.parseShelves(JSONObject(fixture("category_party.json")))
        assertEquals(listOf("Indian party", "Party today"), shelves.map { it.title })
        val first = shelves[0].playlists[0]
        assertEquals("Bollywood Dance Essentials", first.title)
        assertEquals("Playlist • YouTube Music", first.subtitle)
        // The browse id's "VL" prefix is dropped: what remains is the playlist id a link carries.
        assertEquals("RDCLAK5uy_m1SfwuulEBVcweY5bCV8jJ6ZCn8M2gKGM", first.playlistId)
        assertEquals("https://music.youtube.com/playlist?list=RDCLAK5uy_m1SfwuulEBVcweY5bCV8jJ6ZCn8M2gKGM", first.link)
        assertTrue(first.thumbnailUrl!!.startsWith("https://"))
    }

    @Test
    fun cardLinksImportThroughTheYouTubeProvider() {
        val card = YtPlaylistCard("Turn Up Time", "", "RDCLAK5uy_l3XabTm3_hzKqB9XOpyJj2xYRO597H5eE", null)
        val check = PlaylistUrlValidator(StreamProviders.default(HttpGet { error("no network") })).check(card.link)
        assertEquals("youtube", (check as UrlCheck.Valid).ref.providerId)
    }

    @Test
    fun readsACuratedPlaylistAcrossItsPages() {
        val sent = mutableListOf<JSONObject>()
        val browse = YtMusicBrowse(post = { _, body ->
            val request = JSONObject(body).also(sent::add)
            if (request.has("continuation")) fixture("playlist_page2.json") else fixture("playlist_page1.json")
        })
        val playlist = browse.playlist("RDCLAK5uy_l3XabTm3_hzKqB9XOpyJj2xYRO597H5eE")
        assertEquals("Turn Up Time", playlist.name)
        // Three rows on the first page, two on the next; the second page has no further token.
        assertEquals(5, playlist.tracks.size)
        assertEquals(2, sent.size)
        assertEquals("VLRDCLAK5uy_l3XabTm3_hzKqB9XOpyJj2xYRO597H5eE", sent[0].getString("browseId"))
        // Signed out, the next page only comes back with the visitor id the first page gave out.
        assertEquals("TEST_VISITOR", sent[1].getJSONObject("context").getJSONObject("client").getString("visitorData"))
        assertEquals("9-dEHfSCZUQ", playlist.tracks[3].videoId)
        val first = playlist.tracks[0]
        assertEquals("NSZ26l3DIKE", first.videoId)
        assertEquals("Spend Dat", first.title)
        assertEquals("Yung Miami", first.artist)
        assertEquals(297.0, first.durationSec!!, 0.0)
        assertTrue(first.thumbnailUrl!!.startsWith("https://"))
    }

    @Test
    fun stopsWhenAContinuationRepeatsThePage() {
        var calls = 0
        val browse = YtMusicBrowse(post = { _, _ -> calls++; fixture("playlist_page1.json") })
        assertEquals(3, browse.playlist("RDCLAK5uy_l3XabTm3_hzKqB9XOpyJj2xYRO597H5eE").tracks.size)
        assertEquals(2, calls)
    }

    @Test
    fun importsCuratedPlaylistsWithoutNewPipe() {
        val browse = YtMusicBrowse(post = { _, body ->
            if (JSONObject(body).has("continuation")) fixture("playlist_page2.json") else fixture("playlist_page1.json")
        })
        val ref = PlaylistRef("youtube", "RDCLAK5uy_l3XabTm3_hzKqB9XOpyJj2xYRO597H5eE", "https://music.youtube.com/playlist?list=RDCLAK5uy_l3XabTm3_hzKqB9XOpyJj2xYRO597H5eE")
        val playlist = YouTubeMusicProvider(browse).fetchPlaylist(ref)
        assertEquals("Turn Up Time", playlist.name)
        val t = playlist.tracks[0]
        assertEquals("https://music.youtube.com/watch?v=NSZ26l3DIKE", t.streamUrl)
        assertEquals("Yung Miami", t.artist)
        assertEquals(false, t.storable)
    }

    @Test
    fun asksForTheCategoryInThePhonesLanguageAndCountry() {
        var sent = ""
        val browse = YtMusicBrowse(post = { _, body -> sent = body; fixture("category_party.json") }, locale = Locale("en", "IN"))
        browse.shelves(YtCategory("Party", "ggMPOg1uX0pmQ0s2V0JRclZs", null))
        val client = JSONObject(sent).getJSONObject("context").getJSONObject("client")
        assertEquals("WEB_REMIX", client.getString("clientName"))
        assertEquals("IN", client.getString("gl"))
        assertEquals("ggMPOg1uX0pmQ0s2V0JRclZs", JSONObject(sent).getString("params"))
    }
}
