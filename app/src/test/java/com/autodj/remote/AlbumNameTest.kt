package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumNameTest {
    @Test
    fun dropsYouTubesPrefix() {
        assertEquals("1989", albumName("Album – 1989"))
        assertEquals("5 Taara", albumName("Single · 5 Taara"))
        assertEquals("Red", albumName("EP - Red"))
    }

    @Test
    fun leavesOtherNamesAlone() {
        assertEquals("Album Mix", albumName("Album Mix"))
        assertEquals("Album –", albumName("Album –"))
    }
}
