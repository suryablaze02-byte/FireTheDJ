package com.autodj.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.autodj.BuildConfig
import com.autodj.analysis.AnalysisCache
import com.autodj.analysis.ArtworkRepository
import com.autodj.analysis.FolderScanner
import com.autodj.analysis.GenreClassifier
import com.autodj.analysis.GenreLookup
import com.autodj.analysis.Genres
import com.autodj.analysis.TrackAnalyzer
import com.autodj.dj.EngineHolder
import com.autodj.lyrics.LyricsStore
import com.autodj.dj.MixEngine
import com.autodj.dj.MixMode
import com.autodj.dj.MixService
import com.autodj.model.TrackInfo
import com.autodj.remote.ImportResult
import com.autodj.remote.PlaylistImporter
import com.autodj.remote.PlaylistUrlValidator
import com.autodj.remote.RemoteLibrary
import com.autodj.remote.RemotePlaylist
import com.autodj.remote.StreamProviders
import com.autodj.remote.StreamStore
import com.autodj.remote.TrackSink
import com.autodj.ui.vis.VisualizerStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import android.os.Process
import java.util.concurrent.Executors

enum class AnalysisStatus { QUEUED, ANALYZING, READY, FAILED }

data class LibraryItem(
    val uri: String,
    val name: String,
    val status: AnalysisStatus,
    val info: TrackInfo? = null,
    val error: String? = null,
)

/**
 * A source to mix from, alongside the genre crates: a folder the user picked, or ([remote])
 * a streaming playlist imported from a link, keyed by that link.
 */
data class MusicFolder(val uri: String, val name: String, val tracks: Set<String>, val remote: Boolean = false)

/** Progress of importing a playlist link. */
sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    data class Done(val name: String, val tracks: Int) : ImportState
    data class Failed(val reason: String) : ImportState
}

/** Source filter value for "every track in the library". */
const val ALL_SOURCES = "all"

@UnstableApi
class DjViewModel(app: Application) : AndroidViewModel(app), TrackSink {
    private val prefs = app.getSharedPreferences("library", 0)
    private val cache = AnalysisCache(app)
    private val artworkRepo = ArtworkRepository(app)
    private val genreLookup = GenreLookup(app)
    private val remoteLibrary = RemoteLibrary(app.filesDir)
    private val streamStore = StreamStore.get(app)
    private val importer = PlaylistImporter(PlaylistUrlValidator(StreamProviders.default(jamendoClientId = BuildConfig.JAMENDO_CLIENT_ID)), this)
    private val analysisQueue = Channel<String>(Channel.UNLIMITED)

    /** Leaves cores free for playback and the UI. */
    private val analysisWorkers = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

    /**
     * Analysis runs on its own background-priority threads, so decoding a whole track never
     * takes CPU from the screen (visualizers drop frames), the decks or the remote. That matters
     * most on TV boxes, whose four slow cores are also playing the mix.
     */
    private val analysisDispatcher = Executors.newFixedThreadPool(analysisWorkers) { job ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            job.run()
        }, "analysis")
    }.asCoroutineDispatcher()
    private val artworkQueue = Channel<TrackInfo>(Channel.UNLIMITED)
    private val lyricsQueue = Channel<TrackInfo>(Channel.UNLIMITED)

    private val _library = MutableStateFlow<List<LibraryItem>>(emptyList())
    val library: StateFlow<List<LibraryItem>> = _library

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders

    /** Track uri → cover art file path. */
    private val _artwork = MutableStateFlow<Map<String, String>>(emptyMap())
    val artwork: StateFlow<Map<String, String>> = _artwork

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val _mode = MutableStateFlow(
        MixMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: MixMode.SMOOTH,
    )
    val mode: StateFlow<MixMode> = _mode

    fun setMode(m: MixMode) {
        _mode.value = m
        prefs.edit().putString(KEY_MODE, m.name).apply()
    }

    private val _visualizerStyle = MutableStateFlow(
        VisualizerStyle.entries.firstOrNull { it.name == prefs.getString(KEY_VISUALIZER, null) } ?: VisualizerStyle.CLUB,
    )
    /** The full-screen visualizer's look, remembered between sets. */
    val visualizerStyle: StateFlow<VisualizerStyle> = _visualizerStyle

    fun setVisualizerStyle(s: VisualizerStyle) {
        _visualizerStyle.value = s
        prefs.edit().putString(KEY_VISUALIZER, s.name).apply()
    }

    /** Shared with [MixService]; outlives this ViewModel so the set survives the activity. */
    val engine: MixEngine = EngineHolder.get(app)

    init {
        val saved = prefs.getString(KEY_URIS, "").orEmpty().lines().filter { it.isNotBlank() }
        _library.value = saved.map { LibraryItem(it, nameFor(Uri.parse(it)), AnalysisStatus.QUEUED) }
        _folders.value = remoteLibrary.playlists().map { it.toSource() }
        Log.i("AutoDJ", "remote playlists: ${remoteLibrary.playlists().size}" + (remoteLibrary.loadError?.let { " (couldn't read: $it)" } ?: ""))
        _artwork.value = saved.mapNotNull { uri -> artworkRepo.cached(uri)?.let { uri to it.path } }.toMap()
        saved.forEach { analysisQueue.trySend(it) }
        // Several tracks at once: each analysis is single-threaded, phones have 6–8 cores.
        repeat(analysisWorkers) {
            viewModelScope.launch {
                for (uri in analysisQueue) analyze(uri)
            }
        }
        viewModelScope.launch {
            for (track in artworkQueue) {
                if (track.uri in _artwork.value) continue
                val file = withContext(Dispatchers.IO) { artworkRepo.fetch(track, remoteLibrary.track(track.uri)?.artworkUrl) } ?: continue
                _artwork.update { it + (track.uri to file.path) }
            }
        }
        // Timed lyrics for the lyric looks (and word-play mixes on the phone), one gentle request at a time.
        LyricsStore.init(app)
        viewModelScope.launch(Dispatchers.IO) {
            for (track in lyricsQueue) {
                runCatching { LyricsStore.fetch(track) }.onFailure { Log.w("AutoDJ", "lyrics for \"${track.title}\": ${it.message}") }
                delay(LYRICS_GAP_MS)
            }
        }
        // Rescan saved folders so songs added to them since last time show up.
        prefs.getString(KEY_FOLDERS, "").orEmpty().lines().filter { it.isNotBlank() }.forEach { scanFolder(Uri.parse(it)) }
    }

    /** Adds every audio file in [tree] (and its subfolders), and keeps watching it on later launches. */
    fun addFolder(tree: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val saved = savedFolders()
        if (tree.toString() !in saved) prefs.edit().putString(KEY_FOLDERS, (saved + tree.toString()).joinToString("\n")).apply()
        scanFolder(tree)
    }

    /**
     * Adds tracks found in the device's media library (the TV app's storage and USB scan). They are
     * readable for as long as the app holds the audio permission; no grants to keep.
     * Returns how many were new.
     */
    fun addDeviceTracks(uris: List<Uri>): Int {
        val before = _library.value.size
        addToLibrary(uris)
        return _library.value.size - before
    }

    /** Pastes a playlist link: validates it, fetches the playlist and adds it as a source. */
    fun importPlaylist(link: String) {
        if (_importState.value == ImportState.Working) return
        _importState.value = ImportState.Working
        viewModelScope.launch {
            _importState.value = when (val r = withContext(Dispatchers.IO) { importer.import(link) }) {
                is ImportResult.Imported -> ImportState.Done(r.playlist.name, r.playlist.tracks.size)
                is ImportResult.Failed -> ImportState.Failed(r.reason)
            }
        }
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
    }

    /** [TrackSink]: the playlist becomes a source; its tracks are analysed and mixed like local files. */
    override fun ingest(playlist: RemotePlaylist) {
        remoteLibrary.put(playlist)
        val source = playlist.toSource()
        _folders.update { list -> list.filterNot { it.uri == source.uri } + source }
        addToLibrary(playlist.tracks.map { Uri.parse(it.streamUrl) })
    }

    private fun isStream(uri: String) = uri.startsWith("https://")

    /**
     * The stream's stored copy, downloading it if needed; null if the download fails or the
     * service doesn't let apps keep copies of the track (then it streams).
     */
    private fun storeStream(uri: String): java.io.File? {
        if (remoteLibrary.track(uri)?.storable == false) {
            streamStore.delete(listOf(uri))
            return null
        }
        if (streamStore.fileFor(uri) != null) return streamStore.fileFor(uri)
        val t0 = System.currentTimeMillis()
        return runCatching { streamStore.fetch(uri) }
            .onSuccess { Log.i("AutoDJ", "stored \"${remoteLibrary.track(uri)?.title}\" (${it.length() / 1024} KB) in ${System.currentTimeMillis() - t0} ms") }
            .onFailure { Log.w("AutoDJ", "couldn't store \"${remoteLibrary.track(uri)?.title}\", it will stream: ${it.message}") }
            .getOrNull()
    }

    private fun RemotePlaylist.toSource() = MusicFolder(link, name, tracks.map { it.streamUrl }.toSet(), remote = true)

    /** Library name before analysis: the provider's title for streams, else the file name. */
    private fun nameFor(uri: Uri): String =
        remoteLibrary.track(uri.toString())?.let { t -> if (t.artist.isBlank()) t.title else "${t.artist} - ${t.title}" }
            ?: TrackAnalyzer.displayName(getApplication(), uri)

    /** Forgets a folder and drops its songs from the library. */
    fun removeFolder(uri: String) {
        val folder = _folders.value.firstOrNull { it.uri == uri } ?: return
        _folders.update { list -> list.filterNot { it.uri == uri } }
        if (folder.remote) {
            remoteLibrary.remove(uri)
            // Free the downloads, unless another imported playlist still has the track.
            val stillUsed = _folders.value.filter { it.remote }.flatMap { it.tracks }.toSet()
            viewModelScope.launch(Dispatchers.IO) { streamStore.delete(folder.tracks - stillUsed) }
        }
        prefs.edit().putString(KEY_FOLDERS, (savedFolders() - uri).joinToString("\n")).apply()
        _library.update { list -> list.filterNot { it.uri in folder.tracks } }
        save()
        releasePermission(Uri.parse(uri))
    }

    /** Stops any set and empties the library: tracks, folders, analysis and artwork. Music files are untouched. */
    fun clearLibrary() {
        engine.stop()
        _library.value.forEach { releasePermission(Uri.parse(it.uri)) }
        savedFolders().forEach { releasePermission(Uri.parse(it)) }
        _library.value = emptyList()
        _folders.value = emptyList()
        _artwork.value = emptyMap()
        prefs.edit().remove(KEY_URIS).remove(KEY_FOLDERS).apply()
        viewModelScope.launch(Dispatchers.IO) {
            cache.clear()
            artworkRepo.clear()
            genreLookup.clear()
            remoteLibrary.clear()
            streamStore.clear()
        }
    }

    private fun scanFolder(tree: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val (name, files) = withContext(Dispatchers.IO) { FolderScanner.name(app, tree) to FolderScanner.audioFiles(app, tree) }
            val folder = MusicFolder(tree.toString(), name, files.map { it.toString() }.toSet())
            _folders.update { list -> list.filterNot { it.uri == folder.uri } + folder }
            addToLibrary(files)
        }
    }

    private fun savedFolders() = prefs.getString(KEY_FOLDERS, "").orEmpty().lines().filter { it.isNotBlank() }

    private fun releasePermission(uri: Uri) {
        if (uri.scheme != "content") return // streams carry no storage permission
        try {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    private fun addToLibrary(uris: List<Uri>) {
        val known = _library.value.map { it.uri }.toSet()
        val fresh = uris.filter { it.toString() !in known }.distinct()
        _library.update { list ->
            list + fresh.map { LibraryItem(it.toString(), nameFor(it), AnalysisStatus.QUEUED) }
        }
        save()
        fresh.forEach { analysisQueue.trySend(it.toString()) }
    }

    fun remove(uri: String) {
        _library.update { list -> list.filterNot { it.uri == uri } }
        save()
    }

    /**
     * Mixes one genre crate, or every genre for [ALL], from [source]: the whole library
     * ([ALL_SOURCES]) or one folder. Unclassified tracks are never mixed in. The engine decides
     * the order as it plays.
     */
    fun startSet(crate: String, source: String = ALL_SOURCES) {
        if (crate == Genres.UNCLASSIFIED) return
        val folder = _folders.value.firstOrNull { it.uri == source }
        val tracks = _library.value.mapNotNull { it.info }.filter { belongs(it, crate, folder) }
        if (tracks.isEmpty()) return
        // Media3 promotes the service to the foreground once the session reports playing.
        val app = getApplication<Application>()
        app.startService(Intent(app, MixService::class.java))
        val crateLabel = if (crate == ALL) "All genres" else crate
        val label = if (folder != null) "${folder.name} · $crateLabel" else crateLabel
        engine.start(tracks, label, _mode.value)
        playingSet = PlayingSet(crate, folder?.uri, label)
    }

    /** What the set playing now was started from, so songs analysed during it can join it. */
    private class PlayingSet(val crate: String, val folderUri: String?, val label: String)
    private var playingSet: PlayingSet? = null

    private fun belongs(t: TrackInfo, crate: String, folder: MusicFolder?) =
        t.genreFamily != Genres.UNCLASSIFIED && (crate == ALL || t.genreFamily == crate) &&
            (folder == null || t.uri in folder.tracks)

    /**
     * A song just finished analysing: if a set from its crate (and source) is playing, it joins
     * that set's pool, so the crate grows on the fly instead of only on the next set. A set
     * started elsewhere (Android Auto) carries a different label and is left alone.
     */
    private fun offerToPlayingSet(t: TrackInfo) {
        val set = playingSet ?: return
        val state = engine.state.value
        if (!state.running || state.crate != set.label) return
        val folder = set.folderUri?.let { uri -> _folders.value.firstOrNull { it.uri == uri } ?: return }
        if (!belongs(t, set.crate, folder)) return
        engine.addTracks(listOf(t))
        Log.i("AutoDJ", "added \"${t.title}\" to the playing set (${set.label})")
    }

    fun togglePause() = engine.togglePause()
    fun stop() = engine.stop()

    private suspend fun analyze(uri: String) {
        if (_library.value.none { it.uri == uri }) return
        setItem(uri) { it.copy(status = AnalysisStatus.ANALYZING) }
        try {
            // Streams are downloaded once, here: analysis reads the stored copy and the decks play it.
            // If the download fails the track still works, streaming instead.
            val stored = if (isStream(uri)) withContext(Dispatchers.IO) { storeStream(uri) } else null
            val analysed = withContext(analysisDispatcher) {
                cache.get(uri) ?: TrackAnalyzer.analyze(
                    getApplication(), Uri.parse(uri), remoteLibrary.track(uri), stored?.let { Uri.fromFile(it) } ?: Uri.parse(uri),
                ).also { cache.put(it) }
            }
            val info = withOnlineGenre(analysed)
            setItem(uri) { it.copy(status = AnalysisStatus.READY, info = info, name = info.title) }
            offerToPlayingSet(info)
            artworkQueue.trySend(info)
            lyricsQueue.trySend(info)
        } catch (e: Exception) {
            setItem(uri) { it.copy(status = AnalysisStatus.FAILED, error = e.message ?: e.javaClass.simpleName) }
        }
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

    private fun setItem(uri: String, change: (LibraryItem) -> LibraryItem) {
        _library.update { list -> list.map { if (it.uri == uri) change(it) else it } }
    }

    private fun save() {
        prefs.edit().putString(KEY_URIS, _library.value.joinToString("\n") { it.uri }).apply()
    }

    override fun onCleared() {
        analysisDispatcher.close()
        super.onCleared()
    }

    private companion object {
        const val KEY_URIS = "uris"
        const val KEY_MODE = "mode"
        const val KEY_VISUALIZER = "visualizer"
        const val KEY_FOLDERS = "folders"

        /** Pause between lyrics requests, to go easy on the free catalogue. */
        const val LYRICS_GAP_MS = 400L
    }
}
