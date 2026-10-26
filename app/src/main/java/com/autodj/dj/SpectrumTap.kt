package com.autodj.dj

import com.autodj.analysis.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The live spectrum of what a deck outputs (effects and crossfade gain included), for the
 * visualizer. The audio thread feeds it as the effects chain processes; the UI reads the
 * frame for the moment actually being heard. Audio is processed some 0.2–0.5 s before it
 * reaches the speaker, so every frame is stamped with its track time and looked up by the
 * deck's playback position rather than taken as "latest".
 *
 * Reads are not locked: a frame being overwritten while drawn costs one slightly wrong frame
 * of a visual, never audio.
 */
class SpectrumTap(val bands: Int = BANDS) {
    private val times = DoubleArray(FRAMES) { Double.NaN }
    private val levels = FloatArray(FRAMES * bands)
    private val rms = FloatArray(FRAMES)

    @Volatile
    private var written = 0

    // Audio-thread state.
    private var sampleRate = 44100
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val acc = FloatArray(N)
    private var filled = 0
    private var accStartSec = 0.0
    private var binLo = IntArray(bands)
    private var binHi = IntArray(bands)

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
        // Log-spaced bands from 40 Hz to 16 kHz, each covering at least one FFT bin.
        val binHz = sampleRate.toDouble() / N
        for (b in 0 until bands) {
            val lo = LOW_HZ * (HIGH_HZ / LOW_HZ).pow(b.toDouble() / bands)
            val hi = LOW_HZ * (HIGH_HZ / LOW_HZ).pow((b + 1).toDouble() / bands)
            binLo[b] = max(1, (lo / binHz).toInt())
            binHi[b] = max(binLo[b] + 1, min(N / 2, (hi / binHz).toInt() + 1))
        }
        reset()
    }

    fun reset() {
        filled = 0
        times.fill(Double.NaN)
        written = 0
    }

    /** Audio thread: [frames] frames of interleaved output starting at track time [startSec]. */
    fun feed(buf: FloatArray, frames: Int, channels: Int, startSec: Double) {
        var f = 0
        while (f < frames) {
            if (filled == 0) accStartSec = startSec + f.toDouble() / sampleRate
            var s = 0f
            for (c in 0 until channels) s += buf[f * channels + c]
            acc[filled++] = s / channels
            if (filled == N) {
                analyse()
                filled = 0
            }
            f++
        }
    }

    private fun analyse() {
        var sum = 0.0
        for (i in 0 until N) {
            re[i] = acc[i] * window[i]
            im[i] = 0f
            sum += acc[i] * acc[i]
        }
        Fft.transform(re, im)
        val slot = written % FRAMES
        for (b in 0 until bands) {
            var peak = 0f
            for (k in binLo[b] until binHi[b]) peak = max(peak, re[k] * re[k] + im[k] * im[k])
            // Magnitude in dB relative to a full-scale sine, mapped from -60..0 dB onto 0..1.
            val db = 10 * log10(max(peak, 1e-12f)) - FULL_SCALE_DB
            levels[slot * bands + b] = ((db + 60f) / 60f).coerceIn(0f, 1f)
        }
        rms[slot] = sqrt(sum / N).toFloat()
        // Stamp the middle of the window: that's the moment the frame describes.
        times[slot] = accStartSec + N / 2.0 / sampleRate
        written++
    }

    /**
     * UI thread: copies the bands for track time [sec] into [out] and returns the level (RMS),
     * or -1 if nothing near that time was captured (paused for long, seeking, or silent deck).
     */
    fun frameAt(sec: Double, out: FloatArray): Float {
        val n = written
        if (n == 0) return -1f
        var best = -1
        var bestGap = MAX_GAP_SEC
        for (back in 0 until min(n, FRAMES)) {
            val slot = (n - 1 - back) % FRAMES
            val gap = kotlin.math.abs(times[slot] - sec)
            if (gap < bestGap) {
                bestGap = gap
                best = slot
            }
        }
        if (best < 0) return -1f
        System.arraycopy(levels, best * bands, out, 0, min(bands, out.size))
        return rms[best]
    }

    companion object {
        const val BANDS = 48
        private const val N = 1024
        /** About 3 s of frames at 44.1 kHz: covers the output latency with room to spare. */
        private const val FRAMES = 128
        private const val LOW_HZ = 40.0
        private const val HIGH_HZ = 16000.0
        private const val MAX_GAP_SEC = 0.25

        /** Power of a full-scale sine's peak bin under a Hann window, in dB. */
        private val FULL_SCALE_DB = (20 * log10(N / 4.0)).toFloat()
    }
}
