package com.autodj.dj

import android.content.Context
import com.autodj.analysis.AnalysisCache
import com.autodj.analysis.Genres
import com.autodj.model.TrackInfo
import com.autodj.remote.SponsorBlock
import com.autodj.remote.YouTubeLinks

/** Between the genre names when a set is picked from several crates. */
const val CRATE_SEP = "\n"

/** Several crates' name: "House + Pop & Dance", or "House + 2 more" past two. */
fun cratesLabel(names: List<String>): String =
    if (names.size < 3) names.joinToString(" + ") else "${names.first()} + ${names.size - 1} more"

/**
 * The library as saved by the app (its track list and their analysis), for starting a set
 * from outside the app's screens, such as Android Auto. Only analysed, classified tracks count,
 * the same rule the Library screen follows.
 */
class CrateLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("library", 0)
    private val cache = AnalysisCache(context)

    init {
        SponsorBlock.init(context)
    }

    /** One crate on offer: a genre family, every genre ([family] null), or [YOUR_SET]. */
    data class Crate(val family: String?, val label: String, val tracks: List<TrackInfo>) {
        val mediaId get() = PREFIX + (family ?: ALL)
    }

    /** Blocking (reads the analysis files); run off the main thread. The crates last mixed together on the phone, "All genres", then the biggest crates first. */
    fun crates(): List<Crate> {
        val tracks = prefs.getString("uris", "").orEmpty().lines().filter { it.isNotBlank() }
            .mapNotNull { cache.get(it) }
            .map(::withNonMusic)
            .filter { it.genreFamily != Genres.UNCLASSIFIED }
        if (tracks.isEmpty()) return emptyList()
        val families = tracks.groupBy { it.genreFamily }
            .map { (family, list) -> Crate(family, family, list) }
            .sortedByDescending { it.tracks.size }
        val picks = prefs.getString("picks", null).orEmpty().split(CRATE_SEP)
        val picked = families.filter { it.family in picks }
        val yourSet = if (picked.size < 2) emptyList()
            else listOf(Crate(YOUR_SET, cratesLabel(picked.map { it.label }), picked.flatMap { it.tracks }))
        return yourSet + Crate(null, "All genres", tracks) + families
    }

    /** A YouTube upload's non-music parts, as last looked up (no lookup here: the app's analysis does that). */
    private fun withNonMusic(t: TrackInfo): TrackInfo {
        val id = YouTubeLinks.videoId(t.uri) ?: return t
        val parts = SponsorBlock.stored(id, t.durationSec)
        return if (parts.isNullOrEmpty()) t else t.withNonMusic(parts.map { it.startSec..it.endSec })
    }

    /** The crate a media id names, read fresh. */
    fun crate(mediaId: String): Crate? = crates().firstOrNull { it.mediaId == mediaId }

    /** The mixing mode last picked in the app. */
    fun mode(): MixMode = MixMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: MixMode.SMOOTH

    companion object {
        const val ROOT = "root"
        const val PREFIX = "crate:"
        const val YOUR_SET = "your set"
        private const val ALL = "all"
    }
}
