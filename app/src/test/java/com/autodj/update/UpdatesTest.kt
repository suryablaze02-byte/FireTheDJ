package com.autodj.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updates.newer("1.2.0", "1.1.0"))
        assertTrue(Updates.newer("1.10.0", "1.9.2"))
        assertTrue(Updates.newer("2.0", "1.9.9"))
        assertFalse(Updates.newer("1.1.0", "1.1.0"))
        assertFalse(Updates.newer("1.0.9", "1.1.0"))
    }

    @Test
    fun readsWhatsNewFromReleaseNotes() {
        val body = """
            ## FireTheDJ 1.1.0
            ### New: FireTheDJ TV
            - **Start a mix from the couch**: pick a genre crate and a mode.
            - **Full-screen visualizers** start on their own.
            Some prose that isn't a bullet.
            - See the [README](https://example.com) for details.
        """.trimIndent()
        assertEquals(
            listOf("Start a mix from the couch", "Full-screen visualizers start on their own.", "See the README for details."),
            Updates.notes(body),
        )
    }

    @Test
    fun plainTextNotesGiveTheirSummary() {
        val body = "FireTheDJ 1.1.0\r\nFireTheDJ now has an Android TV app.\r\n\r\nNew: FireTheDJ TV\r\nA separate app."
        assertEquals(listOf("FireTheDJ now has an Android TV app."), Updates.notes(body))
    }
}
