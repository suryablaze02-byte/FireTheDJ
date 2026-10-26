package com.autodj.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.autodj.remote.YouTubeAudioSource
import com.autodj.remote.YouTubeLinks
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes any audio file Android can play into mono float PCM at roughly 11 kHz. */
object PcmDecoder {
    private const val TARGET_RATE = 11025.0

    class Pcm(val samples: FloatArray, val sampleRate: Double) {
        val durationSec get() = samples.size / sampleRate
    }

    fun decodeMono(context: Context, uri: Uri): Pcm {
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
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        var factor = max(1, (sampleRate / TARGET_RATE).roundToInt())
        val out = FloatBuilder()
        var shortScratch = ShortArray(0)
        var floatScratch = FloatArray(0)
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
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    factor = max(1, (sampleRate / TARGET_RATE).roundToInt())
                } else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.nativeOrder())
                    // Downmix to mono and decimate by box-averaging `factor` frames.
                    // Bulk-copy the buffer out first; per-element buffer reads are slow on ART.
                    val n: Int
                    if (floatPcm) {
                        val fb = buf.asFloatBuffer()
                        n = fb.remaining()
                        if (floatScratch.size < n) floatScratch = FloatArray(n)
                        fb.get(floatScratch, 0, n)
                    } else {
                        val sb = buf.asShortBuffer()
                        n = sb.remaining()
                        if (shortScratch.size < n) shortScratch = ShortArray(n)
                        sb.get(shortScratch, 0, n)
                    }
                    val scale = if (floatPcm) 1f / channels else 1f / (32768f * channels)
                    var i = 0
                    while (i + channels <= n) {
                        var s = 0f
                        for (c in 0 until channels) s += if (floatPcm) floatScratch[i + c] else shortScratch[i + c].toFloat()
                        acc += s * scale
                        if (++accCount == factor) { out.add(acc / factor); acc = 0f; accCount = 0 }
                        i += channels
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
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

    private class FloatBuilder {
        private var data = FloatArray(1 shl 20)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
