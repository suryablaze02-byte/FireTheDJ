package com.autodj.analysis

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.autodj.dj.EngineHolder
import com.autodj.lyrics.LyricsStore
import com.autodj.model.TrackInfo
import com.autodj.remote.RemoteTrack
import com.autodj.remote.StreamStore
import com.autodj.remote.YouTubeStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every song's analysis, for the whole process rather than one screen: it keeps going when the
 * app is closed or swiped away, kept alive by [AnalysisService] while there is work. Each song
 * is decoded once, for its beats, key and genre and for the singing its lyrics are lined up
 * with; its lyrics are fetched in the same job, so a song is ready for word play the moment
 * it is ready to mix.
 *
 * Up to four songs at a time when nothing plays (two on low-memory phones), two while a set
 * plays, so the mix never stutters.
 */
@UnstableApi
object AnalysisJobs {
    enum class Status { QUEUED, ANALYZING, READY, FAILED }

    class Result(val status: Status, val info: TrackInfo? = null, val error: String? = null)

    /** Where every song asked for stands, by uri. */
    private val _results = MutableStateFlow<Map<String, Result>>(emptyMap())
    val results: StateFlow<Map<String, Result>> = _results

    /** Songs analysed or failed since the queue was last empty, and songs asked for in that time: the notification's "108 of 161". */
    class Progress(val done: Int, val total: Int, val lyrics: Int) {
        val busy get() = done < total
    }

    private val _progress = MutableStateFlow(Progress(0, 0, 0))
    val progress: StateFlow<Progress> = _progress

    private lateinit var app: Context
    private lateinit var cache: AnalysisCache
    private lateinit var genreLookup: GenreLookup
    private lateinit var streams: StreamStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = Channel<Pair<String, RemoteTrack?>>(Channel.UNLIMITED)
    private val wanted = ConcurrentHashMap.newKeySet<String>()
    private val running = AtomicInteger(0)
    private var maxIdle = 4

    /** Background-priority threads, so decoding never takes CPU from the screen or the decks. */
    private val cpu by lazy {
        Executors.newFixedThreadPool(4) { job ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                job.run()
            }, "analysis")
        }.asCoroutineDispatcher()
    }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        cache = AnalysisCache(app)
        genreLookup = GenreLookup(app)
        streams = StreamStore.get(app)
        LyricsStore.init(app)
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        maxIdle = if (am.isLowRamDevice) 2 else (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_AT_ONCE)
        val saved = app.getSharedPreferences(SETTINGS, 0)
        _settings.value = Settings(saved.getBoolean("background", true), saved.getInt("songs_at_once", 0).coerceIn(0, MAX_AT_ONCE))
        retryLyrics()
        repeat(MAX_AT_ONCE) {
            scope.launch {
                for ((uri, remote) in queue) {
                    // Wait for a free slot: fewer while a set plays.
                    while (running.get() >= limit()) delay(SLOT_CHECK_MS)
                    if (uri !in wanted) continue
                    running.incrementAndGet()
                    try {
                        run(uri, remote)
                    } finally {
                        running.decrementAndGet()
                    }
                }
            }
        }
    }

    /** Songs whose lyrics couldn't be looked up (the catalogue busy or unreachable), to ask again. */
    private val lyricsLater = ConcurrentHashMap<String, TrackInfo>()

    /**
     * Asks again for lyrics the catalogue was too busy to give, a few minutes after the queue
     * empties, a few rounds at most, while the app is running.
     */
    private fun retryLyrics() {
        val tries = HashMap<String, Int>()
        scope.launch {
            while (true) {
                delay(LYRICS_RETRY_MS)
                if (lyricsLater.isEmpty() || _progress.value.busy) continue
                Log.i("AutoDJ", "asking again for lyrics of ${lyricsLater.size} songs")
                for ((uri, info) in lyricsLater.toList()) {
                    val n = (tries[uri] ?: 0) + 1
                    tries[uri] = n
                    if (uri !in wanted || n > LYRICS_ROUNDS) { lyricsLater -= uri; continue }
                    val asked = withContext(cpu) { runCatching { LyricsStore.fetch(info) }.getOrDefault(true) }
                    if (asked) lyricsLater -= uri
                }
            }
        }
    }

    /**
     * What Settings › Analysis controls: whether analysis keeps going with the app in the
     * background (a foreground service), and how many songs at once when no set is playing
     * ([songsAtOnce] 0 = Auto, what this phone can take).
     */
    data class Settings(val background: Boolean = true, val songsAtOnce: Int = 0)

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    /** Songs at once on Auto: half the processor's cores, at most [MAX_AT_ONCE] (2 on a low-memory phone). */
    val autoSongsAtOnce get() = maxIdle

    fun setBackground(on: Boolean) {
        _settings.update { it.copy(background = on) }
        saveSettings()
        if (on && _progress.value.busy) AnalysisService.start(app)
    }

    fun setSongsAtOnce(n: Int) {
        _settings.update { it.copy(songsAtOnce = n.coerceIn(0, MAX_AT_ONCE)) }
        saveSettings()
    }

    private fun saveSettings() {
        val s = _settings.value
        app.getSharedPreferences(SETTINGS, 0).edit().putBoolean("background", s.background).putInt("songs_at_once", s.songsAtOnce).apply()
    }

    /** Songs analysed so far that have lyrics, and songs analysed: for "lyrics for 74 of 107". Reads the lyrics store; off the main thread. */
    fun lyricsCount(): Pair<Int, Int> {
        val ready = _results.value.filter { it.key in wanted && it.value.status == Status.READY }.keys
        return ready.count { LyricsStore.get(it) != null } to ready.size
    }

    /** How many songs may be analysed at once right now: never more than [PLAYING] while a set plays. */
    private fun limit(): Int {
        val idle = _settings.value.songsAtOnce.takeIf { it > 0 } ?: maxIdle
        val s = EngineHolder.get(app).state.value
        return if (s.running && !s.paused) minOf(PLAYING, idle) else idle
    }

    /**
     * Asks for [uri] to be analysed (once: asking again for a song already done or waiting does
     * nothing). [remote] is what its streaming service told us about it, if it's a stream.
     */
    fun enqueue(uri: String, remote: RemoteTrack?) {
        val r = _results.value[uri]
        if (uri in wanted && r != null && r.status != Status.FAILED) return
        wanted += uri
        _results.update { it + (uri to Result(Status.QUEUED)) }
        _progress.update { Progress(if (it.busy) it.done else 0, (if (it.busy) it.total else 0) + 1, if (it.busy) it.lyrics else 0) }
        queue.trySend(uri to remote)
        if (_settings.value.background) AnalysisService.start(app)
    }

    /** Stops caring about [uri] (removed from the library): a queued job for it is skipped. */
    fun forget(uri: String) {
        wanted -= uri
        _results.update { it - uri }
    }

    fun forgetAll() {
        wanted.clear()
        _results.value = emptyMap()
        _progress.value = Progress(0, 0, 0)
    }

    private suspend fun run(uri: String, remote: RemoteTrack?) {
        set(uri, Result(Status.ANALYZING))
        var lyricsFound = false
        val result = try {
            // Streams are downloaded once, here: analysis reads the stored copy and the decks play it.
            // If the download fails the track still works, streaming instead.
            val stored = if (uri.startsWith("https://")) withContext(Dispatchers.IO) { storeStream(uri, remote) } else null
            // An analysis far shorter than the song is a preview clip YouTube gave out before the
            // app swapped in full-length uploads: do it again, lyrics too.
            val cached = cache.get(uri)?.let { c ->
                val listed = remote?.durationSec
                if (listed != null && YouTubeStreams.isPreview(c.durationSec, listed)) {
                    Log.i("AutoDJ", "\"${c.title}\" was analysed as a ${c.durationSec.toInt()} s clip of a ${listed.toInt()} s song: analysing again")
                    cache.remove(uri)
                    LyricsStore.forget(uri)
                    null
                } else {
                    c
                }
            }
            val analysed = cached?.let { TrackAnalyzer.Analysed(it, FloatArray(0)) } ?: withContext(cpu) {
                TrackAnalyzer.analyzeWithSinging(app, Uri.parse(uri), remote, stored?.let { Uri.fromFile(it) } ?: Uri.parse(uri))
                    .also { cache.put(it.info) }
            }
            val info = withOnlineGenre(analysed.info)
            // Ready to mix now; its lyrics follow in the same slot, before the next song starts.
            set(uri, Result(Status.READY, info))
            val asked = withContext(cpu) {
                runCatching { LyricsStore.fetch(info, analysed.singing.takeIf { it.isNotEmpty() }) }
                    .onFailure { Log.w("AutoDJ", "lyrics for \"${info.title}\": ${it.message}") }
                    .getOrDefault(true)
            }
            // The catalogue was busy: ask again once the queue has emptied.
            if (!asked) lyricsLater[uri] = info
            lyricsFound = LyricsStore.get(info) != null
            Result(Status.READY, info)
        } catch (e: Exception) {
            Result(Status.FAILED, error = e.message ?: e.javaClass.simpleName)
        } catch (e: OutOfMemoryError) {
            // One huge file mustn't take the whole app down: its arrays are gone by now, so carry on.
            Log.w("AutoDJ", "out of memory analysing $uri")
            Result(Status.FAILED, error = "Not enough memory to analyse this song")
        }
        set(uri, result)
        _progress.update { Progress(it.done + 1, it.total, it.lyrics + if (lyricsFound) 1 else 0) }
    }

    private fun set(uri: String, r: Result) {
        if (uri !in wanted) return
        _results.update { it + (uri to r) }
    }

    /**
     * The stream's stored copy, downloading it if needed; null if the download fails or the
     * service doesn't let apps keep copies of the track (then it streams).
     */
    private fun storeStream(uri: String, remote: RemoteTrack?): java.io.File? {
        if (remote?.storable == false) {
            streams.delete(listOf(uri))
            return null
        }
        streams.fileFor(uri)?.let { return it }
        val t0 = System.currentTimeMillis()
        return runCatching { streams.fetch(uri) }
            .onSuccess { Log.i("AutoDJ", "stored \"${remote?.title}\" (${it.length() / 1024} KB) in ${System.currentTimeMillis() - t0} ms") }
            .onFailure { Log.w("AutoDJ", "couldn't store \"${remote?.title}\", it will stream: ${it.message}") }
            .getOrNull()
    }

    /**
     * Tags and audio couldn't place the track in a crate: ask the iTunes store for its genre by
     * artist and title, then file it as if the file had carried that tag.
     */
    private suspend fun withOnlineGenre(info: TrackInfo): TrackInfo {
        if (info.genreFamily != Genres.UNCLASSIFIED) return info
        val tag = withContext(Dispatchers.IO) { genreLookup.lookup(info.uri, info.artist, info.title) } ?: return info
        val g = GenreClassifier.classify(info.features, info.bpm, Genres.clean(tag), tagSource = "online")
        Log.i("AutoDJ", "genre for \"${info.title}\" from the web: $tag → ${g.family}")
        if (g.family == Genres.UNCLASSIFIED) return info
        return info.withGenre(g).also { cache.put(it) }
    }

    fun clearCaches() {
        cache.clear()
        genreLookup.clear()
    }

    private const val PLAYING = 2

    /** Workers, and the most songs at once (the decoder takes four at a time). */
    const val MAX_AT_ONCE = 4
    private const val SETTINGS = "analysis_settings"
    private const val SLOT_CHECK_MS = 500L
    private const val LYRICS_RETRY_MS = 3 * 60_000L
    private const val LYRICS_ROUNDS = 5
}
