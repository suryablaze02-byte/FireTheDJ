package com.autodj.cast

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * The mix as a live stream a Chromecast can play: FireTheDJ's own audio output is captured
 * (exactly what the listener hears, both decks and every effect), encoded to AAC and served
 * over plain HTTP on the Wi-Fi network as an endless `audio/aac` stream, like internet radio.
 *
 * Also serves the playing track's artwork, for the TV to show.
 */
class MixStreamer(private val context: Context, private val onLost: (String) -> Unit) {
    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var server: ServerSocket? = null
    private val listeners = CopyOnWriteArrayList<ArrayBlockingQueue<ByteArray>>()
    @Volatile private var running = false

    /** The current track's cover, served at [artUrl]. */
    @Volatile var artwork: File? = null

    var streamUrl: String? = null
        private set

    /** Where the current artwork can be fetched; changes with [artVersion] so the TV refetches it. */
    fun artUrl(artVersion: Int): String? = streamUrl?.substringBeforeLast('/')?.let { "$it/art/$artVersion.jpg" }

    /** Starts capturing and serving; returns the stream's URL. Needs RECORD_AUDIO and a live [projection]. */
    @SuppressLint("MissingPermission") // checked by the caller before the projection was granted
    fun start(projection: MediaProjection): String {
        val host = wifiAddress() ?: throw IOException("Connect the phone to the same Wi-Fi as the TV")
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUid(Process.myUid())
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer * 4, FRAME_BYTES * 8))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES * 4)
                },
                null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            start()
        }
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(host, 0))
        }
        record = rec
        codec = enc
        server = socket
        running = true
        rec.startRecording()
        thread(name = "mix-encode") { encodeLoop(rec, enc) }
        thread(name = "mix-http") { acceptLoop(socket) }
        return "http://${host.hostAddress}:${socket.localPort}/mix.aac".also {
            streamUrl = it
            Log.i(TAG, "streaming the mix at $it")
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        runCatching { record?.stop() }
        record?.release()
        runCatching { codec?.stop() }
        codec?.release()
        record = null
        codec = null
        server = null
        streamUrl = null
        listeners.clear()
    }

    // ---- capture and encode --------------------------------------------------------------

    private fun encodeLoop(rec: AudioRecord, enc: MediaCodec) {
        val pcm = ByteArray(FRAME_BYTES)
        val info = MediaCodec.BufferInfo()
        var frames = 0L
        var lastLevelLog = System.currentTimeMillis()
        var sumSquares = 0.0
        var samples = 0L
        try {
            while (running) {
                val n = rec.read(pcm, 0, pcm.size)
                if (n < 0) {
                    // The capture is gone for good (another app took the screen capture, or Android
                    // ended it); retrying only spins. Report it and stop.
                    Log.w(TAG, "mix capture lost (AudioRecord error $n)")
                    if (running) onLost("The TV lost the mix: Android stopped FireTheDJ's audio capture")
                    break
                }
                if (n == 0) continue
                // How loud the capture is, now and then: silence here means the TV gets silence.
                for (i in 0 until n - 1 step 2) {
                    val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort() / 32768.0
                    sumSquares += s * s
                }
                samples += n / 2
                if (System.currentTimeMillis() - lastLevelLog > 10_000) {
                    Log.i(TAG, "cast capture level: rms %.4f, %d listener(s)".format(sqrt(sumSquares / samples.coerceAtLeast(1)), listeners.size))
                    lastLevelLog = System.currentTimeMillis()
                    sumSquares = 0.0
                    samples = 0
                }
                val inIdx = enc.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = enc.getInputBuffer(inIdx)!!
                    buf.clear()
                    buf.put(pcm, 0, n)
                    enc.queueInputBuffer(inIdx, 0, n, frames * 1_000_000 / RATE, 0)
                    frames += n / 4
                }
                while (true) {
                    val outIdx = enc.dequeueOutputBuffer(info, 0)
                    if (outIdx < 0) break
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val out = enc.getOutputBuffer(outIdx)!!
                        val frame = ByteArray(info.size + 7)
                        adtsHeader(frame, frame.size)
                        out.position(info.offset)
                        out.get(frame, 7, info.size)
                        // A listener that can't keep up loses frames rather than holding the others back.
                        for (q in listeners) q.offer(frame)
                    }
                    enc.releaseOutputBuffer(outIdx, false)
                }
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "mix capture stopped: ${e.message}")
        }
    }

    /** ADTS header for one AAC-LC frame, 44.1 kHz stereo: what makes raw AAC streamable. */
    private fun adtsHeader(b: ByteArray, length: Int) {
        val profile = 2 // AAC LC
        val freqIdx = 4 // 44100 Hz
        val channels = 2
        b[0] = 0xFF.toByte()
        b[1] = 0xF1.toByte()
        b[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (channels shr 2)).toByte()
        b[3] = (((channels and 3) shl 6) + (length shr 11)).toByte()
        b[4] = ((length and 0x7FF) shr 3).toByte()
        b[5] = (((length and 7) shl 5) + 0x1F).toByte()
        b[6] = 0xFC.toByte()
    }

    // ---- HTTP ----------------------------------------------------------------------------

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            thread(name = "mix-client") { serve(client) }
        }
    }

    private fun serve(client: Socket) {
        client.use { c ->
            c.soTimeout = 15_000
            val request = c.getInputStream().bufferedReader().readLine().orEmpty()
            val method = request.substringBefore(' ')
            val path = request.substringAfter(' ').substringBefore(' ')
            Log.i(TAG, "cast client ${c.inetAddress.hostAddress}: $request")
            val out = c.getOutputStream()
            when {
                method == "OPTIONS" -> head(out, 204, "text/plain")
                path.startsWith("/mix.aac") -> stream(out)
                path.startsWith("/art/") -> art(out)
                else -> head(out, 404, "text/plain")
            }
        }
    }

    private fun stream(out: OutputStream) {
        val queue = ArrayBlockingQueue<ByteArray>(QUEUE_FRAMES)
        listeners += queue
        try {
            // No length: the stream lasts as long as the cast.
            head(out, 200, "audio/aac", extra = "Cache-Control: no-cache, no-store\r\n")
            while (running) {
                val frame = queue.poll(1, TimeUnit.SECONDS) ?: continue
                out.write(frame)
            }
        } catch (_: IOException) {
            // The TV hung up; it reconnects if it wants more.
        } finally {
            listeners -= queue
        }
    }

    private fun art(out: OutputStream) {
        val bytes = artwork?.takeIf { it.exists() }?.readBytes()
        if (bytes == null) {
            head(out, 404, "text/plain")
            return
        }
        head(out, 200, "image/jpeg", extra = "Content-Length: ${bytes.size}\r\n")
        out.write(bytes)
    }

    private fun head(out: OutputStream, code: Int, type: String, extra: String = "") {
        val reason = when (code) { 200 -> "OK"; 204 -> "No Content"; else -> "Not Found" }
        out.write(
            ("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\n" +
                // The receiver page reads the audio with Web Audio for its visualizers, which needs CORS.
                "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\n" +
                extra + "Connection: close\r\n\r\n").toByteArray(),
        )
        out.flush()
    }

    /** The phone's address on the Wi-Fi network, where the TV can reach it. */
    private fun wifiAddress(): Inet4Address? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }
    }

    private companion object {
        const val TAG = "AutoDJ"
        const val RATE = 44_100
        const val BITRATE = 192_000
        /** One AAC frame's worth of 16-bit stereo PCM. */
        const val FRAME_BYTES = 1024 * 4
        /** About 10 s of frames per listener before it starts losing them. */
        const val QUEUE_FRAMES = 450
    }
}
