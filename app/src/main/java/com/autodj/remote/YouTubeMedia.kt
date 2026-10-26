package com.autodj.remote

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * googlevideo paces one open-ended response down to roughly playback speed, but serves bounded
 * ranges at line rate, so YouTube audio is always read as a run of ranges of this size.
 */
private const val RANGE_BYTES = 1024L * 1024

private val REFUSED = setOf(403, 404, 410)

/** What decks load tracks through: files, content uris and https as before, YouTube watch links resolved. */
@UnstableApi
object YouTubeMedia {
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(false)
        val resolving = ResolvingDataSource.Factory(ChunkedDataSource.Factory(http)) { spec ->
            // Runs on the loader thread when the deck opens the track, where blocking is fine.
            val videoId = YouTubeLinks.videoId(spec.uri.toString()) ?: return@Factory spec
            val stream = YouTubeStreams.resolve(videoId)
            spec.buildUpon()
                .setUri(Uri.parse(stream.url))
                .setHttpRequestHeaders(spec.httpRequestHeaders + stream.headers)
                .build()
        }
        return DefaultDataSource.Factory(context, resolving)
    }
}

/**
 * Reads a googlevideo URL as consecutive [RANGE_BYTES] ranges while looking like one stream to
 * the player. The file size comes from the URL's `clen`; other URLs are passed straight through.
 */
@UnstableApi
private class ChunkedDataSource(private val upstream: DataSource) : DataSource {
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    private var spec: DataSpec? = null
    private var passthrough = false
    private var upstreamOpen = false
    private var position = 0L
    private var remaining = 0L
    private var chunkLeft = 0L

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        if (total == null || dataSpec.uri.host?.endsWith(".googlevideo.com") != true) {
            passthrough = true
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        spec = dataSpec
        position = dataSpec.position
        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total else min(total, position + dataSpec.length)
        remaining = (end - position).coerceAtLeast(0)
        chunkLeft = 0
        if (remaining > 0) openChunk()
        return remaining
    }

    private fun openChunk() {
        val base = spec!!
        val length = min(RANGE_BYTES, remaining)
        try {
            upstream.open(base.buildUpon().setPosition(position).setLength(length).build())
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // Expired or refused: the player retries the load, and that retry should extract afresh.
            if (e.responseCode in REFUSED) YouTubeStreams.forgetUrl(base.uri.toString())
            throw e
        }
        upstreamOpen = true
        chunkLeft = length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkLeft == 0L) {
            upstream.close()
            upstreamOpen = false
            openChunk()
        }
        val n = upstream.read(buffer, offset, min(length.toLong(), chunkLeft).toInt())
        // A range that ends short is a broken connection, not the end of the track.
        if (n == C.RESULT_END_OF_INPUT) throw IOException("Stream ended $chunkLeft bytes early at $position")
        position += n
        remaining -= n
        chunkLeft -= n
        return n
    }

    override fun getUri(): Uri? = if (passthrough) upstream.uri else spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (upstreamOpen) upstream.close()
        upstreamOpen = false
        spec = null
    }
}

/**
 * A YouTube track for [android.media.MediaExtractor], so analysis decodes it straight from the
 * network without keeping a copy. Reads go out as [RANGE_BYTES] ranges; the last few are kept,
 * since the extractor reads mostly forwards with small jumps back.
 */
class YouTubeAudioSource(private val videoId: String) : MediaDataSource() {
    private var stream = YouTubeStreams.resolve(videoId)
    private val total = stream.contentLength ?: throw IOException("YouTube didn't say how long the track is")
    private val blocks = object : LinkedHashMap<Long, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>) = size > KEEP_BLOCKS
    }

    override fun getSize() = total

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= total) return -1
        var done = 0
        while (done < size && position + done < total) {
            val at = position + done
            val index = at / RANGE_BYTES
            val block = blocks.getOrPut(index) { fetch(index) }
            val from = (at - index * RANGE_BYTES).toInt()
            val n = min(size - done, block.size - from)
            if (n <= 0) break
            System.arraycopy(block, from, buffer, offset + done, n)
            done += n
        }
        return if (done == 0) -1 else done
    }

    /** Where the bytes are fetched from: the resolved URL, or the server it last redirected to. */
    private var mediaUrl = stream.url
    private var fetches = 0
    private var fetchMs = 0L

    private fun fetch(index: Long): ByteArray {
        val start = index * RANGE_BYTES
        val end = min(total, start + RANGE_BYTES) - 1
        val t0 = System.currentTimeMillis()
        return try {
            get(start, end)
        } catch (e: RefusedException) {
            // The URL went stale mid-analysis: extract a fresh one, once.
            YouTubeStreams.forgetUrl(stream.url)
            stream = YouTubeStreams.resolve(videoId)
            mediaUrl = stream.url
            get(start, end)
        }.also {
            fetches++
            fetchMs += System.currentTimeMillis() - t0
        }
    }

    private fun get(start: Long, end: Long): ByteArray {
        repeat(MAX_REDIRECTS) {
            val conn = URL(mediaUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            stream.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.setRequestProperty("Range", "bytes=$start-$end")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    // googlevideo hands a range to another of its servers; later ranges go there too.
                    val next = URL(conn.url, conn.getHeaderField("Location") ?: throw IOException("Redirect without a location"))
                    if (next.protocol != "https") throw IOException("Refusing a redirect to ${next.protocol}")
                    mediaUrl = next.toString()
                    return@repeat
                }
                if (code in REFUSED) throw RefusedException(code)
                if (code != 206 && code != 200) throw IOException("HTTP $code from ${conn.url.host}")
                val bytes = conn.inputStream.use { it.readBytes() }
                val expected = (end - start + 1).toInt()
                if (bytes.size < expected) throw IOException("Range $start-$end came back short (${bytes.size} bytes)")
                return if (code == 200) bytes.copyOfRange(start.toInt(), start.toInt() + expected) else bytes
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    override fun close() {
        // The extractor closes its source too, so this can run more than once.
        if (fetches > 0) Log.i("AutoDJ", "YouTube $videoId: $fetches ranges, ${total / 1024} KB in $fetchMs ms")
        fetches = 0
        blocks.clear()
    }

    private class RefusedException(code: Int) : IOException("HTTP $code")

    private companion object {
        const val KEEP_BLOCKS = 4
        const val MAX_REDIRECTS = 5
    }
}
