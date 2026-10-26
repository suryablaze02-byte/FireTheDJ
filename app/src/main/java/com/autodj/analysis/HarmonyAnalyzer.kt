package com.autodj.analysis

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Harmony(
    /** Tonic pitch class, 0 = C … 11 = B. */
    val keyPc: Int,
    val minor: Boolean,
    /** 0..1: how clearly the key stands out from the next best. */
    val keyConfidence: Float,
    /** Start bar of the most-repeated loud 8-bar phrase (the hook/chorus), or -1. */
    val hookBar: Int,
)

/**
 * Key and repetition structure from a chromagram (energy per pitch class). The key is the
 * best Krumhansl–Schmuckler profile match over the whole track; the hook is the loud phrase
 * whose 8-bar chord/melody pattern recurs most often.
 */
object HarmonyAnalyzer {
    private const val N = 4096
    private const val HOP = 2048

    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    fun analyze(
        samples: FloatArray,
        sampleRate: Double,
        downbeatSec: Double,
        barSec: Double,
        barEnergy: FloatArray,
        phraseOffset: Int,
    ): Harmony {
        val barCount = barEnergy.size
        val binHz = sampleRate / N
        // From C3 up: below that, kick and sub-bass fundamentals follow drum tuning, not the key.
        val kLo = ceil(130 / binHz).toInt()
        val kHi = min(N / 2 - 1, floor(2000 / binHz).toInt())
        val pcOf = IntArray(N / 2) { k ->
            if (k < kLo || k > kHi) -1 else Math.floorMod((12 * log2(k * binHz / 440.0) + 69).roundToInt(), 12)
        }
        val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / N)).toFloat() }
        val re = FloatArray(N)
        val im = FloatArray(N)
        val total = DoubleArray(12)
        val barChroma = Array(barCount) { DoubleArray(12) }
        val frames = (samples.size - N) / HOP + 1
        val frame = DoubleArray(12)
        val logFrame = DoubleArray(12)
        for (f in 0 until max(0, frames)) {
            val off = f * HOP
            for (i in 0 until N) {
                re[i] = samples[off + i] * window[i]
                im[i] = 0f
            }
            Fft.transform(re, im)
            frame.fill(0.0)
            logFrame.fill(0.0)
            for (k in kLo..kHi) {
                val pc = pcOf[k]
                val mag = sqrt((re[k] * re[k] + im[k] * im[k]).toDouble())
                frame[pc] += mag
                logFrame[pc] += ln(1 + 100 * mag)
            }
            // Key: each frame counts equally, so loud drops don't outvote the rest of the song.
            val peak = frame.max()
            if (peak > 1e-9) for (pc in 0 until 12) frame[pc] /= peak
            for (pc in 0 until 12) total[pc] += frame[pc]
            // Hook: log-compressed patterns keep the contrast between chords.
            val t = (off + N / 2) / sampleRate
            val bar = floor((t - downbeatSec) / barSec).toInt()
            if (bar in 0 until barCount) for (pc in 0 until 12) barChroma[bar][pc] += logFrame[pc]
        }

        // Key: correlate with all 24 rotated profiles.
        val scores = HashMap<Pair<Int, Boolean>, Double>()
        for (tonic in 0 until 12) for (minor in listOf(false, true)) {
            val profile = if (minor) MINOR else MAJOR
            scores[tonic to minor] = pearson(total) { pc -> profile[Math.floorMod(pc - tonic, 12)] }
        }
        val (bestKey, best) = scores.maxBy { it.value }.toPair()
        val bestPc = bestKey.first
        val bestMinor = bestKey.second
        // Relative and neighbouring keys always score close and mix fine anyway; what matters
        // is how clearly the key beats the ones that would clash.
        val clashing = scores.filterKeys { (pc, m) -> Camelot.distance(bestPc, bestMinor, pc, m) > 1 }.values.max()
        // Calibrated to real mixes: a gap of ~0.12 over the clashing keys is a clear call.
        val confidence = (((best - clashing) / 0.12).coerceIn(0.0, 1.0) * ((best - 0.3) / 0.4).coerceIn(0.0, 1.0)).toFloat()

        return Harmony(bestPc, bestMinor, confidence, findHook(barChroma, barEnergy, phraseOffset))
    }

    /** The loud phrase whose 8-bar chroma pattern matches the most other phrases. */
    private fun findHook(barChroma: Array<DoubleArray>, barEnergy: FloatArray, phraseOffset: Int): Int {
        val barCount = barEnergy.size
        val starts = (0..barCount - 8).filter { Math.floorMod(it - phraseOffset, 8) == 0 }
        val vectors = starts.associateWith { p ->
            val v = DoubleArray(96)
            for (b in 0 until 8) {
                val c = barChroma[p + b]
                val norm = sqrt(c.sumOf { it * it }).coerceAtLeast(1e-9)
                val mean = c.average() / norm
                for (pc in 0 until 12) v[b * 12 + pc] = c[pc] / norm - mean
            }
            v
        }
        fun loud(p: Int) = (p until p + 8).map { barEnergy[it] }.average() >= 0.8
        var hook = -1
        var bestCount = 0
        for (p in starts) {
            if (!loud(p)) continue
            val count = starts.count { q -> q != p && cosine(vectors.getValue(p), vectors.getValue(q)) >= 0.9 }
            if (count > bestCount) {
                bestCount = count
                hook = p
            }
        }
        return hook
    }

    private fun pearson(x: DoubleArray, y: (Int) -> Double): Double {
        val ys = DoubleArray(12) { y(it) }
        val mx = x.average()
        val my = ys.average()
        var num = 0.0
        var dx = 0.0
        var dy = 0.0
        for (i in 0 until 12) {
            num += (x[i] - mx) * (ys[i] - my)
            dx += (x[i] - mx) * (x[i] - mx)
            dy += (ys[i] - my) * (ys[i] - my)
        }
        return if (dx <= 0 || dy <= 0) 0.0 else num / sqrt(dx * dy)
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na <= 0 || nb <= 0) 0.0 else dot / sqrt(na * nb)
    }
}

/** Camelot wheel notation, the DJ shorthand for harmonic mixing. */
object Camelot {
    private val NAMES = arrayOf("C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")

    /** 1..12 on the wheel. */
    fun number(pc: Int, minor: Boolean): Int {
        val fifths = Math.floorMod(pc * 7, 12)
        return Math.floorMod(fifths + if (minor) 4 else 7, 12) + 1
    }

    fun code(pc: Int, minor: Boolean) = "${number(pc, minor)}${if (minor) "A" else "B"}"

    fun name(pc: Int, minor: Boolean) = NAMES[Math.floorMod(pc, 12)] + if (minor) "m" else ""

    /** 0 = same key; 1 = neighbour on the wheel or relative major/minor (mixes cleanly); 2+ = clash. */
    fun distance(pcA: Int, minorA: Boolean, pcB: Int, minorB: Boolean): Int {
        val d = Math.floorMod(number(pcA, minorA) - number(pcB, minorB), 12)
        val steps = min(d, 12 - d)
        return steps + if (minorA != minorB) 1 else 0
    }
}
