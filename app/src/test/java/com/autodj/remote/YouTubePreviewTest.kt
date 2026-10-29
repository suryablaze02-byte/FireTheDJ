package com.autodj.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePreviewTest {
    @Test
    fun aMinuteOfAThreeMinuteSongIsAPreview() {
        // "The Middle": YouTube gave 59 s of a 184 s song.
        assertTrue(YouTubeStreams.isPreview(59.1, 184.0))
        assertTrue(YouTubeStreams.isPreview(30.0, 210.0))
    }

    @Test
    fun theWholeSongOrACloseEditIsNot() {
        assertFalse(YouTubeStreams.isPreview(184.0, 184.0))
        // A radio edit a bit shorter than the listed video.
        assertFalse(YouTubeStreams.isPreview(170.0, 205.0))
        // A short song that's simply short.
        assertFalse(YouTubeStreams.isPreview(45.0, 70.0))
        // Nothing to compare with.
        assertFalse(YouTubeStreams.isPreview(null, 184.0))
        assertFalse(YouTubeStreams.isPreview(59.0, 0.0))
    }
}
