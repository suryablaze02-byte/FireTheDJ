package com.autodj.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkTitleTest {
    @Test
    fun videoTitlesComeDownToTheSongName() {
        assertEquals("Shape of You", ArtworkRepository.cleanTitle("Ed Sheeran - Shape of You (Official Music Video)", "Ed Sheeran"))
        assertEquals("Truth Hurts", ArtworkRepository.cleanTitle("Truth Hurts [Lyrics]", "Lizzo"))
        assertEquals("Work from Home", ArtworkRepository.cleanTitle("Work from Home ft. Ty Dolla \$ign", "Fifth Harmony"))
        assertEquals("Tauba Tauba", ArtworkRepository.cleanTitle("Tauba Tauba", "Karan Aujla"))
        // A dash that isn't "Artist - " stays.
        assertEquals("Love Me Now - Remix", ArtworkRepository.cleanTitle("Love Me Now - Remix", "Kygo"))
    }
}
