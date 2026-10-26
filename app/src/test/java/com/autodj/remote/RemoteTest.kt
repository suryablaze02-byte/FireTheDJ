package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTest {
    /** A provider whose playlists are named by a `?list=` parameter, like many services. */
    private class ListParamProvider : StreamProvider {
        override val id = "example"
        override val displayName = "Example"
        override val urlRule = PlaylistUrlRule(hosts = setOf("music.example.com"), idParam = "list", idPattern = Regex("[A-Za-z0-9_-]{4,64}"))
        override fun fetchPlaylist(ref: PlaylistRef) = RemotePlaylist(
            ref.link, id, ref.playlistId, "Mix ${ref.playlistId}",
            listOf(RemoteTrack("Song", "Artist", "https://cdn.example.com/1.mp3", id, "1")),
        )
    }

    private val noNetwork = HttpGet { error("no network in this test") }
    private val validator = PlaylistUrlValidator(listOf(ListParamProvider(), AudiusProvider(noNetwork), WebPlaylistProvider(noNetwork)))

    private fun valid(link: String) = validator.check(link) as? UrlCheck.Valid ?: error("expected valid: $link → ${validator.check(link)}")
    private fun reason(link: String) = (validator.check(link) as? UrlCheck.Invalid ?: error("expected invalid: $link")).reason

    @Test
    fun readsThePlaylistIdFromTheListParameter() {
        val v = valid("  https://www.Music.Example.com/playlist?foo=1&list=PLab_12-x&index=3#t=10 ")
        assertEquals("example", v.ref.providerId)
        assertEquals("PLab_12-x", v.ref.playlistId)
        // Normalised: lower-case host, no fragment, so the same playlist is recognised twice.
        assertEquals("https://www.music.example.com/playlist?foo=1&list=PLab_12-x&index=3", v.ref.link)
        // Percent-encoded values are decoded before checking.
        assertEquals("AB-CD", valid("https://music.example.com/p?list=AB%2DCD").ref.playlistId)
    }

    @Test
    fun rejectsBadLinksWithAReason() {
        assertTrue(reason("").contains("Paste"))
        assertTrue(reason("not a url at all").isNotBlank())
        assertTrue(reason("http://music.example.com/p?list=ABCDE").contains("https"))
        assertTrue(reason("https://music.example.com/p?other=1").contains("\"list\""))
        assertTrue(reason("https://music.example.com/p?list=%3Cscript%3E").contains("malformed"))
        assertTrue(reason("https://unknown.example.org/page").contains("no playlist"))
    }

    @Test
    fun routesLinksToTheRightProvider() {
        val audius = valid("https://audius.co/rene_obe/playlist/silentium")
        assertEquals("audius", audius.ref.providerId)
        assertEquals("rene_obe/playlist/silentium", audius.ref.playlistId)
        assertEquals("audius", valid("https://audius.co/sheaf/album/solitude-4317/").ref.providerId)
        assertEquals("web", valid("https://files.example.org/sets/friday.m3u").ref.providerId)
        assertEquals("web", valid("https://files.example.org/a/Artist%20-%20Song.MP3").ref.providerId)
        // An Audius page that isn't a playlist is not taken.
        assertTrue(validator.check("https://audius.co/rene_obe") is UrlCheck.Invalid)
    }

    @Test
    fun parsesM3uPlaylists() {
        val m3u = """
            #EXTM3U
            #EXTINF:215,Daft Punk - One More Time
            tracks/one%20more%20time.mp3
            #EXTINF:-1,
            https://cdn.example.org/Untitled%20Jam.flac
            http://insecure.example.org/skipped.mp3
            # a comment
        """.trimIndent()
        val provider = WebPlaylistProvider { m3u }
        val ref = (PlaylistUrlValidator(listOf(provider)).check("https://files.example.org/sets/friday.m3u") as UrlCheck.Valid).ref
        val p = provider.fetchPlaylist(ref)
        assertEquals("friday", p.name)
        assertEquals(2, p.tracks.size)
        val first = p.tracks[0]
        assertEquals("One More Time", first.title)
        assertEquals("Daft Punk", first.artist)
        assertEquals(215.0, first.durationSec!!, 0.0)
        assertEquals("https://files.example.org/sets/tracks/one%20more%20time.mp3", first.streamUrl)
        assertEquals("Untitled Jam", p.tracks[1].title)
        assertNull(p.tracks[1].durationSec)
    }

    @Test
    fun aDirectAudioLinkIsAOneTrackPlaylist() {
        val provider = WebPlaylistProvider { error("a single file needs no fetch") }
        val ref = (PlaylistUrlValidator(listOf(provider)).check("https://files.example.org/a/Artist%20-%20Song.mp3") as UrlCheck.Valid).ref
        val t = provider.fetchPlaylist(ref).tracks.single()
        assertEquals("Song", t.title)
        assertEquals("Artist", t.artist)
        assertEquals("https://files.example.org/a/Artist%20-%20Song.mp3", t.streamUrl)
    }

    @Test
    fun audiusKeepsOnlyStreamableTracksAndStableStreamUrls() {
        val calls = mutableListOf<String>()
        val http = HttpGet { url ->
            calls += url
            when {
                "/resolve?" in url -> """{"data":[{"id":"3az1B","playlist_name":"Silentium"}]}"""
                "/playlists/3az1B/tracks" in url -> """{"data":[
                    {"id":"bd4lb","title":"Skylight","user":{"name":"moresleep"},"duration":167,"genre":"Hip-Hop/Rap",
                     "is_streamable":true,"is_stream_gated":false,"access":{"stream":true},
                     "artwork":{"480x480":"https://cdn.example.org/480.jpg"}},
                    {"id":"gated","title":"Members only","user":{"name":"x"},"is_streamable":true,"is_stream_gated":true},
                    {"id":"nope","title":"No stream","user":{"name":"x"},"access":{"stream":false}}
                ]}"""
                else -> error("unexpected $url")
            }
        }
        val provider = AudiusProvider(http, appName = "test")
        val p = provider.fetchPlaylist(PlaylistRef("audius", "rene_obe/playlist/silentium", "https://audius.co/rene_obe/playlist/silentium"))
        assertTrue(calls[0].contains("url=https%3A%2F%2Faudius.co%2Frene_obe%2Fplaylist%2Fsilentium"))
        assertEquals("Silentium", p.name)
        val t = p.tracks.single()
        assertEquals("Skylight", t.title)
        assertEquals("moresleep", t.artist)
        assertEquals("Hip-Hop/Rap", t.genre)
        assertEquals("https://cdn.example.org/480.jpg", t.artworkUrl)
        assertEquals(167.0, t.durationSec!!, 0.0)
        // The API endpoint, not a signed CDN link: it stays valid across launches.
        assertEquals("https://api.audius.co/v1/tracks/bd4lb/stream?app_name=test", t.streamUrl)
        // The artist didn't allow downloads: it streams each time instead of being kept.
        assertFalse(t.storable)
    }

    @Test
    fun importerFeedsTheSinkOnlyOnSuccess() {
        val ingested = mutableListOf<RemotePlaylist>()
        val importer = PlaylistImporter(PlaylistUrlValidator(listOf(ListParamProvider())), TrackSink { ingested += it })
        val ok = importer.import("https://music.example.com/p?list=ABCDE")
        assertTrue(ok is ImportResult.Imported)
        assertEquals("Mix ABCDE", ingested.single().name)
        assertTrue(importer.import("https://music.example.com/p") is ImportResult.Failed)
        assertEquals(1, ingested.size)
    }

    @Test
    fun storeRoundTripsPlaylists() {
        val p = RemotePlaylist(
            "https://audius.co/a/playlist/b", "audius", "3az1B", "Silentium",
            listOf(
                RemoteTrack("Skylight", "moresleep", "https://api.audius.co/v1/tracks/bd4lb/stream?app_name=autodj", "audius", "bd4lb", "https://cdn/x.jpg", 167.0, "Hip-Hop/Rap"),
                RemoteTrack("Bare", "", "https://cdn/b.mp3", "web", "https://cdn/b.mp3"),
            ),
        )
        assertEquals(listOf(p), RemoteLibrary.fromJson(RemoteLibrary.toJson(listOf(p))))
    }

    @Test
    fun routesJamendoAndArchiveLinks() {
        val all = PlaylistUrlValidator(StreamProviders.default(noNetwork, jamendoClientId = "k"))
        fun ref(link: String) = (all.check(link) as UrlCheck.Valid).ref
        assertEquals(PlaylistRef("jamendo", "album/123456", "https://www.jamendo.com/album/123456/night-drive"), ref("https://www.jamendo.com/album/123456/night-drive"))
        assertEquals("playlist/500608900", ref("https://www.jamendo.com/en/playlist/500608900/techno").playlistId)
        assertEquals("archive", ref("https://archive.org/details/microbio05").providerId)
        assertEquals("microbio05", ref("https://archive.org/details/microbio05/m_05-01.mp3").playlistId)
        assertTrue(all.check("https://www.jamendo.com/artist/1/someone") is UrlCheck.Invalid)
    }

    @Test
    fun jamendoNeedsAClientIdAndHonoursDownloadRights() {
        val ref = PlaylistRef("jamendo", "album/42", "https://www.jamendo.com/album/42/x")
        assertTrue(runCatching { JamendoProvider(noNetwork, "").fetchPlaylist(ref) }.exceptionOrNull()!!.message!!.contains("client id"))
        var asked = ""
        val http = HttpGet { url ->
            asked = url
            """{"headers":{"status":"success"},"results":[{"name":"Night Drive","artist_name":"Kraftfeld","image":"https://img/a.jpg","tracks":[
                {"id":"1","name":"Intro","duration":"200","audio":"https://mp3d/1","audiodownload_allowed":true},
                {"id":"2","name":"Peak","artist_name":"Guest","duration":"301","audio":"https://mp3d/2","audiodownload_allowed":false}
            ]}]}"""
        }
        val p = JamendoProvider(http, "abc").fetchPlaylist(ref)
        assertTrue(asked.startsWith("https://api.jamendo.com/v3.0/albums/tracks/?client_id=abc") && "id=42" in asked)
        assertEquals("Night Drive", p.name)
        assertEquals(listOf("Kraftfeld", "Guest"), p.tracks.map { it.artist })
        assertEquals(listOf(true, false), p.tracks.map { it.storable })
        assertEquals(301.0, p.tracks[1].durationSec!!, 0.0)
        assertEquals("https://img/a.jpg", p.tracks[0].artworkUrl)
    }

    @Test
    fun archiveTakesOneFilePerRecordingInTrackOrder() {
        val http = HttpGet {
            """{"metadata":{"title":"Cerelac EP","creator":"THIB","subject":"netlabel; minimal; techno"},"files":[
                {"name":"m_05-02.flac","source":"original","format":"Flac","track":"2/2","title":"Second"},
                {"name":"m_05-02.mp3","source":"derivative","original":"m_05-02.flac","format":"VBR MP3","track":"2/2","title":"Second","length":"464.48"},
                {"name":"m_05-01.ogg","source":"derivative","original":"m_05-01 a.mp3","format":"Ogg Vorbis","track":"1"},
                {"name":"m_05-01 a.mp3","source":"original","format":"VBR MP3","track":"1","title":"First","artist":"The Human Isolated Bacteria"},
                {"name":"cover.jpg","source":"original","format":"JPEG"}
            ]}"""
        }
        val p = InternetArchiveProvider(http).fetchPlaylist(PlaylistRef("archive", "microbio05", "https://archive.org/details/microbio05"))
        assertEquals("Cerelac EP", p.name)
        assertEquals(listOf("First", "Second"), p.tracks.map { it.title })
        assertEquals("https://archive.org/download/microbio05/m_05-01%20a.mp3", p.tracks[0].streamUrl)
        assertEquals("https://archive.org/download/microbio05/m_05-02.mp3", p.tracks[1].streamUrl)
        assertEquals(listOf("The Human Isolated Bacteria", "THIB"), p.tracks.map { it.artist })
        assertEquals("minimal", p.tracks[0].genre)
        assertEquals(464.48, p.tracks[1].durationSec!!, 1e-9)
    }
}
