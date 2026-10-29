package com.autodj.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.autodj.remote.YouTubeAudioSource
import com.autodj.remote.YouTubeLinks
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Semaphore
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes any audio file Android can play into mono float PCM at roughly 11 kHz. */
object PcmDecoder {
    private const val TARGET_RATE = 11025.0

    /** Longer files (whole albums, DJ mixes) aren't mixable songs, and would fill the memory. */
    const val MAX_SEC = 20 * 60

    /**
     * Whole-song decodes running at once. Analysis and lyric alignment both decode, on several
     * threads; a library of long hi-res files decoded all at once runs the app out of memory.
     */
    private val decodes = Semaphore(4)

    class Pcm(val samples: FloatArray, val sampleRate: Double) {
        val durationSec get() = samples.size / sampleRate
    }

    /** A file too long to analyse; its message says so plainly. */
    class TooLong(sec: Long) : IllegalStateException("Longer than ${MAX_SEC / 60} minutes (${sec / 60} min), too long to mix")

    fun decodeMono(context: Context, uri: Uri): Pcm {
        decodes.acquire()
        try {
            return decode(context, uri)
        } finally {
            decodes.release()
        }
    }

    private fun decode(context: Context, uri: Uri): Pcm {
        val extractor = MediaExtractor()
        // A YouTube track is read from the network in ranges; it is never stored.
        val youTube = YouTubeLinks.videoId(uri.toString())?.let { YouTubeAudioSource(it) }
        try {
            if (youTube != null) extractor.setDataSource(youTube) else extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            youTube?.close()
            throw e
        }
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            youTube?.close()
            error("No audio track found")
        }
        val format = extractor.getTrackFormat(trackIndex)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
        if (durationUs > MAX_SEC * 1_000_000L) {
            extractor.release()
            youTube?.close()
            throw TooLong(durationUs / 1_000_000)
        }
        extractor.selectTrack(trackIndex)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var factor = max(1, (sampleRate / TARGET_RATE).roundToInt())
        // Sized from the length up front, so a long song isn't grown and copied twice over.
        val expected = if (durationUs > 0) (durationUs / 1e6 * sampleRate / factor).toInt() + 4096 else 1 shl 20
        val out = FloatBuilder(expected)
        val limit = (MAX_SEC * TARGET_RATE * 1.1).toInt()
        var scratch = FloatArray(0)
        var acc = 0f
        var accCount = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                // Feed every free input buffer without blocking, so the codec always has work queued.
                while (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(0)
                    if (inIdx < 0) break
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 2_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    factor = max(1, (sampleRate / TARGET_RATE).roundToInt())
                } else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.nativeOrder())
                    // Downmix to mono and decimate by box-averaging `factor` frames.
                    val n = readSamples(buf, encoding, scratch).also { scratch = it.second }.first
                    val scale = 1f / channels
                    var i = 0
                    while (i + channels <= n) {
                        var s = 0f
                        for (c in 0 until channels) s += scratch[i + c]
                        acc += s * scale
                        if (++accCount == factor) { out.add(acc / factor); acc = 0f; accCount = 0 }
                        i += channels
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    // No length in the file: stop at the limit rather than run out of memory.
                    if (out.size > limit) throw TooLong(out.size / (sampleRate.toLong() / factor))
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
            youTube?.close()
        }
        return Pcm(out.toArray(), sampleRate.toDouble() / factor)
    }

    /**
     * The buffer's samples as floats in -1..1, whatever the decoder put out: 16-bit (most
     * phones), float, or the 24- and 32-bit integers some decoders give hi-res FLAC. Bulk-read
     * where possible; per-element buffer reads are slow on ART. Returns the count and the scratch array.
     */
    internal fun readSamples(buf: ByteBuffer, encoding: Int, scratch: FloatArray): Pair<Int, FloatArray> {
        val bytes = buf.remaining()
        val n = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> bytes / 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> bytes / 3
            AudioFormat.ENCODING_PCM_8BIT -> bytes
            else -> bytes / 2
        }
        val out = if (scratch.size < n) FloatArray(n) else scratch
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> buf.asFloatBuffer().get(out, 0, n)
            AudioFormat.ENCODING_PCM_32BIT -> {
                val ib = buf.asIntBuffer()
                for (i in 0 until n) out[i] = ib.get(i) / 2147483648f
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                // Little-endian, three bytes a sample.
                val raw = ByteArray(n * 3)
                buf.get(raw)
                for (i in 0 until n) {
                    val b = i * 3
                    val v = (raw[b].toInt() and 0xFF) or ((raw[b + 1].toInt() and 0xFF) shl 8) or (raw[b + 2].toInt() shl 16)
                    out[i] = v / 8388608f
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> for (i in 0 until n) out[i] = ((buf.get().toInt() and 0xFF) - 128) / 128f
            else -> {
                var shorts = shortScratch.get()!!
                if (shorts.size < n) shorts = ShortArray(n).also { shortScratch.set(it) }
                buf.asShortBuffer().get(shorts, 0, n)
                for (i in 0 until n) out[i] = shorts[i] / 32768f
            }
        }
        return n to out
    }

    /** 16-bit samples copied out in bulk, one array per decoding thread. */
    private val shortScratch = ThreadLocal.withInitial { ShortArray(0) }

    private class FloatBuilder(capacity: Int) {
        private var data = FloatArray(capacity)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size + data.size / 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
    }
}
