package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockTest {
    // Shaped like the API's answer for a hash prefix: several videos share it.
    private val answer = """
        [
          {"videoID": "otherVideo1", "segments": [{"category": "music_offtopic", "actionType": "skip", "segment": [0, 50], "videoDuration": 300}]},
          {"videoID": "dQw4w9WgXcQ", "segments": [
            {"category": "music_offtopic", "actionType": "skip", "segment": [0.0, 31.5], "videoDuration": 245.2},
            {"category": "sponsor", "actionType": "skip", "segment": [100, 110], "videoDuration": 245.2},
            {"category": "music_offtopic", "actionType": "skip", "segment": [221.0, 245.2], "videoDuration": 245.2}
          ]}
        ]
    """.trimIndent()

    @Test
    fun onlySendsTheFirstFourCharactersOfTheHash() {
        assertEquals("5f6b", SponsorBlock.hashPrefix("dQw4w9WgXcQ"))
    }

    @Test
    fun picksOurVideoAndItsNonMusicPartsOnly() {
        val marked = SponsorBlock.parse(answer, "dQw4w9WgXcQ")
        assertEquals(listOf(SponsorBlock.Span(0.0, 31.5), SponsorBlock.Span(221.0, 245.2)), marked.map { it.span })
    }

    @Test
    fun nothingMarkedIsEmpty() {
        assertTrue(SponsorBlock.parse(null, "dQw4w9WgXcQ").isEmpty())
        assertTrue(SponsorBlock.parse(answer, "notInThere1").isEmpty())
    }

    @Test
    fun partsOfAnotherCutOfTheVideoAreIgnored() {
        val marked = SponsorBlock.parse(answer, "dQw4w9WgXcQ")
        // Our audio is the same length (within 2 s): the times apply.
        assertEquals(2, SponsorBlock.fitting(marked, 244.0).size)
        // A different upload (the preview swap) or edit: they don't.
        assertTrue(SponsorBlock.fitting(marked, 212.0).isEmpty())
        // No length given: trusted.
        val unknown = listOf(SponsorBlock.Marked(SponsorBlock.Span(0.0, 20.0), 0.0))
        assertEquals(1, SponsorBlock.fitting(unknown, 212.0).size)
    }
}
