package com.autodj.analysis

import android.content.Context
import com.autodj.model.TrackInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Stores analysis results as JSON so each track is only analysed once. */
class AnalysisCache(context: Context) {
    private val dir = File(context.filesDir, "analysis").apply { mkdirs() }

    fun get(uri: String): TrackInfo? {
        val file = fileFor(uri)
        if (!file.exists()) return null
        return try {
            val o = JSONObject(file.readText())
            if (o.optInt("version") != VERSION) return null
            val energy = o.getJSONArray("barEnergy")
            val drops = o.getJSONArray("dropBars")
            val features = o.getJSONArray("features")
            TrackInfo(
                uri = uri,
                title = o.getString("title"),
                artist = o.getString("artist"),
                genre = o.getString("genre"),
                genreFamily = o.getString("genreFamily"),
                genreConfidence = o.getDouble("genreConfidence").toFloat(),
                genreSource = o.getString("genreSource"),
                durationSec = o.getDouble("durationSec"),
                bpm = o.getDouble("bpm"),
                downbeatSec = o.getDouble("downbeatSec"),
                phraseOffsetBars = o.getInt("phraseOffsetBars"),
                barEnergy = FloatArray(energy.length()) { energy.getDouble(it).toFloat() },
                introEndBar = o.getInt("introEndBar"),
                outroStartBar = o.getInt("outroStartBar"),
                dropBars = IntArray(drops.length()) { drops.getInt(it) },
                loudnessDb = o.getDouble("loudnessDb"),
                features = AudioFeatures.fromArray(DoubleArray(features.length()) { features.getDouble(it) }),
                vocalness = o.getJSONArray("vocalness").let { v -> FloatArray(v.length()) { v.getDouble(it).toFloat() } },
                keyPc = o.getInt("keyPc"),
                keyMinor = o.getBoolean("keyMinor"),
                keyConfidence = o.getDouble("keyConfidence").toFloat(),
                hookBar = o.getInt("hookBar"),
                tempoMap = o.getJSONArray("tempoMap").let { m -> FloatArray(m.length()) { m.getDouble(it).toFloat() } },
            )
        } catch (e: Exception) {
            null
        }
    }

    fun put(t: TrackInfo) {
        val o = JSONObject()
            .put("version", VERSION)
            .put("title", t.title)
            .put("artist", t.artist)
            .put("genre", t.genre)
            .put("genreFamily", t.genreFamily)
            .put("genreConfidence", t.genreConfidence.toDouble())
            .put("genreSource", t.genreSource)
            .put("durationSec", t.durationSec)
            .put("bpm", t.bpm)
            .put("downbeatSec", t.downbeatSec)
            .put("phraseOffsetBars", t.phraseOffsetBars)
            .put("barEnergy", JSONArray().apply { t.barEnergy.forEach { put(it.toDouble()) } })
            .put("introEndBar", t.introEndBar)
            .put("outroStartBar", t.outroStartBar)
            .put("dropBars", JSONArray().apply { t.dropBars.forEach { put(it) } })
            .put("loudnessDb", t.loudnessDb)
            .put("features", JSONArray().apply { t.features.toArray().forEach { put(it) } })
            .put("vocalness", JSONArray().apply { t.vocalness.forEach { put(it.toDouble()) } })
            .put("keyPc", t.keyPc)
            .put("keyMinor", t.keyMinor)
            .put("keyConfidence", t.keyConfidence.toDouble())
            .put("hookBar", t.hookBar)
            .put("tempoMap", JSONArray().apply { t.tempoMap.forEach { put(it.toDouble()) } })
        fileFor(t.uri).writeText(o.toString())
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(uri: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private companion object {
        // 2: genre family/confidence and audio features. 3: generic tags defer to audio; tempo octave check.
        // 4: key, per-bar vocalness, hook. 5: key confidence against clashing keys only. 6: chroma from 130 Hz, per-frame normalised. 7: hook uses log chroma. 8: local tempo map. 9: main tempo = dominant local tempo.
        const val VERSION = 9
    }
}
