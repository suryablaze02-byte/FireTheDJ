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
        assertEquals(
            listOf("1.3.0", "1.4.0", "1.4.0", "1.5.0", "1.5.1", "1.6.1", "1.3.1", "1.6.0", "2.0.0", "2.0.0", "2.0.0", "2.0.0"),
            all.drop(1).map { it.since },
        )
        val redesign = listOf("Your set", "Slide between tabs", "Effects, live", "Pick a look")
        // After seeing 1.5.1's: the new looks, stems and the redesign.
        assertEquals(listOf("Two new looks", "Stem separation") + redesign, phoneWhatsNew("1.5.1", stems = true).drop(1).map { it.title })
        // A phone that can't run stems doesn't hear about them.
        assertEquals(listOf("Two new looks") + redesign, phoneWhatsNew("1.5.2", stems = false).drop(1).map { it.title })
        assertEquals(listOf("Two new looks") + redesign, phoneWhatsNew("1.6.0", stems = true).drop(1).map { it.title })
        // Seen 1.9's: only the redesign.
        assertEquals(redesign, phoneWhatsNew("1.9.2", stems = true).drop(1).map { it.title })
        // Seen everything: no tour.
        assertTrue(phoneWhatsNew("2.0.0", stems = true).isEmpty())
    }

    @Test
    fun theNewUserTourOpensTheScreensItTalksAbout() {
        val tour = phoneTour(stems = true)
        assertEquals(TourScreen.MUSIC, tour.first { it.target == "stream" }.screen)
        assertEquals(TourScreen.SETTINGS, tour.last().screen)
        assertTrue(phoneTour(stems = false).none { it.target == "stems" })
        // The pictures: the welcome first, the looks step later.
        assertEquals(TourArt.WELCOME, tour.first().art)
        assertTrue(tour.any { it.art == TourArt.LOOKS })
    }
}
