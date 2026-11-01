package com.autodj.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TourTest {
    @Test
    fun whatsNewShowsOnlyStepsNewerThanTheLastTour() {
        // Someone whose last tour was before versions were remembered: everything since 1.3, after a welcome.
        val all = phoneWhatsNew("1.2.99", stems = true)
        assertEquals("What's new in FireTheDJ", all.first().title)
        assertEquals(listOf("1.3.0", "1.4.0", "1.4.0", "1.5.0", "1.5.1", "1.3.1", "1.6.0"), all.drop(1).map { it.since })
        // After seeing 1.5.1's: only stems.
        assertEquals(listOf("Stem separation"), phoneWhatsNew("1.5.1", stems = true).drop(1).map { it.title })
        // A phone that can't run stems doesn't hear about them; with nothing else new, no tour.
        assertTrue(phoneWhatsNew("1.5.2", stems = false).isEmpty())
        assertTrue(phoneWhatsNew("1.6.0", stems = true).isEmpty())
    }

    @Test
    fun theNewUserTourOpensTheScreensItTalksAbout() {
        val tour = phoneTour(stems = true)
        assertEquals(TourScreen.MUSIC, tour.first { it.target == "stream" }.screen)
        assertEquals(TourScreen.SETTINGS, tour.last().screen)
        assertTrue(phoneTour(stems = false).none { it.target == "stems" })
    }
}
