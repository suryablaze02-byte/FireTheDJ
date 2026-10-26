package com.autodj.remote

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Streamed tracks, downloaded once into app storage. Analysis decodes the stored file and the
 * decks play it, so a track costs one download however often it's mixed, and a set never
 * stalls on the network. Files are keyed by the track's stable stream URL. When the store
 * outgrows [maxBytes], the least recently used files go first; an evicted track simply
 * streams again until it is re-downloaded.
 */
class StreamStore(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val download: Downloader = HttpDownloader(),
) {
    /** Writes the resource at a URL into a file; throws on failure. */
    fun interface Downloader {
        fun fetch(url: String, into: File)
    }

    init {
        dir.mkdirs()
        // Leftovers from a download the app was killed during.
        dir.listFiles { f -> f.name.endsWith(PART) }?.forEach { it.delete() }
    }

    /** The stored copy of [url], if it has been downloaded; marks it as recently used. */
    fun fileFor(url: String): File? = file(url).takeIf { it.exists() }?.also { it.setLastModified(System.currentTimeMillis()) }

    /**
     * The stored copy of [url], downloading it first if needed. Blocking; run off the main
     * thread. Downloads land in a temporary file and are renamed when complete, so a stored
     * file is never partial.
     */
    fun fetch(url: String): File {
        fileFor(url)?.let { return it }
        val target = file(url)
        val part = File(dir, target.name + "." + Thread.currentThread().id + PART)
        try {
            download.fetch(url, part)
            if (part.length() == 0L) throw IOException("Empty download")
            synchronized(this) {
                if (!target.exists() && !part.renameTo(target)) throw IOException("Couldn't store the download")
            }
        } finally {
            part.delete()
        }
        trim(keep = target)
        return target
    }

    fun delete(urls: Collection<String>) {
        urls.forEach { file(it).delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Bytes currently stored. */
    fun size(): Long = stored().sumOf { it.length() }

    /** Deletes least recently used files until the store fits, never [keep]. */
    @Synchronized
    private fun trim(keep: File) {
        var total = size()
        if (total <= maxBytes) return
        for (f in stored().sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (f == keep) continue
            total -= f.length()
            f.delete()
        }
    }

    private fun stored() = dir.listFiles { f -> f.isFile && !f.name.endsWith(PART) }.orEmpty().toList()

    private fun file(url: String) = File(dir, sha1(url) + ".audio")

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** 2 GB: roughly 400 club tracks. */
        const val DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024
        private const val PART = ".part"

        @Volatile
        private var instance: StreamStore? = null

        /** One store per app: the analysis workers and both decks share it. */
        fun get(context: Context): StreamStore = instance ?: synchronized(this) {
            instance ?: StreamStore(File(context.applicationContext.filesDir, "streams")).also { instance = it }
        }
    }
}

/** Downloads over HttpURLConnection, following redirects (stream APIs redirect to their CDN). */
class HttpDownloader(private val userAgent: String = "FireTheDJ/1.0") : StreamStore.Downloader {
    override fun fetch(url: String, into: File) {
        var target = URL(url)
        repeat(MAX_REDIRECTS) {
            val conn = target.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", userAgent)
            try {
                when (val code = conn.responseCode) {
                    in 300..399 -> {
                        val next = URL(target, conn.getHeaderField("Location") ?: throw IOException("Redirect without a location"))
                        if (next.protocol != "https") throw IOException("Refusing a redirect to ${next.protocol}")
                        target = next
                        return@repeat
                    }
                    200 -> {
                        conn.inputStream.use { input -> into.outputStream().use { input.copyTo(it, 64 * 1024) } }
                        return
                    }
                    else -> throw IOException("HTTP $code from ${target.host}")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
    }
}
