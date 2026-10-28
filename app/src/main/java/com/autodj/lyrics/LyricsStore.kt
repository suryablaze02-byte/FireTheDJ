package com.autodj.lyrics

import android.content.Context
import android.icu.text.Transliterator
import android.net.Uri
import android.os.Build
import android.util.Log
import com.autodj.analysis.BeatAnalyzer
import com.autodj.analysis.PcmDecoder
import com.autodj.model.TrackInfo
import com.autodj.remote.StreamStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Each track's timed words, fetched once from [LyricsSources] after the track is analysed,
 * lined up with the audio ([LyricsAligner]) and kept on the phone. A track with no usable
 * lyrics is remembered too, and asked about again after a week.
 *
 * The lyrics as the catalogue timed them are kept beside the aligned ones, so a better
 * aligner can line them up again without asking the catalogue twice.
 */
object LyricsStore {
    private const val RETRY_MS = 7L * 24 * 60 * 60 * 1000

    /** Bumped when lookup or alignment improves, so earlier results are redone. */
    private const val MATCHER_VERSION = 7

    /** The last version whose lookup (not alignment) is still good: its lyrics are lined up again rather than fetched again. */
    private const val LOOKUP_VERSION = 6

    private var dir: File? = null
    private var app: Context? = null
    private val memory = ConcurrentHashMap<String, TimedLyrics>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        if (dir == null) dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        if (app == null) app = context.applicationContext
    }

    private fun file(uri: String) = dir?.let { File(it, "${uri.hashCode().toUInt()}_${uri.length}.json") }

    /** The track's lyrics on its own audio, or null when there are none (or not fetched yet). */
    fun get(t: TrackInfo): TimedLyrics? = get(t.uri)

    fun get(uri: String): TimedLyrics? {
        memory[uri]?.let { return it }
        if (uri in missing) return null
        val f = file(uri) ?: return null
        if (!f.exists()) return null
        val loaded = runCatching { read(JSONObject(f.readText())) }.getOrNull()
        if (loaded != null) memory[uri] = loaded else missing += uri
        return loaded
    }

    /** Fetches and stores the track's lyrics unless already known. Blocking (network, decoding): call off the main thread. */
    fun fetch(t: TrackInfo) {
        val f = file(t.uri) ?: return
        val old = if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrNull() else null
        val v = old?.optInt("v") ?: 0
        // Known lyrics stay; a miss is retried after a week, when the catalogue may have the song.
        if (old != null && v == MATCHER_VERSION && (!old.optBoolean("none") || System.currentTimeMillis() - f.lastModified() < RETRY_MS)) return
        // Lyrics found by a lookup that is still good are lined up again from their original times.
        val kept = old?.takeIf { v >= LOOKUP_VERSION && !it.optBoolean("none") }?.let(::original)
        val found = kept ?: LyricsSources.find(t.title, t.artist, t.durationSec)?.let {
            Original(Lrc.parse(it.lrc, ::toLatin), it.source, it.durationSec)
        }
        val vocal = found?.let { runCatching { vocalCurve(t) }.onFailure { e -> Log.w("AutoDJ", "singing curve for \"${t.title}\": ${e.message}") }.getOrNull() }
        val aligned = found?.let {
            LyricsAligner.align(it.words, t, it.source, it.durationSec?.let { len -> t.durationSec - len }, vocal, BeatAnalyzer.VOCAL_FPS)
        }
        if (aligned != null) {
            memory[t.uri] = aligned
            missing -= t.uri
            f.writeText(write(aligned, found.durationSec).toString())
            Log.i(
                "AutoDJ",
                "lyrics for \"${t.title}\": ${aligned.words.size} words from ${aligned.source}${if (kept != null) " (kept)" else ""}, " +
                    "shifted ${"%+.1f".format(aligned.offsetSec)} s by ${if (vocal != null) "the singing" else "bars"}",
            )
        } else {
            memory -= t.uri
            missing += t.uri
            f.writeText(JSONObject().put("none", true).put("v", MATCHER_VERSION).toString())
            Log.i("AutoDJ", "lyrics for \"${t.title}\": " + if (found == null) "none found" else "found, but they don't line up with the audio")
        }
    }

    /** How much singing there is through the track, decoded from its own audio (a stored copy of a stream when there is one). */
    private fun vocalCurve(t: TrackInfo): FloatArray? {
        val ctx = app ?: return null
        val uri = StreamStore.get(ctx).fileFor(t.uri)?.let { Uri.fromFile(it) } ?: Uri.parse(t.uri)
        val pcm = PcmDecoder.decodeMono(ctx, uri)
        return BeatAnalyzer.vocalCurve(pcm.samples, pcm.sampleRate)
    }

    private class Original(val words: List<LyricWord>, val source: String, val durationSec: Double?)

    /** The words as the catalogue timed them, from a stored result (its shift taken back off). */
    private fun original(o: JSONObject): Original? {
        val a = o.optJSONArray("words") ?: return null
        val off = o.optDouble("offset", 0.0)
        val words = (0 until a.length()).map {
            val w = a.getJSONArray(it)
            LyricWord(w.getString(0), w.getString(1), w.getDouble(2) - off, w.getDouble(3) - off, w.getBoolean(4), w.getBoolean(5), w.getInt(6))
        }
        return Original(words, o.optString("source", "LRCLIB"), o.optDouble("dur").takeIf { !it.isNaN() })
    }

    private val latin: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= 29) runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull() else null
    }

    /** Telugu, Tamil, Devanagari… written in Latin letters, so words match whatever script the lyrics used. */
    private fun toLatin(word: String): String =
        if (word.all { it.code < 128 }) word else latin?.transliterate(word) ?: word

    private fun write(l: TimedLyrics, durationSec: Double?) = JSONObject()
        .put("v", MATCHER_VERSION)
        .put("source", l.source)
        .put("offset", l.offsetSec)
        .apply { if (durationSec != null) put("dur", durationSec) }
        .put("words", JSONArray().apply {
            l.words.forEach {
                put(JSONArray().put(it.key).put(it.text).put(it.startSec).put(it.endSec).put(it.lineStart).put(it.lineEnd).put(it.line))
            }
        })

    private fun read(o: JSONObject): TimedLyrics? {
        if (o.optBoolean("none") || o.optInt("v") != MATCHER_VERSION) return null
        val a = o.getJSONArray("words")
        val words = (0 until a.length()).map {
            val w = a.getJSONArray(it)
            LyricWord(w.getString(0), w.getString(1), w.getDouble(2), w.getDouble(3), w.getBoolean(4), w.getBoolean(5), w.getInt(6))
        }
        return TimedLyrics(words, o.getString("source"), o.getDouble("offset"))
    }
}
