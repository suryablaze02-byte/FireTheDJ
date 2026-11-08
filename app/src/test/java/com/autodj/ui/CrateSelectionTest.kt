package com.autodj.ui

import com.autodj.analysis.Genres
import com.autodj.dj.CRATE_SEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrateSelectionTest {
    @Test
    fun severalCratesHoldOnlyTheirGenres() {
        val picked = "House${CRATE_SEP}Techno"
        assertTrue(inCrate("House", picked))
        assertTrue(inCrate("Techno", picked))
        assertFalse(inCrate("Rock", picked))
        assertTrue(inCrate("Rock", ALL))
        assertFalse(inCrate(Genres.UNCLASSIFIED, ALL))
    }

    @Test
    fun namesThePick() {
        assertEquals("All genres", crateLabel(ALL))
        assertEquals("House", crateLabel("House"))
        assertEquals("House + Techno", crateLabel("House${CRATE_SEP}Techno"))
        assertEquals("House + 2 more", crateLabel("House${CRATE_SEP}Techno${CRATE_SEP}Rock"))
    }
}
