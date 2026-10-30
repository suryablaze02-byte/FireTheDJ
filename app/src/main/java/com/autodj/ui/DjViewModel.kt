package com.autodj.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.autodj.BuildConfig
import com.autodj.analysis.AnalysisJobs
import com.autodj.analysis.ArtworkRepository
import com.autodj.analysis.FolderScanner
import com.autodj.analysis.Genres
import com.autodj.analysis.Languages
import com.autodj.lyrics.LyricsStore
import com.autodj.analysis.TrackAnalyzer
import com.autodj.dj.EngineHolder
import com.autodj.dj.MixEngine
import com.autodj.dj.MixMode
import com.autodj.dj.MixService
import com.autodj.dj.SfxDressing
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AnalysisStatus { QUEUED, ANALYZING, READY, FAILED }

data class LibraryItem(
    val uri: String,
    val name: String,
    val status: AnalysisStatus,
    val info: TrackInfo? = null,
    val error: String? = null,
    /** The language it's sung in, once known (see [com.autodj.analysis.Languages]). */
    val language: String? = null,
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
    data class Done(val name: String, val tracks: Int, val missing: Int = 0) : ImportState
    data class Failed(val reason: String) : ImportState
}

/** Source filter value for "every track in the library". */
const val ALL_SOURCES = "all"

@UnstableApi
class DjViewModel(app: Application) : AndroidViewModel(app), TrackSink {
    private val prefs = app.getSharedPreferences("library", 0)
    private val artworkRepo = ArtworkRepository(app)
    private val remoteLibrary = RemoteLibrary(app.filesDir)
    private val streamStore = StreamStore.get(app)
    private val importer = PlaylistImporter(PlaylistUrlValidator(StreamProviders.default(jamendoClientId = BuildConfig.JAMENDO_CLIENT_ID)), this)
    private val artworkQueue = Channel<TrackInfo>(Channel.UNLIMITED)

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
        // The engine picks each song's party sounds (Latin, Arabic, Indian) by the language it's sung in.
        SfxDressing.languageOf = { t -> _library.value.firstOrNull { it.uri == t.uri }?.language }
        val saved = prefs.getString(KEY_URIS, "").orEmpty().lines().filter { it.isNotBlank() }
        _library.value = saved.map { LibraryItem(it, nameFor(Uri.parse(it)), AnalysisStatus.QUEUED) }
        _folders.value = remoteLibrary.playlists().map { it.toSource() }
        Log.i("AutoDJ", "remote playlists: ${remoteLibrary.playlists().size}" + (remoteLibrary.loadError?.let { " (couldn't read: $it)" } ?: ""))
        _artwork.value = saved.mapNotNull { uri -> artworkRepo.cached(uri)?.let { uri to it.path } }.toMap()
        // Analysis (and each song's lyrics) runs for the whole app, so it carries on with this screen
        // closed; the library mirrors where each song stands.
        AnalysisJobs.init(app)
        viewModelScope.launch { AnalysisJobs.results.collect(::mirror) }
        saved.forEach { AnalysisJobs.enqueue(it, remoteLibrary.track(it)) }
        viewModelScope.launch {
            for (track in artworkQueue) {
                if (track.uri in _artwork.value) continue
                val file = withContext(Dispatchers.IO) { artworkRepo.fetch(track, remoteLibrary.track(track.uri)?.artworkUrl) } ?: continue
                _artwork.update { it + (track.uri to file.path) }
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
    /** Where in YT Music the playlist being imported was picked, to keep with it. */
    private var importOrigin: String? = null

    fun importPlaylist(link: String, origin: String? = null) {
        if (_importState.value == ImportState.Working) return
        _importState.value = ImportState.Working
        importOrigin = origin
        viewModelScope.launch {
            _importState.value = when (val r = withContext(Dispatchers.IO) { importer.import(link) }) {
                is ImportResult.Imported -> ImportState.Done(r.playlist.name, r.playlist.tracks.size, r.playlist.missing)
                is ImportResult.Failed -> ImportState.Failed(r.reason)
            }
        }
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
    }

    /** [TrackSink]: the playlist becomes a source; its tracks are analysed and mixed like local files. */
    override fun ingest(playlist: RemotePlaylist) {
        // Re-adding a playlist without a chip keeps the one it was first picked from.
        val origin = importOrigin ?: remoteLibrary.playlists().firstOrNull { it.link == playlist.link }?.origin
        remoteLibrary.put(playlist.copy(origin = origin))
        val source = playlist.toSource()
        _folders.update { list -> list.filterNot { it.uri == source.uri } + source }
        addToLibrary(playlist.tracks.map { Uri.parse(it.streamUrl) })
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
        folder.tracks.forEach(AnalysisJobs::forget)
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
        AnalysisJobs.forgetAll()
        viewModelScope.launch(Dispatchers.IO) {
            AnalysisJobs.clearCaches()
            artworkRepo.clear()
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
        fresh.forEach { AnalysisJobs.enqueue(it.toString(), remoteLibrary.track(it.toString())) }
    }

    fun remove(uri: String) {
        AnalysisJobs.forget(uri)
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
        // The language first: the engine's first plans pick their party sounds by it.
        val languages = _library.value.associate { it.uri to it.language }
        _setLanguage.value = Languages.ofSet(tracks.map { languages[it.uri] })
        SfxDressing.setLanguage = _setLanguage.value
        Log.i("AutoDJ", "set language: ${_setLanguage.value ?: "mixed"} (" + tracks.mapNotNull { languages[it.uri] }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" } + ")")
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
    fun skip() = engine.skip()
    fun stop() = engine.stop()

    /**
     * Copies where each song stands from [AnalysisJobs] into the library. A song that has just
     * become ready joins the playing set (if it belongs) and gets its cover art looked up.
     */
    private fun mirror(results: Map<String, AnalysisJobs.Result>) {
        val readyNow = mutableListOf<TrackInfo>()
        val playlists = playlistsByTrack()
        _library.value = _library.value.map { item ->
            val r = results[item.uri] ?: return@map item
            val status = AnalysisStatus.valueOf(r.status.name)
            if (status == AnalysisStatus.READY && item.status != AnalysisStatus.READY && r.info != null) readyNow += r.info
            item.copy(
                status = status, info = r.info, error = r.error, name = r.info?.title ?: item.name,
                language = r.info?.let { languageOf(it, playlists[it.uri]) },
            )
        }
        readyNow.forEach {
            offerToPlayingSet(it)
            artworkQueue.trySend(it)
        }
    }

    /** The playlist each streamed track came in. */
    private fun playlistsByTrack(): Map<String, RemotePlaylist> =
        remoteLibrary.playlists().flatMap { p -> p.tracks.map { it.streamUrl to p } }.toMap()

    /**
     * The language a song is sung in, from the most to the least trusted say: the YT Music chip
     * or shelf its playlist was picked from, the playlist's name ("Tollywood…"), its lyrics (their
     * script, or their everyday words), then its genre tag and title. Null when nothing says.
     */
    private fun languageOf(t: TrackInfo, playlist: RemotePlaylist?): String? =
        Languages.named(playlist?.origin) ?: Languages.named(playlist?.name)
            ?: LyricsStore.get(t)?.language
            ?: Languages.named(t.genre) ?: Languages.named(t.title)

    private val _setLanguage = MutableStateFlow<String?>(null)

    /** The language most songs of the playing set are sung in, or null for a mixed set. */
    val setLanguage: StateFlow<String?> = _setLanguage

    private fun save() {
        prefs.edit().putString(KEY_URIS, _library.value.joinToString("\n") { it.uri }).apply()
    }

    private companion object {
        const val KEY_URIS = "uris"
        const val KEY_MODE = "mode"
        const val KEY_VISUALIZER = "visualizer"
        const val KEY_FOLDERS = "folders"
    }
}
