package com.autodj.dj

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStatsTest {
    private fun play(set: Long, sec: Int, artist: String = "A", title: String = "T", at: Long = 100, language: String? = null, crate: String = "House") =
        Play(at, set, "u$title", title, artist, "House", language, crate, sec)

    @Test
    fun hoursCountEverySecondButPlaysNeedThirty() {
        val s = listeningStats(listOf(play(1, 200, title = "One"), play(1, 10, title = "Skipped"), play(2, 100, title = "One")))
        assertEquals(310, s.heardSec)
        assertEquals(2, s.sets)
        assertEquals(210, s.longestSec)
        assertEquals(listOf(TopItem("One", 2, "uOne", "A")), s.songs)
    }

    @Test
    fun rangeDropsOlderPlays() {
        val s = listeningStats(listOf(play(1, 100, at = 10), play(2, 50, at = 500)), sinceMs = 100)
        assertEquals(50, s.heardSec)
        assertEquals(1, s.sets)
    }

    @Test
    fun jointArtistsCountForEach() {
        val s = listeningStats(listOf(play(1, 60, artist = "Deep Jandu, Roach Killa & Arif Lohar"), play(1, 60, artist = "Arif Lohar feat. X")))
        assertEquals(TopItem("Arif Lohar", 2), s.artists.first())
        assertEquals(4, s.artists.size)
    }

    @Test
    fun favouritesGoByTimeAndFolderNamesAreDropped() {
        val s = listeningStats(listOf(play(1, 300, crate = "Party · Techno", language = "Hindi"), play(2, 100, language = "English"), play(2, 100)))
        assertEquals("Techno", s.crate)
        assertEquals("Hindi", s.language)
    }

    @Test
    fun nothingPlayedIsEmptyNotACrash() {
        val s = listeningStats(emptyList())
        assertEquals(0, s.sets)
        assertEquals(null, s.crate)
    }

    @Test
    fun formatsTime() {
        assertEquals("25 m", formatHeard(25 * 60))
        assertEquals("42 h 18 m", formatHeard(42 * 3600 + 18 * 60))
    }
}
