package com.autodj.dj

import android.content.Context
import android.content.SharedPreferences
import com.autodj.model.TrackInfo
import com.autodj.remote.YouTubeLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the listener told the app they like: songs liked (their own crate), songs hidden (never
 * mixed again, from any source) and the first-launch survey's languages, genres and artists.
 *
 * A song is known by its id (the YouTube video id, else its uri) and also by its cleaned title and
 * artists, so a song hidden from a YouTube playlist stays hidden when the same song comes in from
 * a folder or a Spotify playlist. Kept in SharedPreferences; safe to call from any thread.
 */
object Taste {
    /** A liked or hidden song, with enough to list it in Settings. */
    data class Song(val id: String, val match: String, val title: String, val artist: String)

    /** An artist picked in the survey; [photo] is the YouTube Music picture, when one was found. */
    data class Artist(val name: String, val photo: String?)

    data class Survey(val languages: List<String>, val genres: List<String>, val artists: List<Artist>)

    private lateinit var prefs: SharedPreferences
    private val lock = Any()
    private var liked = linkedMapOf<String, Song>()
    private var hidden = linkedMapOf<String, Song>()

    private val _version = MutableStateFlow(0)
    /** Bumps on every change, so screens can recompute what they show. */
    val version: StateFlow<Int> = _version

    fun init(context: Context) = synchronized(lock) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, 0)
        liked = read(KEY_LIKED)
        hidden = read(KEY_HIDDEN)
    }

    fun likedSongs(): List<Song> = synchronized(lock) { liked.values.toList().asReversed() }
    fun hiddenSongs(): List<Song> = synchronized(lock) { hidden.values.toList().asReversed() }

    fun isLiked(uri: String, title: String, artist: String) = synchronized(lock) { find(liked, uri, title, artist) != null }
    fun isHidden(uri: String, title: String, artist: String) = synchronized(lock) { find(hidden, uri, title, artist) != null }
    fun isLiked(t: TrackInfo) = isLiked(t.uri, t.title, t.artist)
    fun isHidden(t: TrackInfo) = isHidden(t.uri, t.title, t.artist)

    /** Likes the song, or takes the like back. A liked song is never hidden. Returns whether it is liked now. */
    fun toggleLike(uri: String, title: String, artist: String): Boolean = change {
        val had = find(liked, uri, title, artist)
        if (had != null) {
            liked.remove(had.id)
            false
        } else {
            val song = song(uri, title, artist)
            liked[song.id] = song
            find(hidden, uri, title, artist)?.let { hidden.remove(it.id) }
            true
        }
    }

    /** Hides the song from every set; it loses its like. */
    fun hide(uri: String, title: String, artist: String) = change {
        val song = song(uri, title, artist)
        hidden[song.id] = song
        find(liked, uri, title, artist)?.let { liked.remove(it.id) }
    }

    fun unhide(id: String) = change { hidden.remove(id) }

    fun unhide(uri: String, title: String, artist: String) = change { find(hidden, uri, title, artist)?.let { hidden.remove(it.id) } }

    fun surveyDone(): Boolean = synchronized(lock) { prefs.getBoolean(KEY_SURVEY_DONE, false) }

    fun survey(): Survey? = synchronized(lock) {
        val o = prefs.getString(KEY_SURVEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        Survey(
            strings(o.optJSONArray("languages")),
            strings(o.optJSONArray("genres")),
            objects(o.optJSONArray("artists")).map { Artist(it.optString("name"), it.optString("photo").ifBlank { null }) },
        )
    }

    fun saveSurvey(s: Survey) = change {
        val o = JSONObject()
            .put("languages", JSONArray(s.languages))
            .put("genres", JSONArray(s.genres))
            .put("artists", JSONArray(s.artists.map { JSONObject().put("name", it.name).put("photo", it.photo ?: "") }))
        prefs.edit().putString(KEY_SURVEY, o.toString()).putBoolean(KEY_SURVEY_DONE, true).apply()
    }

    /** Skipped: not offered again on launch (Settings still has it). */
    fun skipSurvey() = change { prefs.edit().putBoolean(KEY_SURVEY_DONE, true).apply() }

    /** The id a song is kept under: its YouTube video, else its uri. */
    fun idOf(uri: String) = YouTubeLinks.videoId(uri)?.let { "yt:$it" } ?: uri

    /**
     * The same song from any source: its title without upload notes ("(Official Video)") and its
     * artists in any order, letters and digits only. Blank when the title has none.
     */
    fun matchOf(title: String, artist: String): String {
        val t = norm(YouTubeLinks.cleanTitle(title))
        if (t.isEmpty()) return ""
        val artists = artist.split(ARTIST_SEP).map(::norm).filter { it.isNotEmpty() }.sorted()
        return t + "|" + artists.joinToString(",")
    }

    private fun song(uri: String, title: String, artist: String) = Song(idOf(uri), matchOf(title, artist), title, artist)

    private fun find(map: Map<String, Song>, uri: String, title: String, artist: String): Song? {
        map[idOf(uri)]?.let { return it }
        val m = matchOf(title, artist)
        return if (m.isEmpty()) null else map.values.firstOrNull { it.match == m }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun <T> change(block: () -> T): T = synchronized(lock) {
        val r = block()
        write(KEY_LIKED, liked)
        write(KEY_HIDDEN, hidden)
        _version.value++
        r
    }

    private fun read(key: String): LinkedHashMap<String, Song> {
        val out = linkedMapOf<String, Song>()
        val a = prefs.getString(key, null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return out
        objects(a).forEach { o ->
            val s = Song(o.optString("id"), o.optString("match"), o.optString("title"), o.optString("artist"))
            if (s.id.isNotBlank()) out[s.id] = s
        }
        return out
    }

    private fun write(key: String, map: Map<String, Song>) {
        val a = JSONArray(map.values.map { JSONObject().put("id", it.id).put("match", it.match).put("title", it.title).put("artist", it.artist) })
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    private fun objects(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private val ARTIST_SEP = Regex("""\s*(?:,|&|/|;|\bfeat\.?|\bft\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
    private const val PREFS = "taste"
    private const val KEY_LIKED = "liked"
    private const val KEY_HIDDEN = "hidden"
    private const val KEY_SURVEY = "survey"
    private const val KEY_SURVEY_DONE = "survey_done"
}
