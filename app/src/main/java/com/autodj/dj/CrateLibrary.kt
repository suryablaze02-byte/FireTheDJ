package com.autodj.dj

import android.content.Context
import com.autodj.analysis.AnalysisCache
import com.autodj.analysis.Genres
import com.autodj.model.TrackInfo

/**
 * The library as saved by the app (its track list and their analysis), for starting a set
 * from outside the app's screens, such as Android Auto. Only analysed, classified tracks count,
 * the same rule the Library screen follows.
 */
class CrateLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("library", 0)
    private val cache = AnalysisCache(context)

    /** One crate on offer: a genre family, or every genre ([family] null). */
    data class Crate(val family: String?, val label: String, val tracks: List<TrackInfo>) {
        val mediaId get() = PREFIX + (family ?: ALL)
    }

    /** Blocking (reads the analysis files); run off the main thread. Biggest crates first, "All genres" on top. */
    fun crates(): List<Crate> {
        val tracks = prefs.getString("uris", "").orEmpty().lines().filter { it.isNotBlank() }
            .mapNotNull { cache.get(it) }
            .filter { it.genreFamily != Genres.UNCLASSIFIED }
        if (tracks.isEmpty()) return emptyList()
        val families = tracks.groupBy { it.genreFamily }
            .map { (family, list) -> Crate(family, family, list) }
            .sortedByDescending { it.tracks.size }
        return listOf(Crate(null, "All genres", tracks)) + families
    }

    /** The crate a media id names, read fresh. */
    fun crate(mediaId: String): Crate? = crates().firstOrNull { it.mediaId == mediaId }

    /** The mixing mode last picked in the app. */
    fun mode(): MixMode = MixMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: MixMode.SMOOTH

    companion object {
        const val ROOT = "root"
        const val PREFIX = "crate:"
        private const val ALL = "all"
    }
}
