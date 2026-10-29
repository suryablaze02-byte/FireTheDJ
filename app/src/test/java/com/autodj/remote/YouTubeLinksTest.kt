package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinksTest {
    private val noNetwork = HttpGet { error("no network in this test") }
    private val validator = PlaylistUrlValidator(StreamProviders.default(noNetwork))

    private fun valid(link: String) = validator.check(link) as? UrlCheck.Valid ?: error("expected valid: $link → ${validator.check(link)}")

    @Test
    fun routesYouTubeMusicPlaylistsToTheYouTubeProvider() {
        val v = valid("https://music.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG")
        assertEquals("youtube", v.ref.providerId)
        assertEquals("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG", v.ref.playlistId)
        // Albums, and a video played from inside a playlist, name the playlist the same way.
        assertEquals("OLAK5uy_kVB6LyKpG0RmcEb5dXr_n0aTBuX3W6dVc", valid("https://music.youtube.com/playlist?list=OLAK5uy_kVB6LyKpG0RmcEb5dXr_n0aTBuX3W6dVc").ref.playlistId)
        assertEquals("youtube", valid("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890").ref.providerId)
        // A lone video isn't a playlist.
        assertTrue((validator.check("https://music.youtube.com/watch?v=dQw4w9WgXcQ") as UrlCheck.Invalid).reason.contains("\"list\""))
    }

    @Test
    fun readsVideoIdsBackFromTrackLinks() {
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId(YouTubeLinks.watchUrl("dQw4w9WgXcQ")))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://www.youtube.com/watch?list=PL1&v=dQw4w9WgXcQ&t=3"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://youtu.be/dQw4w9WgXcQ"))
        // Not YouTube tracks: other hosts, other pages, malformed ids, local files.
        assertNull(YouTubeLinks.videoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLinks.videoId("https://music.youtube.com/playlist?list=PL1234"))
        assertNull(YouTubeLinks.videoId("https://music.youtube.com/watch?v=short"))
        assertNull(YouTubeLinks.videoId("content://media/external/audio/media/12"))
        assertNull(YouTubeLinks.videoId("https://api.audius.co/v1/tracks/abc/stream?app_name=autodj"))
    }

    @Test
    fun takesArtistAndTitleFromTopicChannelsAndVideoNames() {
        assertEquals("Daft Punk" to "One More Time", YouTubeLinks.artistAndTitle("Daft Punk - Topic", "One More Time"))
        assertEquals("Fred again.." to "Delilah (pull me out of this)", YouTubeLinks.artistAndTitle("Fred again..", "Fred again.. - Delilah (pull me out of this) [Official Video]"))
        assertEquals("Dua Lipa" to "Houdini", YouTubeLinks.artistAndTitle("DuaLipaVEVO", "Dua Lipa - Houdini (Official Music Video)"))
        assertEquals("Disclosure" to "Latch", YouTubeLinks.artistAndTitle("Disclosure", "Latch (Lyrics)"))
        // A title that is only an upload note keeps its text.
        assertEquals("X" to "(Official Video)", YouTubeLinks.artistAndTitle("X", "(Official Video)"))
    }

    @Test
    fun aDashInsideBracketsIsPartOfTheNote() {
        assertEquals("Uploader" to "Miss You (Club Mix - Extended Version)", YouTubeLinks.artistAndTitle("Uploader", "Miss You (Club Mix - Extended Version)"))
        assertEquals("Lynx" to "Miss You (Club Mix - Extended Version)", YouTubeLinks.artistAndTitle("Uploader", "Lynx - Miss You (Club Mix - Extended Version)"))
    }
}
