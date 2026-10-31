package com.autodj.dj.stems

import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import kotlin.math.sqrt

/**
 * How a deck plays its stems: the mix's own moves, done on the real parts instead of with EQ.
 * The bass swap and bass kill turn the bass stem down (the kick stays, which EQ couldn't do);
 * a vocal cut turns the vocals down; a vocal isolate (or a word loop's [FxType.STEM_ACAPELLA]) turns
 * everything else down, a clean a cappella.
 */
object StemMix {
    /** Gains for drums, bass, other, vocals at track time [t]. */
    fun gains(p: FxPlan, t: Double, out: FloatArray = FloatArray(4)): FloatArray {
        val kill = (p.active(FxType.BASS_KILL, t)?.amount(t) ?: 0f).finite(0f).coerceIn(0f, 1f)
        val bass = (p.bass.at(t).finite(1f) * (1 - kill)).coerceIn(0f, 1f)
        val cut = (p.active(FxType.VOCAL_CUT, t)?.amount(t) ?: 0f).finite(0f).coerceIn(0f, 1f)
        val iso = if (p.active(FxType.STEM_ACAPELLA, t) != null) 1f
            else (p.active(FxType.VOCAL_ISOLATE, t)?.amount(t) ?: 0f).finite(0f).coerceIn(0f, 1f)
        val rest = 1 - iso
        out[0] = rest
        out[1] = bass * rest
        out[2] = rest
        out[3] = 1 - cut
        return out
    }

    private fun Float.finite(fallback: Float) = if (isFinite()) this else fallback

    /** Correlation a lineup must reach to be trusted; below it the window isn't used. */
    const val MIN_CORRELATION = 0.9f

    /**
     * Where [live] (the deck's own audio, mono, starting [liveIndex] frames into the window by
     * the clock) really sits in [guide]: the lag in frames to add, or null if nothing within
     * [maxLag] frames matches well. A coarse search on a decimated copy, then the exact frame.
     */
    fun align(live: FloatArray, guide: FloatArray, liveIndex: Int, maxLag: Int): Int? {
        val d = 8
        val live8 = decimate(live, d)
        val coarseMax = maxLag / d
        var best = Int.MIN_VALUE
        var bestC = -1f
        val e8 = liveIndex / d
        val guide8 = decimate(guide, d)
        for (lag in -coarseMax..coarseMax) {
            val c = correlation(live8, guide8, e8 + lag) ?: continue
            if (c > bestC) { bestC = c; best = lag }
        }
        if (best == Int.MIN_VALUE || bestC < MIN_CORRELATION - 0.1f) return null
        // The exact frame, on the first quarter second at full rate.
        val head = live.copyOf(minOf(live.size, 11025))
        var lag = best * d
        var fineC = -1f
        for (l in best * d - 2 * d..best * d + 2 * d) {
            val c = correlation(head, guide, liveIndex + l) ?: continue
            if (c > fineC) { fineC = c; lag = l }
        }
        return if (fineC >= MIN_CORRELATION) lag else null
    }

    /** Normalised correlation of [a] with [b] from [at]; null when [a] doesn't fit inside [b] there, or either is silent. */
    private fun correlation(a: FloatArray, b: FloatArray, at: Int): Float? {
        if (at < 0 || at + a.size > b.size) return null
        var ab = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[at + i].toDouble()
            ab += x * y
            aa += x * x
            bb += y * y
        }
        if (aa < 1e-9 || bb < 1e-9) return null
        return (ab / sqrt(aa * bb)).toFloat()
    }

    private fun decimate(x: FloatArray, d: Int) = FloatArray(x.size / d) { i ->
        var s = 0f
        for (k in 0 until d) s += x[i * d + k]
        s / d
    }
}
