package com.autodj.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class BeatAnalysis(
    val bpm: Double,
    val downbeatSec: Double,
    val phraseOffsetBars: Int,
    val barEnergy: FloatArray,
    val introEndBar: Int,
    val outroStartBar: Int,
    val dropBars: IntArray,
    val loudnessDb: Double,
    val features: AudioFeatures,
    /** Per bar, 0..1: how much this bar sounds like singing, relative to the rest of the track. */
    val vocalness: FloatArray,
    val harmony: Harmony,
    /**
     * Local tempo every [BeatAnalyzer.TEMPO_HOP_SEC] seconds (folded to the main tempo's octave),
     * or 0 where there is too little rhythm to tell (beatless intros, breakdowns).
     */
    val tempoMap: FloatArray,
)

/** Rhythm and timbre descriptors used to tell genres apart. */
class AudioFeatures(
    /** Share of spectral energy below ~200 Hz. */
    val lowRatio: Double,
    /** Energy-weighted spectral centroid (brightness), Hz. */
    val centroidHz: Double,
    /** Fraction of beats in loud sections that carry a kick: ~1 for four-on-the-floor. */
    val fourOnFloor: Double,
    /** Kick strength on the off-beats relative to the beats (breakbeats, hip-hop). */
    val kickSyncopation: Double,
    /** Hi-hat strength on the off-beats relative to the beats (house "tss"). */
    val hatOffbeat: Double,
    /** Onsets per second. */
    val onsetRate: Double,
    /** Spread of bar loudness: big breakdowns and drops score high. */
    val dynamics: Double,
) {
    /** Roughly unit-scaled vector, for similarity between tracks. */
    fun vector() = doubleArrayOf(
        lowRatio * 2, centroidHz / 2500, fourOnFloor, kickSyncopation, hatOffbeat / 2, onsetRate / 6, dynamics * 3,
    )

    fun toArray() = doubleArrayOf(lowRatio, centroidHz, fourOnFloor, kickSyncopation, hatOffbeat, onsetRate, dynamics)

    companion object {
        fun fromArray(a: DoubleArray) = AudioFeatures(a[0], a[1], a[2], a[3], a[4], a[5], a[6])
    }
}

/**
 * Pure-Kotlin analysis of mono PCM: tempo, beat grid, downbeat, phrase grid and the
 * energy structure (intro / main / breakdowns / outro). No Android dependencies, so it
 * runs in plain JVM unit tests.
 */
object BeatAnalyzer {
    private const val WIN = 512
    private const val HOP = 128
    private const val MIN_BPM = 70.0
    private const val MAX_BPM = 180.0
    private const val LOUD = 0.7f

    /**
     * How much singing there is through the track, [VOCAL_FPS] values a second: the vocal
     * band's share of the sound times how harmonic it is, as for [BeatAnalysis.vocalness]
     * but fine enough to line lyrics up to a fraction of a second.
     */
    fun vocalCurve(samples: FloatArray, sampleRate: Double): FloatArray {
        val vocal = spectralFrames(samples, sampleRate).vocal
        val fps = sampleRate / HOP
        val frameCentre = WIN / 2 / sampleRate
        val n = (samples.size / sampleRate * VOCAL_FPS).toInt()
        return FloatArray(n) {
            meanRange(vocal, (it / VOCAL_FPS - frameCentre) * fps, ((it + 1) / VOCAL_FPS - frameCentre) * fps).toFloat()
        }
    }

    const val VOCAL_FPS = 10.0

    fun analyze(samples: FloatArray, sampleRate: Double): BeatAnalysis {
        require(samples.size >= sampleRate * 20) { "Track is too short to analyse" }
        val fps = sampleRate / HOP
        val frames = spectralFrames(samples, sampleRate)
        val env = onsetEnvelope(frames.flux, fps.roundToInt())
        val lowEnv = onsetEnvelope(frames.lowFlux, fps.roundToInt())
        val highEnv = onsetEnvelope(frames.highFlux, fps.roundToInt())

        val estimated = correctOctave(estimateTempo(env, fps), env, lowEnv, fps)
        // The main tempo (and so the bar grid) is the one the song spends most of its time at.
        val bpm = dominantTempo(estimated, tempoMap(env, fps, estimated), env, fps)
        val localTempos = tempoMap(env, fps, bpm)
        val beatSec = 60.0 / bpm
        val barSec = 4 * beatSec
        val frameCentre = WIN / 2 / sampleRate
        // Phase comes from the kick band: off-beat hi-hats are louder in full-band flux.
        val envSum = env.sum().coerceAtLeast(1e-9)
        val lowSum = lowEnv.sum().coerceAtLeast(1e-9)
        val phaseEnv = DoubleArray(env.size) { 0.25 * env[it] / envSum + lowEnv[it] / lowSum }
        val firstBeatSec = foldPeak(phaseEnv, 60.0 * fps / bpm).second / fps + frameCentre
        val durationSec = samples.size / sampleRate
        fun rmsBetween(from: Double, to: Double) =
            meanRange(frames.rms, (from - frameCentre) * fps, (to - frameCentre) * fps)

        // Downbeat: structural changes (sections starting/stopping) land on bar starts.
        val beatCount = ((durationSec - firstBeatSec) / beatSec).toInt()
        val beatRms = DoubleArray(beatCount) {
            rmsBetween(firstBeatSec + it * beatSec, firstBeatSec + (it + 1) * beatSec)
        }
        var downbeatSec = firstBeatSec + bestOffset(novelty(beatRms, 4), 4) * beatSec
        while (downbeatSec >= barSec) downbeatSec -= barSec

        val barCount = ((durationSec - downbeatSec) / barSec).toInt()
        val rawBar = DoubleArray(barCount) {
            rmsBetween(downbeatSec + it * barSec, downbeatSec + (it + 1) * barSec)
        }
        val ref = percentile(rawBar, 0.95).coerceAtLeast(1e-6)
        val barEnergy = FloatArray(barCount) { (rawBar[it] / ref).coerceAtMost(1.0).toFloat() }

        // Phrases are 8 bars; they start where the arrangement changes.
        val phraseOffset = bestOffset(novelty(DoubleArray(barCount) { barEnergy[it].toDouble() }, 4), 8)
        fun isPhrase(bar: Int) = Math.floorMod(bar - phraseOffset, 8) == 0
        fun snapToPhrase(bar: Int): Int {
            for (d in 0..2) {
                if (isPhrase(bar - d)) return bar - d
                if (isPhrase(bar + d)) return bar + d
            }
            return bar
        }

        val loudBar = { i: Int -> i in 0 until barCount && barEnergy[i] >= LOUD }
        val firstLoud = (0 until barCount).firstOrNull { loudBar(it) && loudBar(it + 1) } ?: 0
        val lastLoud = (barCount - 1 downTo 0).firstOrNull { loudBar(it) && loudBar(it - 1) }
        val introEnd = snapToPhrase(firstLoud).coerceIn(0, barCount)
        var outroStart = if (lastLoud == null) barCount else snapToPhrase(lastLoud + 1).coerceIn(0, barCount)
        if (outroStart <= introEnd) outroStart = barCount

        val drops = (introEnd + 8..outroStart - 8).filter { j ->
            isPhrase(j) && meanOf(barEnergy, j, j + 8) < 0.75 * meanOf(barEnergy, j - 8, j)
        }

        val loudBars = rawBar.filterIndexed { i, _ -> barEnergy[i] >= LOUD }
        val loudness = 20 * log10((if (loudBars.isEmpty()) rawBar.average() else loudBars.average()).coerceAtLeast(1e-6))

        // Rhythm features are measured on the loud sections, where the full groove plays.
        val beatTimes = (0 until ((durationSec - downbeatSec) / beatSec).toInt())
            .map { downbeatSec + it * beatSec }
            .filter { t -> loudBar(((t - downbeatSec) / barSec).toInt()) }
            .ifEmpty { (0 until beatCount).map { firstBeatSec + it * beatSec } }
        fun peakNear(e: DoubleArray, sec: Double): Double {
            val c = ((sec - frameCentre) * fps).roundToInt()
            var m = 0.0
            for (i in max(0, c - 2)..min(e.size - 1, c + 2)) m = max(m, e[i])
            return m
        }
        val kickOn = beatTimes.map { peakNear(lowEnv, it) }
        val kickOff = beatTimes.map { peakNear(lowEnv, it + beatSec / 2) }
        val hatOn = beatTimes.map { peakNear(highEnv, it) }
        val hatOff = beatTimes.map { peakNear(highEnv, it + beatSec / 2) }
        val kickThreshold = 0.25 * percentile(kickOn.toDoubleArray(), 0.9)
        val envMean = env.average()
        val envStd = sqrt(env.sumOf { (it - envMean) * (it - envMean) } / env.size)
        val onsetCount = (1 until env.size - 1).count { i ->
            env[i] > envMean + envStd && env[i] >= env[i - 1] && env[i] > env[i + 1]
        }
        val energyMean = barEnergy.average()
        val features = AudioFeatures(
            lowRatio = frames.lowEnergy / frames.totalEnergy.coerceAtLeast(1e-12),
            centroidHz = frames.centroidHz,
            fourOnFloor = kickOn.count { it > kickThreshold }.toDouble() / kickOn.size,
            kickSyncopation = kickOff.average() / kickOn.average().coerceAtLeast(1e-9),
            hatOffbeat = hatOff.average() / hatOn.average().coerceAtLeast(1e-9),
            onsetRate = onsetCount / durationSec,
            dynamics = sqrt(barEnergy.sumOf { (it - energyMean) * (it - energyMean) } / barCount.coerceAtLeast(1)),
        )

        val rawVocal = DoubleArray(barCount) {
            meanRange(frames.vocal, (downbeatSec + it * barSec - frameCentre) * fps, (downbeatSec + (it + 1) * barSec - frameCentre) * fps)
        }
        val vLo = percentile(rawVocal, 0.1)
        val vHi = percentile(rawVocal, 0.9)
        val vocalness = FloatArray(barCount) {
            if (vHi - vLo < 1e-9) 0f else ((rawVocal[it] - vLo) / (vHi - vLo)).coerceIn(0.0, 1.0).toFloat()
        }

        return BeatAnalysis(
            features = features,
            vocalness = vocalness,
            tempoMap = localTempos,
            harmony = HarmonyAnalyzer.analyze(samples, sampleRate, downbeatSec, barSec, barEnergy, phraseOffset),
            bpm = bpm,
            downbeatSec = downbeatSec,
            phraseOffsetBars = phraseOffset,
            barEnergy = barEnergy,
            introEndBar = introEnd,
            outroStartBar = outroStart,
            dropBars = drops.toIntArray(),
            loudnessDb = loudness,
        )
    }

    private class Frames(
        val flux: FloatArray,
        val lowFlux: FloatArray,
        val highFlux: FloatArray,
        val rms: FloatArray,
        /** Per frame: vocal-band share × how peaky (harmonic) that band is. */
        val vocal: FloatArray,
        val lowEnergy: Double,
        val totalEnergy: Double,
        val centroidHz: Double,
    )

    /** Bins below ~200 Hz, where kick drums live. */
    private const val LOW_BINS = 10

    /** Hi-hats and cymbals live above this. */
    private const val HIGH_HZ = 3000.0

    /** Per-frame spectral flux (full, kick band and hat band), RMS, and whole-track timbre. */
    private fun spectralFrames(samples: FloatArray, sampleRate: Double): Frames {
        val binHz = sampleRate / WIN
        val highBin = (HIGH_HZ / binHz).toInt()
        val vocalLo = (250 / binHz).toInt()
        val vocalHi = (3500 / binHz).toInt()
        var lowEnergy = 0.0
        var totalEnergy = 0.0
        var centroidSum = 0.0
        var centroidWeight = 0.0
        val n = (samples.size - WIN) / HOP + 1
        val window = FloatArray(WIN) { (0.5 - 0.5 * cos(2 * PI * it / WIN)).toFloat() }
        val re = FloatArray(WIN)
        val im = FloatArray(WIN)
        val half = WIN / 2
        var prev = FloatArray(half)
        var cur = FloatArray(half)
        val flux = FloatArray(n)
        val lowFlux = FloatArray(n)
        val highFlux = FloatArray(n)
        val rms = FloatArray(n)
        val vocal = FloatArray(n)
        for (f in 0 until n) {
            val off = f * HOP
            var e = 0.0
            for (i in 0 until WIN) {
                val s = samples[off + i]
                e += s * s
                re[i] = s * window[i]
                im[i] = 0f
            }
            rms[f] = sqrt(e / WIN).toFloat()
            Fft.transform(re, im)
            var sum = 0f
            var low = 0f
            var high = 0f
            var frameEnergy = 0.0
            var frameCentroid = 0.0
            var bandPower = 0.0
            var bandMax = 0.0
            for (k in 1 until half) {
                val power = re[k] * re[k] + im[k] * im[k]
                val mag = sqrt(power)
                frameEnergy += power
                if (k in vocalLo..vocalHi) {
                    bandPower += power
                    if (power > bandMax) bandMax = power.toDouble()
                }
                frameCentroid += k * binHz * mag
                if (k <= LOW_BINS) lowEnergy += power
                val m = ln(1f + 1000f * mag)
                cur[k] = m
                val d = m - prev[k]
                if (d > 0) {
                    sum += d
                    if (k <= LOW_BINS) low += d
                    if (k >= highBin) high += d
                }
            }
            totalEnergy += frameEnergy
            if (frameEnergy > 1e-12 && bandPower > 1e-12) {
                val peakiness = bandMax / (bandPower / (vocalHi - vocalLo + 1))
                vocal[f] = (bandPower / frameEnergy * min(1.0, log10(peakiness) / 1.5)).toFloat().coerceAtLeast(0f)
            }
            var magSum = 0.0
            for (k in 1 until half) magSum += sqrt(re[k] * re[k] + im[k] * im[k])
            if (magSum > 1e-9) {
                centroidSum += frameCentroid / magSum * rms[f]
                centroidWeight += rms[f]
            }
            flux[f] = if (f == 0) 0f else sum
            lowFlux[f] = if (f == 0) 0f else low
            highFlux[f] = if (f == 0) 0f else high
            val t = prev; prev = cur; cur = t
        }
        return Frames(
            flux, lowFlux, highFlux, rms, vocal,
            lowEnergy = lowEnergy,
            totalEnergy = totalEnergy,
            centroidHz = if (centroidWeight > 0) centroidSum / centroidWeight else 0.0,
        )
    }

    /** Flux minus its local average, half-wave rectified. */
    private fun onsetEnvelope(flux: FloatArray, width: Int): DoubleArray {
        val prefix = DoubleArray(flux.size + 1)
        for (i in flux.indices) prefix[i + 1] = prefix[i] + flux[i]
        return DoubleArray(flux.size) { i ->
            val lo = max(0, i - width / 2)
            val hi = min(flux.size, i + width / 2 + 1)
            max(0.0, flux[i] - (prefix[hi] - prefix[lo]) / (hi - lo))
        }
    }

    /** Coarse tempo by weighted autocorrelation, then a fine comb search to 0.01 BPM. */
    private fun estimateTempo(env: DoubleArray, fps: Double): Double {
        val minLag = floor(60 * fps / MAX_BPM).toInt()
        val maxLag = ceil(60 * fps / MIN_BPM).toInt()
        val ac = DoubleArray(2 * maxLag + 2)
        for (lag in max(1, minLag - 1) until min(ac.size, env.size)) {
            var s = 0.0
            for (i in 0 until env.size - lag) s += env[i] * env[i + lag]
            ac[lag] = s / (env.size - lag)
        }
        var bestLag = minLag
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = 60 * fps / lag
            val weight = exp(-0.5 * (log2(bpm / 120.0) / 0.8).let { it * it })
            val score = weight * (ac[lag] + 0.5 * ac[2 * lag])
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        val y0 = ac[bestLag - 1]
        val y1 = ac[bestLag]
        val y2 = ac[bestLag + 1]
        val denom = y0 - 2 * y1 + y2
        val delta = if (denom != 0.0) (0.5 * (y0 - y2) / denom).coerceIn(-0.5, 0.5) else 0.0
        return refineTempo(env, 60 * fps / (bestLag + delta), fps)
    }

    const val TEMPO_HOP_SEC = 12.0
    private const val TEMPO_WINDOW_SEC = 24.0

    /**
     * The tempo in overlapping 24 s windows. Songs that change tempo (a half-time intro, a
     * switch into a faster dance section) show up as windows away from the main tempo.
     */
    private fun tempoMap(env: DoubleArray, fps: Double, bpm: Double): FloatArray {
        val hop = (TEMPO_HOP_SEC * fps).toInt()
        val win = (TEMPO_WINDOW_SEC * fps).toInt()
        val count = max(1, (env.size - win) / hop + 1)
        val overall = env.average()
        return FloatArray(count) { w ->
            val part = env.copyOfRange(w * hop, min(env.size, w * hop + win))
            if (part.size < win / 2 || part.average() < 0.3 * overall) {
                0f // too little rhythm to call
            } else {
                val local = estimateTempo(part, fps)
                // An octave away from the main tempo is the same tempo; anything else is kept as heard.
                (listOf(local, local * 2, local / 2).firstOrNull { abs(it / bpm - 1) <= 0.08 } ?: local).toFloat()
            }
        }
    }

    /**
     * If one local tempo clearly dominates (≥60% of measured windows) and it isn't [estimated],
     * that is the song's real main tempo: e.g. a song at 94 for two minutes that switches to 128
     * at the end, which a whole-track estimate leaning toward ~120 would call 128.
     */
    private fun dominantTempo(estimated: Double, map: FloatArray, env: DoubleArray, fps: Double): Double {
        val known = map.filter { it > 0f }
        if (known.size < 4) return estimated
        // The measured tempo with the most windows within ±3% of it.
        val members = known.map { t -> known.filter { abs(it / t - 1) <= 0.03 } }.maxBy { it.size }
        val centre = members.sorted()[members.size / 2].toDouble()
        val share = members.size.toDouble() / known.size
        if (share < 0.6 || abs(centre / estimated - 1) <= 0.04) return estimated
        return refineTempo(env, centre, fps)
    }

    /** Comb search ±3% around [coarse] in 0.01 BPM steps. */
    private fun refineTempo(env: DoubleArray, coarse: Double, fps: Double): Double {
        var best = coarse
        var bestFold = Double.NEGATIVE_INFINITY
        var bpm = coarse * 0.97
        while (bpm <= coarse * 1.03) {
            val score = foldPeak(env, 60 * fps / bpm).first
            if (score > bestFold) {
                bestFold = score
                best = bpm
            }
            bpm += 0.01
        }
        return (best * 100).roundToInt() / 100.0
    }

    /**
     * Tempo estimates can land an octave low (a 150 BPM track read as 75). The giveaway is in
     * the kick band: a real 150 has a kick in nearly every slot of the 75 grid's beats *and*
     * off-beats. Hip-hop (a kick on the "and" of 3 now and then) and half-time grooves have kicks
     * in only a few off-beat slots, so they stay put.
     */
    internal fun correctOctave(bpm: Double, env: DoubleArray, lowEnv: DoubleArray, fps: Double): Double {
        if (bpm >= DOUBLE_BELOW_BPM || bpm * 2 > MAX_DOUBLED_BPM) return bpm
        val period = 60 * fps / bpm
        val phase = foldPeak(lowEnv, period).second
        val slots = ((lowEnv.size - phase) / (period / 2)).toInt()
        if (slots < 16) return bpm
        val strength = DoubleArray(slots) { k ->
            val c = (phase + k * period / 2).roundToInt()
            var m = 0.0
            for (i in max(0, c - 2)..min(lowEnv.size - 1, c + 2)) m = max(m, lowEnv[i])
            m
        }
        val threshold = 0.3 * percentile(strength, 0.9)
        if (threshold <= 0) return bpm
        val onBeats = strength.filterIndexed { k, _ -> k % 2 == 0 }
        val offBeats = strength.filterIndexed { k, _ -> k % 2 == 1 }
        val onCover = onBeats.count { it > threshold }.toDouble() / onBeats.size
        val offCover = offBeats.count { it > threshold }.toDouble() / offBeats.size
        return if (onCover >= KICK_COVER && offCover >= KICK_COVER) refineTempo(env, bpm * 2, fps) else bpm
    }

    private const val DOUBLE_BELOW_BPM = 100.0
    private const val MAX_DOUBLED_BPM = 200.0

    /** Share of slots that must carry a kick; allows for breakdowns without drums. */
    private const val KICK_COVER = 0.6

    /**
     * Folds the onset envelope modulo [period] frames. A correct period stacks every beat
     * into the same bin, so the peak height scores the period and its position is the phase.
     */
    private fun foldPeak(env: DoubleArray, period: Double): Pair<Double, Double> {
        val bins = ceil(period).toInt()
        val hist = DoubleArray(bins)
        for (i in env.indices) {
            val pos = i - floor(i / period) * period
            val b0 = floor(pos).toInt()
            val frac = pos - b0
            hist[b0 % bins] += env[i] * (1 - frac)
            hist[(b0 + 1) % bins] += env[i] * frac
        }
        val smooth = DoubleArray(bins) {
            0.25 * hist[(it - 1 + bins) % bins] + 0.5 * hist[it] + 0.25 * hist[(it + 1) % bins]
        }
        var peak = 0
        for (i in 1 until bins) if (smooth[i] > smooth[peak]) peak = i
        val y0 = smooth[(peak - 1 + bins) % bins]
        val y1 = smooth[peak]
        val y2 = smooth[(peak + 1) % bins]
        val denom = y0 - 2 * y1 + y2
        val delta = if (denom != 0.0) (0.5 * (y0 - y2) / denom).coerceIn(-0.5, 0.5) else 0.0
        val phase = (peak + delta + period) % period
        return smooth[peak] to phase
    }

    /** |mean of next w values - mean of previous w values| at each index. */
    private fun novelty(x: DoubleArray, w: Int): DoubleArray = DoubleArray(x.size) { i ->
        if (i < w || i + w > x.size) 0.0
        else abs((i until i + w).sumOf { x[it] } - (i - w until i).sumOf { x[it] }) / w
    }

    /**
     * The offset (mod [period]) where novelty peaks fall. Only local maxima count, so the
     * many small wiggles inside a section can't outvote a few clear section changes.
     */
    private fun bestOffset(scores: DoubleArray, period: Int): Int {
        val totals = DoubleArray(period)
        for (i in scores.indices) {
            val left = if (i > 0) scores[i - 1] else 0.0
            val right = if (i + 1 < scores.size) scores[i + 1] else 0.0
            if (scores[i] > left && scores[i] >= right) totals[i % period] += scores[i] * scores[i]
        }
        return totals.indices.maxBy { totals[it] }
    }

    private fun meanRange(values: FloatArray, from: Double, to: Double): Double {
        val lo = from.roundToInt().coerceIn(0, values.size)
        val hi = to.roundToInt().coerceIn(lo, values.size)
        if (hi == lo) return 0.0
        var s = 0.0
        for (i in lo until hi) s += values[i]
        return s / (hi - lo)
    }

    private fun meanOf(values: FloatArray, from: Int, to: Int): Double {
        val lo = from.coerceIn(0, values.size)
        val hi = to.coerceIn(lo, values.size)
        return if (hi == lo) 0.0 else (lo until hi).sumOf { values[it].toDouble() } / (hi - lo)
    }

    private fun percentile(values: DoubleArray, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).roundToInt()]
    }
}
