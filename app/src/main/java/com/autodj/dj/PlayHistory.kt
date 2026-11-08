package com.autodj.dj

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One song as it was heard in a set. */
data class Play(
    val at: Long,
    /** When its set started: songs with the same value were one set. */
    val setId: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val genre: String,
    val language: String?,
    val crate: String,
    val heardSec: Int,
)

/** What was played on this device, kept in a file of its own so clearing the library leaves it alone. */
class PlayHistory(context: Context) {
    private val file = File(context.filesDir, "play_history.jsonl")

    @Synchronized
    fun add(p: Play) {
        val line = JSONObject()
            .put("at", p.at).put("set", p.setId).put("uri", p.uri).put("title", p.title).put("artist", p.artist)
            .put("genre", p.genre).put("lang", p.language ?: "").put("crate", p.crate).put("sec", p.heardSec)
        runCatching { file.appendText(line.toString() + "\n") }
    }

    /** Blocking; run off the main thread. A line cut short by a crash is skipped. */
    @Synchronized
    fun all(): List<Play> {
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                Play(
                    o.getLong("at"), o.getLong("set"), o.getString("uri"), o.getString("title"), o.getString("artist"),
                    o.getString("genre"), o.getString("lang").ifBlank { null }, o.getString("crate"), o.getInt("sec"),
                )
            }.getOrNull()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}

data class TopItem(val name: String, val plays: Int, val uri: String? = null, val artist: String = "")

data class ListeningStats(
    val heardSec: Int,
    val sets: Int,
    val longestSec: Int,
    /** Each set's length in seconds, oldest first. */
    val setLengths: List<Int>,
    val crate: String?,
    val crateUris: List<String>,
    val genre: String?,
    val language: String?,
    val artists: List<TopItem>,
    val songs: List<TopItem>,
)

/** A song counts as played once 30 seconds of it were heard; every second still counts towards the hours. */
private const val PLAY_MIN_SEC = 30

// shortcut: splits every "&" and ",", so a duo like "Simon & Garfunkel" counts as two; fine for a fun stat.
private val ARTIST_SPLIT = Regex("""\s*(?:,|&|\bfeat\.?\b|\bft\.?\b)\s*""", RegexOption.IGNORE_CASE)

fun listeningStats(all: List<Play>, sinceMs: Long = 0): ListeningStats {
    val plays = all.filter { it.at >= sinceMs }
    val sets = plays.groupBy { it.setId }.toSortedMap().values.map { s -> s.sumOf { it.heardSec } }
    // Folder sets are named "Folder · Crate"; the crate is the part that means something here.
    fun crateOf(p: Play) = p.crate.substringAfterLast(" · ")
    fun <K> mostHeard(key: (Play) -> K?): K? =
        plays.filter { key(it) != null }.groupBy { key(it)!! }.maxByOrNull { (_, v) -> v.sumOf { it.heardSec } }?.key
    val crate = mostHeard(::crateOf)
    val counted = plays.filter { it.heardSec >= PLAY_MIN_SEC }
    val artists = counted.flatMap { p -> p.artist.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotBlank() && !it.equals("Unknown", true) } }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { TopItem(it.key, it.value) }
    val songs = counted.groupBy { it.title to it.artist }.entries.sortedByDescending { it.value.size }.take(5)
        .map { (k, v) -> TopItem(k.first, v.size, v.first().uri, k.second) }
    return ListeningStats(
        heardSec = plays.sumOf { it.heardSec },
        sets = sets.size,
        longestSec = sets.maxOrNull() ?: 0,
        setLengths = sets,
        crate = crate,
        crateUris = plays.filter { crateOf(it) == crate }.sortedByDescending { it.heardSec }.map { it.uri }.distinct().take(3),
        genre = mostHeard { it.genre },
        language = mostHeard { it.language },
        artists = artists,
        songs = songs,
    )
}

/** "42 h 18 m", or "25 m" under an hour. */
fun formatHeard(sec: Int): String {
    val m = (sec / 60).coerceAtLeast(1)
    return if (m < 60) "$m m" else "${m / 60} h ${m % 60} m"
}
