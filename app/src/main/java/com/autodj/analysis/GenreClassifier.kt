package com.autodj.analysis

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class GenreResult(
    /** What the UI shows: the tag text when there is one ("Tech House"), else the family. */
    val label: String,
    /** The crate this track belongs to. */
    val family: String,
    val confidence: Float,
    /** "tag", "audio" or "tag+audio". */
    val source: String,
)

/**
 * Classifies a track into a genre family from its tempo and rhythm/timbre features, then
 * fuses that with the file's genre tag. Each family is a fuzzy profile (tempo range, kick
 * pattern, hats, brightness, density); scores are normalised into a confidence with some
 * mass left for "none of these" so odd tracks land in Unclassified instead of a wrong crate.
 */
object GenreClassifier {
    /** Below this, an audio-only guess is not trusted enough to mix with. */
    const val MIN_CONFIDENCE = 0.45

    fun classify(f: AudioFeatures, bpm: Double, tag: String, tagSource: String = "tag"): GenreResult {
        val (audioFamily, audioConf) = classifyAudio(f, bpm)
        val tagFamily = Genres.family(tag)
        return when {
            tagFamily != null && tagFamily == audioFamily ->
                GenreResult(tag, tagFamily, max(0.9, audioConf).toFloat(), "$tagSource+audio")
            // "Dance", "Electronic", "Pop"…: let the audio name the style; keep the tag only as a fallback.
            tagFamily != null && Genres.isGeneric(tag) ->
                if (audioConf >= MIN_CONFIDENCE) GenreResult(audioFamily, audioFamily, audioConf.toFloat(), "audio")
                else GenreResult(tag, tagFamily, 0.6f, tagSource)
            // Tags are usually right when present; the audio model is coarse.
            tagFamily != null -> GenreResult(tag, tagFamily, 0.8f, tagSource)
            audioConf >= MIN_CONFIDENCE -> GenreResult(audioFamily, audioFamily, audioConf.toFloat(), "audio")
            else -> GenreResult(Genres.UNCLASSIFIED, Genres.UNCLASSIFIED, audioConf.toFloat(), "audio")
        }
    }

    /** Best family and its confidence (0..1). */
    fun classifyAudio(f: AudioFeatures, bpm: Double): Pair<String, Double> {
        val four = f.fourOnFloor
        val scores = mapOf(
            Genres.HOUSE to trap(bpm, 115.0, 119.0, 128.0, 131.0) * up(four, 0.6, 0.85) *
                (0.6 + 0.4 * up(f.hatOffbeat, 0.9, 1.4)),
            Genres.TECHNO to trap(bpm, 124.0, 128.0, 145.0, 152.0) * up(four, 0.6, 0.85) *
                (0.6 + 0.4 * down(f.centroidHz, 1500.0, 2600.0)),
            Genres.TRANCE to trap(bpm, 128.0, 132.0, 145.0, 150.0) * up(four, 0.6, 0.85) *
                (0.5 + 0.5 * up(f.centroidHz, 1800.0, 2800.0)) * (0.6 + 0.4 * up(f.dynamics, 0.15, 0.3)),
            Genres.DNB to max(trap(bpm, 160.0, 165.0, 180.0, 185.0), trap(bpm, 84.0, 86.0, 89.0, 92.0)) *
                down(four, 0.5, 0.8) * (0.3 + 0.7 * up(f.onsetRate, 3.0, 6.0)),
            Genres.BASS to max(trap(bpm, 135.0, 138.0, 152.0, 155.0), trap(bpm, 68.0, 70.0, 76.0, 78.0)) *
                down(four, 0.4, 0.7) * (0.5 + 0.5 * up(f.lowRatio, 0.35, 0.55)),
            Genres.HIPHOP to max(trap(bpm, 72.0, 78.0, 102.0, 108.0), trap(bpm, 145.0, 155.0, 200.0, 210.0)) *
                down(four, 0.55, 0.85) * (0.5 + 0.5 * up(f.kickSyncopation, 0.3, 0.7)) *
                (0.6 + 0.4 * down(f.centroidHz, 2200.0, 3200.0)) * down(f.onsetRate, 4.0, 7.0),
            Genres.POP to trap(bpm, 90.0, 98.0, 130.0, 135.0) * (0.4 + 0.6 * bell(four, 0.5, 0.35)) *
                (0.5 + 0.5 * up(f.centroidHz, 1800.0, 2800.0)),
            Genres.CHILL to down(f.onsetRate, 1.5, 3.5) * down(four, 0.4, 0.8),
        )
        val total = scores.values.sum() + UNKNOWN_MASS
        val best = scores.maxBy { it.value }
        return best.key to best.value / total
    }

    private const val UNKNOWN_MASS = 0.15

    /** Trapezoid membership: 0 below a, ramps to 1 by b, flat to c, 0 again by d. */
    private fun trap(x: Double, a: Double, b: Double, c: Double, d: Double): Double = when {
        x <= a || x >= d -> 0.0
        x < b -> (x - a) / (b - a)
        x <= c -> 1.0
        else -> (d - x) / (d - c)
    }

    private fun up(x: Double, lo: Double, hi: Double) = min(1.0, max(0.0, (x - lo) / (hi - lo)))
    private fun down(x: Double, lo: Double, hi: Double) = 1 - up(x, lo, hi)
    private fun bell(x: Double, centre: Double, width: Double) = exp(-((x - centre) / width).let { it * it })
}
