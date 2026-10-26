package com.autodj.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class StreamStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val downloads = mutableListOf<String>()

    /** Pretends every URL is a file of [size] bytes. */
    private fun store(maxBytes: Long = Long.MAX_VALUE, size: Int = 1000, fail: Boolean = false) =
        StreamStore(tmp.root, maxBytes) { url, into ->
            downloads += url
            if (fail) {
                into.writeBytes(ByteArray(10)) // half-written, then the connection drops
                throw IOException("connection reset")
            }
            into.writeBytes(ByteArray(size))
        }

    @Test
    fun downloadsATrackOnlyOnce() {
        val s = store()
        assertNull(s.fileFor("https://a/1"))
        val f = s.fetch("https://a/1")
        assertEquals(1000L, f.length())
        assertEquals(f, s.fetch("https://a/1"))
        assertEquals(f, s.fileFor("https://a/1"))
        assertEquals(listOf("https://a/1"), downloads)
    }

    @Test
    fun aFailedDownloadLeavesNothingBehind() {
        val s = store(fail = true)
        assertTrue(runCatching { s.fetch("https://a/1") }.isFailure)
        assertNull(s.fileFor("https://a/1"))
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    @Test
    fun evictsTheLeastRecentlyUsedWhenFull() {
        val s = store(maxBytes = 2500)
        val one = s.fetch("https://a/1")
        val two = s.fetch("https://a/2")
        one.setLastModified(1_000_000)
        two.setLastModified(2_000_000)
        s.fetch("https://a/3") // 3000 bytes > 2500: the oldest goes
        assertFalse(one.exists())
        assertTrue(two.exists())
        assertTrue(s.fileFor("https://a/3") != null)
        assertTrue(s.size() <= 2500)
    }

    @Test
    fun deleteAndClearFreeTheFiles() {
        val s = store()
        s.fetch("https://a/1")
        s.fetch("https://a/2")
        s.delete(listOf("https://a/1"))
        assertNull(s.fileFor("https://a/1"))
        assertTrue(s.fileFor("https://a/2") != null)
        s.clear()
        assertEquals(0L, s.size())
    }
}
