package com.autodj.dj.stems

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Runs the separation network; the phone app's comes from ONNX Runtime, the TV app has none. */
interface StemEngine {
    /** Opens the model; heavy (it holds the network's working memory until closed). */
    fun open(model: File): StemSession
}

/** An open model: one chunk in, the network's two outputs back. */
interface StemSession : Closeable {
    fun run(mix: FloatArray, spec: FloatArray): DemucsDsp.ChunkOutput
}

/**
 * Stem separation, where the device can run it: whether it can, whether the user turned it on,
 * and the engine (found by name, so the TV app, built without it, simply has none).
 */
object Stems {
    private const val ENGINE = "com.autodj.stems.OnnxStemEngine"
    private const val PREFS = "stems"
    private const val KEY_ON = "on"
    private const val KEY_TRICKS = "tricks"

    /** Phones with less memory than this would have other apps (or the set) killed while it runs. */
    private const val MIN_RAM_BYTES = 5_500_000_000L

    val engine: StemEngine? by lazy {
        runCatching { Class.forName(ENGINE).getDeclaredConstructor().newInstance() as StemEngine }.getOrNull()
    }

    /** The app has the engine, and the phone is 64-bit with the memory for it. */
    fun supported(context: Context): Boolean {
        if (engine == null || Build.SUPPORTED_64_BIT_ABIS.isEmpty()) return false
        val mem = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        return mem.totalMem >= MIN_RAM_BYTES
    }

    fun isOn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        if (!on) StemWindows.clear()
    }

    /** Stems for the bass-kill drops and word loops inside songs too, not only the mixes (on unless turned off). */
    fun tricksOn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TRICKS, true)

    fun setTricksOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TRICKS, on).apply()
    }

    /** Separation will run: supported, switched on, and the model downloaded. */
    fun active(context: Context) = isOn(context) && supported(context) && StemModel.file(context).exists()
}

/**
 * One separated stretch of a song: its four stems at the deck's own sample rate, 16-bit stereo,
 * kept off the Java heap. The "other" stem also carries whatever the model left out, so the four
 * add back to exactly the original audio and switching to them is inaudible.
 */
class StemWindow(
    val uri: String,
    /** For the log. */
    val title: String,
    /** Track time of the first frame, as decoded. */
    val startSec: Double,
    val sampleRate: Int,
    val frames: Int,
    /** drums, bass, other, vocals: interleaved stereo. */
    val stems: Array<ShortBuffer>,
    /** The original audio, mono, for lining the window up with what the deck plays. */
    val guide: FloatArray,
) {
    val endSec get() = startSec + frames / sampleRate.toDouble()

    /** Frames to add to (track time − [startSec]) × rate to land on the deck's audio; set once lined up. */
    @Volatile var lag = 0

    @Volatile var state = State.WAITING

    enum class State { WAITING, ALIGNING, READY, FAILED, LATE }

    companion object {
        /** Packs float stems ([4][2][frames], −1..1) into the off-heap 16-bit form. */
        fun pack(uri: String, title: String, startSec: Double, rate: Int, stems: Array<Array<FloatArray>>, guide: FloatArray): StemWindow {
            val frames = stems[0][0].size
            val buffers = Array(stems.size) { s ->
                val bb = ByteBuffer.allocateDirect(frames * 2 * 2).order(ByteOrder.nativeOrder())
                val sb = bb.asShortBuffer()
                val l = stems[s][0]
                val r = stems[s][1]
                for (i in 0 until frames) {
                    sb.put((l[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
                    sb.put((r[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
                }
                sb.flip()
                sb
            }
            return StemWindow(uri, title, startSec, rate, frames, buffers, guide)
        }
    }
}

/** Separated windows waiting for (or being played by) the decks: a few at a time, oldest dropped. */
object StemWindows {
    /** A mix's two windows and a couple of tricks' (about 35 MB for a full 45 s window, far less for a trick's). */
    private const val KEEP = 6
    private val windows = CopyOnWriteArrayList<StemWindow>()
    private val byUri = ConcurrentHashMap<String, List<StemWindow>>()

    fun put(w: StemWindow) {
        windows += w
        while (windows.size > KEEP) windows.removeAt(0)
        reindex()
    }

    /** The window of [uri] covering track time [sec], if one has been separated. */
    fun at(uri: String?, sec: Double): StemWindow? =
        uri?.let { byUri[it] }?.firstOrNull { sec >= it.startSec && sec < it.endSec }

    fun has(uri: String, fromSec: Double, toSec: Double) =
        byUri[uri].orEmpty().any { it.startSec <= fromSec + 0.5 && it.endSec >= toSec - 0.5 }

    fun clear() {
        windows.clear()
        reindex()
    }

    private fun reindex() {
        byUri.clear()
        windows.groupBy { it.uri }.forEach { (k, v) -> byUri[k] = v }
    }
}

/** Windowed-sinc resampling between the song's rate and the model's 44.1 kHz, from a precomputed 256-phase table. */
object Resampler {
    private const val TAPS = 16
    private const val PHASES = 256

    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x.copyOf()
        val ratio = to.toDouble() / from
        val n = (x.size * ratio).toInt()
        // Below the lower of the two Nyquist frequencies, so nothing folds back.
        val cutoff = minOf(1.0, ratio)
        // table[p][j]: the weight of input sample (c − TAPS + 1 + j) when the output falls p/PHASES past c.
        val table = Array(PHASES) { p ->
            val frac = p / PHASES.toDouble()
            val h = DoubleArray(2 * TAPS) { j ->
                val d = frac - (j - TAPS + 1)
                val w = 0.5 + 0.5 * kotlin.math.cos(Math.PI * d / TAPS)
                val sinc = if (d == 0.0) 1.0 else kotlin.math.sin(Math.PI * d * cutoff) / (Math.PI * d * cutoff)
                sinc * w
            }
            val sum = h.sum()
            FloatArray(2 * TAPS) { (h[it] / sum).toFloat() }
        }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / ratio
            val c = t.toInt()
            val h = table[((t - c) * PHASES).toInt().coerceIn(0, PHASES - 1)]
            var acc = 0f
            val first = c - TAPS + 1
            for (j in 0 until 2 * TAPS) {
                val k = first + j
                if (k >= 0 && k < x.size) acc += x[k] * h[j]
            }
            out[i] = acc
        }
        return out
    }
}
