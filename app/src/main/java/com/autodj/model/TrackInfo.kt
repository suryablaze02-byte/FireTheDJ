package com.autodj.model

import com.autodj.analysis.AudioFeatures
import com.autodj.analysis.BeatAnalyzer
import kotlin.math.floor

/**
 * Everything the DJ needs to know about one track. Bars are counted from [downbeatSec]
 * on a constant-tempo grid, so bar `n` starts at `downbeatSec + n * barSec`.
 */
class TrackInfo(
    val uri: String,
    val title: String,
    val artist: String,
    /** Genre as shown: the tag text when there is one, else the detected family. */
    val genre: String,
    /** The crate this track is mixed in (see [com.autodj.analysis.Genres]). */
    val genreFamily: String,
    val genreConfidence: Float,
    /** "tag", "audio", "tag+audio", "online" or "online+audio". */
    val genreSource: String,
    val durationSec: Double,
    val bpm: Double,
    val downbeatSec: Double,
    /** Bar index (0..7) where 8-bar phrases start. */
    val phraseOffsetBars: Int,
    /** Loudness of each bar, normalised so the loud sections sit near 1.0. */
    val barEnergy: FloatArray,
    /** First bar of the main (loud) section; everything before it is intro. */
    val introEndBar: Int,
    /** First bar after the last loud section; everything from here is outro. */
    val outroStartBar: Int,
    /** Phrase starts where the energy drops (breakdowns): natural places to cut. */
    val dropBars: IntArray,
    val loudnessDb: Double,
    val features: AudioFeatures,
    /** Per bar, 0..1: how vocal the bar sounds. */
    val vocalness: FloatArray,
    /** Tonic pitch class (0 = C) and mode. */
    val keyPc: Int,
    val keyMinor: Boolean,
    val keyConfidence: Float,
    /** Start bar of the hook (most-repeated loud phrase), or -1. */
    val hookBar: Int,
    /** Local tempo every 12 s (0 = unknown); see [com.autodj.analysis.BeatAnalysis.tempoMap]. */
    val tempoMap: FloatArray,
) {
    /** The same track, filed under another genre. */
    fun withGenre(g: com.autodj.analysis.GenreResult) = TrackInfo(
        uri, title, artist, g.label, g.family, g.confidence, g.source, durationSec, bpm, downbeatSec, phraseOffsetBars,
        barEnergy, introEndBar, outroStartBar, dropBars, loudnessDb, features, vocalness, keyPc, keyMinor, keyConfidence,
        hookBar, tempoMap,
    )

    private fun knownTempos(fromSec: Double, toSec: Double): List<Float> {
        val hop = BeatAnalyzer.TEMPO_HOP_SEC
        // A window covers [i·hop, i·hop + 2·hop); take those overlapping the span.
        return tempoMap.indices
            .filter { i -> i * hop < toSec && i * hop + 2 * hop > fromSec && tempoMap[i] > 0f }
            .map { tempoMap[it] }
    }

    /** The tempo heard over [fromSec, toSec): the median local tempo, or the main tempo if unknown. */
    fun tempoIn(fromSec: Double, toSec: Double): Double {
        val t = knownTempos(fromSec, toSec).sorted()
        return if (t.isEmpty()) bpm else t[t.size / 2].toDouble()
    }

    /** True when every measured stretch of [fromSec, toSec) runs at the main tempo (±3%). */
    fun steadyIn(fromSec: Double, toSec: Double) =
        knownTempos(fromSec, toSec).all { kotlin.math.abs(it / bpm - 1) <= 0.03 }

    /** Tempo where the next track would come in: its first minute. */
    val startBpm get() = tempoIn(0.0, 60.0)

    /** Tempo where this track hands over: around its last third. */
    val endBpm get() = tempoIn(durationSec * 0.6, durationSec)

    /** Mean vocalness over [from, to). */
    fun vocalIn(from: Int, to: Int): Double {
        val lo = from.coerceIn(0, barCount)
        val hi = to.coerceIn(lo, barCount)
        return if (hi == lo) 0.0 else (lo until hi).sumOf { vocalness[it].toDouble() } / (hi - lo)
    }

    fun energyIn(from: Int, to: Int): Double {
        val lo = from.coerceIn(0, barCount)
        val hi = to.coerceIn(lo, barCount)
        return if (hi == lo) 0.0 else (lo until hi).sumOf { barEnergy[it].toDouble() } / (hi - lo)
    }

    val beatSec get() = 60.0 / bpm
    val barSec get() = 240.0 / bpm
    val barCount get() = barEnergy.size

    fun barTime(bar: Int) = downbeatSec + bar * barSec
    fun barAt(sec: Double) = floor((sec - downbeatSec) / barSec).toInt()
    fun isPhraseStart(bar: Int) = Math.floorMod(bar - phraseOffsetBars, 8) == 0
}
