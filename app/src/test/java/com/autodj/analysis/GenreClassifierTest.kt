package com.autodj.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreClassifierTest {
    @Test
    fun recognisesFamiliesFromFeatures() {
        val cases = listOf(
            Triple(AudioFeatures(0.35, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2), 124.0, Genres.HOUSE),
            Triple(AudioFeatures(0.40, 1400.0, 0.95, 0.2, 1.1, 4.0, 0.1), 134.0, Genres.TECHNO),
            Triple(AudioFeatures(0.30, 3000.0, 0.95, 0.2, 1.2, 4.0, 0.35), 138.0, Genres.TRANCE),
            Triple(AudioFeatures(0.30, 2600.0, 0.30, 0.9, 1.1, 7.0, 0.2), 174.0, Genres.DNB),
            Triple(AudioFeatures(0.60, 1800.0, 0.30, 0.6, 1.0, 3.0, 0.3), 140.0, Genres.BASS),
            Triple(AudioFeatures(0.40, 1500.0, 0.40, 0.8, 1.0, 2.0, 0.15), 90.0, Genres.HIPHOP),
            Triple(AudioFeatures(0.20, 2000.0, 0.20, 0.2, 1.0, 1.0, 0.1), 80.0, Genres.CHILL),
        )
        for ((f, bpm, expected) in cases) {
            val (family, conf) = GenreClassifier.classifyAudio(f, bpm)
            assertEquals("bpm $bpm", expected, family)
            assertTrue("$expected confidence $conf", conf >= GenreClassifier.MIN_CONFIDENCE)
        }
    }

    @Test
    fun oddTracksAreUnclassified() {
        val r = GenreClassifier.classify(AudioFeatures(0.3, 2000.0, 0.65, 0.4, 1.0, 5.0, 0.2), 60.0, "")
        assertEquals(Genres.UNCLASSIFIED, r.family)
    }

    @Test
    fun tagsAreFusedWithAudio() {
        val house = AudioFeatures(0.35, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)
        val agree = GenreClassifier.classify(house, 124.0, "Deep House")
        assertEquals(Genres.HOUSE, agree.family)
        assertEquals("Deep House", agree.label)
        assertEquals("tag+audio", agree.source)
        assertTrue(agree.confidence >= 0.9f)

        val disagree = GenreClassifier.classify(house, 124.0, "Hip-Hop")
        assertEquals(Genres.HIPHOP, disagree.family)
        assertEquals("tag", disagree.source)

        val untagged = GenreClassifier.classify(house, 124.0, "")
        assertEquals(Genres.HOUSE, untagged.family)
        assertEquals("audio", untagged.source)
    }

    @Test
    fun genericTagsLetTheAudioDecide() {
        val house = AudioFeatures(0.35, 2000.0, 0.95, 0.2, 1.6, 4.0, 0.2)
        for (tag in listOf("Dance", "Electronic", "Pop", "Club", "Dance/Electronic", "Pop & Dance")) {
            assertTrue(tag, Genres.isGeneric(tag))
            val r = GenreClassifier.classify(house, 124.0, tag)
            assertEquals(tag, Genres.HOUSE, r.family)
            assertEquals("audio", r.source)
        }
        // Too unclear to call from audio: the generic tag still places it.
        val unclear = GenreClassifier.classify(AudioFeatures(0.3, 2000.0, 0.65, 0.4, 1.0, 5.0, 0.2), 60.0, "Dance")
        assertEquals(Genres.POP, unclear.family)
        assertEquals("tag", unclear.source)
        // Specific tags are not generic.
        for (tag in listOf("Tech House", "Trance", "Hip-Hop", "Electro House", "Dance Pop Rock")) {
            assertFalse(tag, Genres.isGeneric(tag))
        }
    }

    @Test
    fun tagFamilies() {
        assertEquals(Genres.HOUSE, Genres.family("Tech House"))
        assertEquals(Genres.DNB, Genres.family("Drum & Bass"))
        assertEquals(Genres.BASS, Genres.family("Dubstep"))
        assertEquals(Genres.HIPHOP, Genres.family("Trap"))
        assertEquals(null, Genres.family(""))
    }

    @Test
    fun storeGenresLandInCrates() {
        assertEquals(Genres.HIPHOP, Genres.family("Hip-Hop/Rap"))
        assertEquals(Genres.HIPHOP, Genres.family("R&B/Soul"))
        assertEquals(Genres.POP, Genres.family("Bollywood"))
        assertEquals(Genres.POP, Genres.family("Indian Pop"))
        assertEquals(Genres.LATIN, Genres.family("Reggae"))
        assertEquals(Genres.ROCK, Genres.family("Singer/Songwriter"))
        assertEquals(Genres.CHILL, Genres.family("Jazz"))
        assertEquals(Genres.HOUSE, Genres.family("House"))
        assertTrue(Genres.isGeneric("Soundtrack"))
    }

    @Test
    fun searchTitlesDropMixAndFeatureNotes() {
        assertEquals("You", GenreLookup.searchTitle("You (Original mix)"))
        assertEquals("Take Me Up", GenreLookup.searchTitle("Take Me Up (ft. Donna Blakely)"))
        assertEquals("Butter", GenreLookup.searchTitle("Butter feat. Megan Thee Stallion"))
        assertEquals("Sunshine", GenreLookup.searchTitle("Sunshine [Radio mix]"))
    }
}
