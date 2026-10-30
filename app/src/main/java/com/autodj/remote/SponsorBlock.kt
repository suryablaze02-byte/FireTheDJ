package com.autodj.remote

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * The stretches of a YouTube upload that aren't music (a talking intro, a skit, an outro), as
 * marked by SponsorBlock's community (sponsor.ajay.app, category "music_offtopic"), so the DJ
 * mixes only the song itself.
 *
 * Privately, as SponsorBlock suggests: only the first 4 characters of the video id's SHA-256 are
 * sent, and the video is picked out of the answer here, so the service never learns which song
 * is playing. Answers (including "nothing marked") are kept for [KEEP_MS]; failed requests are
 * not, so they are asked again next time.
 *
 * SponsorBlock's data is CC BY-NC-SA 4.0, credited in Settings › About.
 */
object SponsorBlock {
    private const val TAG = "AutoDJ"
    private const val API = "https://sponsor.ajay.app/api/skipSegments/"
    private const val USER_AGENT = "FireTheDJ (https://github.com/suryablaze02-byte/FireTheDJ)"
    private const val PREFS = "sponsorblock"

    /** Marked parts change as people vote: ask again after a month. */
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * A marked part is measured against the upload's length; an upload whose length differs
     * from the audio we analysed by more than this is a different cut, and its times don't apply.
     */
    private const val LENGTH_SLACK_SEC = 2.0

    /** A stretch of a track, in seconds. */
    data class Span(val startSec: Double, val endSec: Double)

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, 0)
    }

    /**
     * The non-music parts of [videoId], whose audio we analysed as [durationSec] long: from the
     * stored answer, or asked for now. Empty when nothing is marked; null when the lookup failed.
     * Blocking; call off the main thread.
     */
    fun nonMusic(videoId: String, durationSec: Double): List<Span>? {
        stored(videoId, durationSec)?.let { return it }
        val body = try {
            request(videoId)
        } catch (e: Exception) {
            Log.w(TAG, "SponsorBlock lookup for $videoId failed: ${e.message}")
            return null
        }
        val all = parse(body, videoId)
        prefs?.edit()?.putString(videoId, JSONObject().put("at", System.currentTimeMillis()).put("spans", toJson(all)).toString())?.apply()
        return fitting(all, durationSec)
    }

    /** Only what is already stored, without asking (for screens and Android Auto). Null when not looked up yet. */
    fun stored(videoId: String, durationSec: Double): List<Span>? {
        val raw = prefs?.getString(videoId, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            if (System.currentTimeMillis() - o.getLong("at") > KEEP_MS) return null
            fitting(fromJson(o.getJSONArray("spans")), durationSec)
        }.getOrNull()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    /** A marked part with the upload length it was measured against (0 when SponsorBlock didn't say). */
    data class Marked(val span: Span, val videoSec: Double)

    /** The parts marked for [durationSec] of audio: others were measured on a different cut of the video. */
    internal fun fitting(marked: List<Marked>, durationSec: Double): List<Span> = marked
        .filter { it.videoSec <= 0 || kotlin.math.abs(it.videoSec - durationSec) <= LENGTH_SLACK_SEC }
        .map { it.span }
        .filter { it.endSec > it.startSec && it.startSec < durationSec }

    /** SponsorBlock's answer for a hash prefix: [videoId]'s skippable non-music parts. */
    internal fun parse(body: String?, videoId: String): List<Marked> {
        if (body.isNullOrBlank()) return emptyList()
        val videos = JSONArray(body)
        for (i in 0 until videos.length()) {
            val v = videos.getJSONObject(i)
            if (v.optString("videoID") != videoId) continue
            val segments = v.optJSONArray("segments") ?: return emptyList()
            return (0 until segments.length()).map { segments.getJSONObject(it) }
                .filter { it.optString("category") == "music_offtopic" && it.optString("actionType", "skip") == "skip" }
                .mapNotNull { s ->
                    val range = s.optJSONArray("segment") ?: return@mapNotNull null
                    Marked(Span(range.getDouble(0), range.getDouble(1)), s.optDouble("videoDuration", 0.0))
                }
        }
        return emptyList()
    }

    /** SHA-256 of the video id, as lowercase hex; the first 4 characters are all that's sent. */
    internal fun hashPrefix(videoId: String): String =
        MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)

    private fun request(videoId: String): String? {
        val categories = URLEncoder.encode("[\"music_offtopic\"]", "UTF-8")
        val c = URL("$API${hashPrefix(videoId)}?categories=$categories").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", USER_AGENT)
            // 404: nothing marked for any video under this prefix.
            when (val code = c.responseCode) {
                200 -> c.inputStream.bufferedReader().readText()
                404 -> null
                else -> throw IOException("HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun toJson(marked: List<Marked>) = JSONArray().apply {
        marked.forEach { put(JSONArray().put(it.span.startSec).put(it.span.endSec).put(it.videoSec)) }
    }

    private fun fromJson(a: JSONArray) = (0 until a.length()).map { a.getJSONArray(it) }
        .map { Marked(Span(it.getDouble(0), it.getDouble(1)), it.getDouble(2)) }
}
