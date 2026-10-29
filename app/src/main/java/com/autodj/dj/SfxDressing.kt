package com.autodj.dj

import com.autodj.analysis.Genres
import com.autodj.analysis.Languages
import com.autodj.dj.fx.Drum
import com.autodj.dj.fx.FxEvent
import com.autodj.dj.fx.FxType
import com.autodj.model.TrackInfo
import kotlin.math.max
import kotlin.math.pow

/**
 * Picks the sound effects for a moment in a track and sizes them to fit it, the way a DJ
 * rides the effects fader: effects follow the music's energy instead of a fixed level.
 *
 * - **Level** follows the energy where the effect plays: a whisper over a breakdown, full
 *   over a drop (see [level]).
 * - **Length** follows the run-up: a build is as long as the quiet stretch before the drop
 *   (2, 4 or 8 bars), and hits ring longer on bigger drops.
 * - **Choice** follows the genre's own [Palette]: filter-style risers and crashes for house,
 *   almost nothing but subs for techno, long risers and snare rolls for trance, sirens and horns
 *   for drum & bass, lasers and gunshots for bass music, horns and "pull up" shots for hip-hop,
 *   horns for Latin, light touches for pop, a whisper for chill, crashes for rock. Indian
 *   music gets sirens, horns and its region's drums (dappu and marfa in the south, dhol in the
 *   north) and the thavil; Latin music timbales, cowbell, güira, shaker and the samba whistle;
 *   Arabic music the darbuka, claps and the mizmar ([flavour], by the language sung). The
 *   options rotate, so two drops in a set don't get the same dressing.
 */
object SfxDressing {
    /**
     * A genre's sound effects: [builds] and [hits] are the rotating choices (each a set of
     * effects played together), [level] scales them all: 1 for full-on genres, lower where
     * effects should only colour the music.
     */
    class Palette(val builds: List<List<FxType>>, val hits: List<List<FxType>>, val level: Float = 1f)

    private val PALETTES: Map<String, Palette> = mapOf(
        Genres.HOUSE to Palette(
            builds = listOf(listOf(FxType.RISER, FxType.REVERSE_CYMBAL), listOf(FxType.RISER), listOf(FxType.REVERSE_CYMBAL, FxType.WHOOSH)),
            hits = listOf(listOf(FxType.CRASH, FxType.SUB_DROP), listOf(FxType.CRASH), listOf(FxType.CRASH, FxType.DOWNLIFTER)),
            level = 0.85f,
        ),
        Genres.TECHNO to Palette(
            builds = listOf(listOf(FxType.RISER), listOf(FxType.REVERSE_CYMBAL), listOf(FxType.RISER, FxType.REVERSE_CYMBAL)),
            hits = listOf(listOf(FxType.SUB_DROP), listOf(FxType.IMPACT), listOf(FxType.SUB_DROP, FxType.DOWNLIFTER)),
            level = 0.8f,
        ),
        Genres.TRANCE to Palette(
            builds = listOf(listOf(FxType.RISER, FxType.SNARE_ROLL), listOf(FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL), listOf(FxType.RISER, FxType.REVERSE_CYMBAL)),
            hits = listOf(listOf(FxType.CRASH, FxType.SUB_DROP), listOf(FxType.CRASH, FxType.DOWNLIFTER), listOf(FxType.CRASH, FxType.IMPACT)),
        ),
        Genres.DNB to Palette(
            builds = listOf(listOf(FxType.SIREN), listOf(FxType.RISER, FxType.SNARE_ROLL), listOf(FxType.SIREN, FxType.REVERSE_CYMBAL)),
            hits = listOf(listOf(FxType.HORN, FxType.SUB_DROP), listOf(FxType.SUB_DROP, FxType.IMPACT), listOf(FxType.HORN, FxType.CRASH)),
        ),
        Genres.BASS to Palette(
            builds = listOf(listOf(FxType.SIREN), listOf(FxType.LASER, FxType.RISER), listOf(FxType.RISER, FxType.REVERSE_CYMBAL)),
            hits = listOf(listOf(FxType.IMPACT, FxType.SUB_DROP), listOf(FxType.GUNSHOT, FxType.SUB_DROP), listOf(FxType.HORN, FxType.IMPACT)),
        ),
        Genres.HIPHOP to Palette(
            builds = listOf(listOf(FxType.SIREN), listOf(FxType.RISER, FxType.REVERSE_CYMBAL), listOf(FxType.SIREN)),
            hits = listOf(listOf(FxType.HORN, FxType.IMPACT), listOf(FxType.GUNSHOT, FxType.SUB_DROP), listOf(FxType.HORN, FxType.CRASH)),
        ),
        Genres.LATIN to Palette(
            builds = listOf(listOf(FxType.SIREN), listOf(FxType.RISER, FxType.REVERSE_CYMBAL), listOf(FxType.WHOOSH)),
            hits = listOf(listOf(FxType.HORN, FxType.IMPACT), listOf(FxType.HORN, FxType.CRASH), listOf(FxType.IMPACT)),
        ),
        Genres.POP to Palette(
            builds = listOf(listOf(FxType.RISER, FxType.SNARE_ROLL), listOf(FxType.REVERSE_CYMBAL), listOf(FxType.RISER)),
            hits = listOf(listOf(FxType.CRASH), listOf(FxType.CRASH, FxType.IMPACT), listOf(FxType.CRASH, FxType.DOWNLIFTER)),
            level = 0.8f,
        ),
        Genres.CHILL to Palette(
            builds = listOf(listOf(FxType.REVERSE_CYMBAL), listOf(FxType.WHOOSH), listOf(FxType.REVERSE_CYMBAL)),
            hits = listOf(emptyList(), listOf(FxType.DOWNLIFTER), emptyList()),
            level = 0.5f,
        ),
        Genres.ROCK to Palette(
            builds = listOf(listOf(FxType.REVERSE_CYMBAL), listOf(FxType.REVERSE_CYMBAL), listOf(FxType.WHOOSH)),
            hits = listOf(listOf(FxType.CRASH), listOf(FxType.CRASH), listOf(FxType.CRASH)),
            level = 0.8f,
        ),
    )

    /** The palette for [family]; an unknown family gets pop's light touch. */
    fun palette(family: String): Palette = PALETTES[family] ?: PALETTES.getValue(Genres.POP)

    /** The party sounds a track gets on top of its genre's: the drums and calls of where its music comes from. */
    enum class Flavour {
        /** Telugu, Tamil, Kannada, Malayalam: dappu, marfa, thavil, nadaswaram. */
        SOUTH_INDIAN,

        /** Hindi, Punjabi and the north and west: dhol. */
        NORTH_INDIAN,

        /** Indian, region unknown: any of them. */
        INDIAN,

        /** Spanish and Portuguese, or Latin music: timbales, cowbell, güira, shaker, the samba whistle. */
        LATIN,

        /** Arabic: darbuka, claps, the mizmar. */
        ARABIC,
    }

    /**
     * The language a track is sung in, as the app knows it (its playlist, lyrics or tags); set
     * by the app. Without it only the track's own tags and title are read.
     */
    @Volatile
    var languageOf: (TrackInfo) -> String? = { Languages.named(it.genre) ?: Languages.named(it.title) }

    /** The playing set's language, for tracks whose own isn't known. */
    @Volatile
    var setLanguage: String? = null

    /**
     * The track's [Flavour]: Indian by its region (tags, labels, composers), else by the language
     * it's sung in (or the set's, when its own isn't known), else Latin for Latin music. Null for
     * everything else: English pop or techno get their genre's sounds alone.
     */
    fun flavour(t: TrackInfo): Flavour? {
        when (region(t)) {
            Genres.Region.SOUTH -> return Flavour.SOUTH_INDIAN
            Genres.Region.NORTH -> return Flavour.NORTH_INDIAN
            Genres.Region.INDIAN -> return Flavour.INDIAN
            null -> Unit
        }
        val language = runCatching { languageOf(t) }.getOrNull() ?: setLanguage
        return when (language) {
            Languages.TELUGU, Languages.TAMIL, Languages.KANNADA, Languages.MALAYALAM -> Flavour.SOUTH_INDIAN
            in Languages.INDIAN -> Flavour.NORTH_INDIAN
            Languages.SPANISH, Languages.PORTUGUESE -> Flavour.LATIN
            Languages.ARABIC -> Flavour.ARABIC
            else -> if (t.genreFamily == Genres.LATIN) Flavour.LATIN else null
        }
    }

    /** The track's Indian region, or null. */
    fun region(t: TrackInfo): Genres.Region? = Genres.region(t.genre, t.title, t.artist)

    fun indian(t: TrackInfo) = when (flavour(t)) {
        Flavour.SOUTH_INDIAN, Flavour.NORTH_INDIAN, Flavour.INDIAN -> true
        else -> false
    }

    /** Whether the track gets regional sounds at all. */
    fun regional(t: TrackInfo) = flavour(t) != null

    /**
     * The drum for this [turn]: dappu and marfa in the south, dhol in the north, timbales for
     * Latin music, the darbuka for Arabic.
     */
    fun drum(t: TrackInfo, turn: Int): FxType {
        val kit = when (flavour(t)) {
            Flavour.SOUTH_INDIAN -> listOf(FxType.DAPPU, FxType.MARFA)
            Flavour.NORTH_INDIAN -> listOf(FxType.DHOL)
            Flavour.LATIN -> listOf(FxType.TIMBALES)
            Flavour.ARABIC -> listOf(FxType.DARBUKA)
            else -> listOf(FxType.DAPPU, FxType.DHOL, FxType.MARFA)
        }
        return kit[Math.floorMod(turn, kit.size)]
    }

    /** The track's tonic, kept in a reed's range. */
    private fun reedTonic(t: TrackInfo): Double {
        var tonic = 261.63 * 2.0.pow(Math.floorMod(t.keyPc, 12) / 12.0)
        if (tonic < 330) tonic *= 2
        return tonic
    }

    /** The nadaswaram in the track's own key, from [start] to [end]. */
    fun reed(t: TrackInfo, start: Double, end: Double, level: Float): FxEvent =
        FxEvent(FxType.NADASWARAM, start, end, level * 0.8f, level, p1 = reedTonic(t).toFloat(), p2 = if (t.keyMinor) 1f else 0f)

    /** The mizmar climbing hijaz from the track's own tonic, from [start] to [end]. */
    fun mizmar(t: TrackInfo, start: Double, end: Double, level: Float): FxEvent =
        FxEvent(FxType.MIZMAR, start, end, level * 0.8f, level, p1 = reedTonic(t).toFloat())

    /** Energy around [bar] (two bars either side), 0..1. */
    private fun energyAround(t: TrackInfo, bar: Int) = t.energyIn(max(0, bar - 2), bar + 2).coerceIn(0.0, 1.0)

    /**
     * Effect level for an effect at [bar]: from 35% of [peak] over a quiet passage up to [peak]
     * over the loudest parts.
     */
    fun level(t: TrackInfo, bar: Int, peak: Float = 0.9f): Float =
        (peak * (0.35 + 0.65 * energyAround(t, bar))).toFloat()

    /** How many quiet bars lead into [bar], rounded down to a build length of 2, 4 or 8. */
    fun buildBars(t: TrackInfo, bar: Int): Int {
        var quiet = 0
        while (quiet < 8 && bar - quiet - 1 >= 0 && t.energyIn(bar - quiet - 1, bar - quiet) < 0.7) quiet++
        return when {
            quiet >= 8 -> 8
            quiet >= 4 -> 4
            else -> 2
        }
    }

    /**
     * The build into a hit on [bar] of [t]: [bars] long, swelling to the hit, from [family]'s
     * palette (by default the track's own genre; a set styles it by its crate). [turn] rotates
     * the choice.
     */
    fun build(t: TrackInfo, bar: Int, bars: Int, turn: Int, peak: Float = 0.9f, family: String = t.genreFamily): List<FxEvent> {
        val hit = t.barTime(bar)
        val from = hit - bars * t.barSec
        val pal = palette(family)
        val top = level(t, bar, peak) * pal.level
        val low = top * 0.35f
        val flavour = flavour(t)
        if (flavour != null) {
            return when (flavour) {
                Flavour.LATIN -> latinBuild(t, hit, from, low, top, turn)
                Flavour.ARABIC -> arabicBuild(t, hit, from, low, top, turn)
                else -> indianBuild(t, hit, from, low, top, turn)
            }
        }
        return pal.builds[Math.floorMod(turn, pal.builds.size)].map { type ->
            when (type) {
                // Over the whole build, swelling.
                FxType.RISER -> FxEvent(type, from, hit, low, top)
                FxType.SIREN -> FxEvent(type, from, hit, low, top * 0.85f, p1 = if (bars >= 4) 2.5f else 4f)
                // Over the last half.
                FxType.SNARE_ROLL -> FxEvent(type, hit - max(1, bars / 2) * t.barSec, hit, low, top * 0.8f)
                FxType.LASER -> FxEvent(type, hit - 2 * t.barSec, hit, top * 0.5f, top * 0.85f, p1 = 0.5f)
                // Sucking into the hit.
                FxType.REVERSE_CYMBAL -> FxEvent(type, hit - t.barSec, hit, top * 0.8f, top * 0.8f)
                else -> FxEvent(type, hit - 2 * t.beatSec, hit, top * 0.8f, top * 0.8f)
            }
        }
    }

    /** Roadshow builds: a siren, the region's drum grooving and filling into the hit, or a riser with a fill. */
    private fun indianBuild(t: TrackInfo, hit: Double, from: Double, low: Float, top: Float, turn: Int): List<FxEvent> {
        val drum = drum(t, turn)
        val lastBar = hit - t.barSec
        return when (Math.floorMod(turn, 3)) {
            // Siren over the build, the drum's fill over its last bar.
            0 -> listOf(
                FxEvent(FxType.SIREN, from, lastBar, low, top * 0.8f, p1 = 3f),
                FxEvent(drum, lastBar, hit, top * 0.75f, top, p1 = Drum.FILL),
            )
            // The drum grooves through the build and fills into the hit.
            1 -> listOf(FxEvent(drum, from, hit, low * 1.4f, top, p1 = Drum.FILL))
            else -> listOf(
                FxEvent(FxType.RISER, from, hit, low, top * 0.75f),
                FxEvent(drum, lastBar, hit, top * 0.7f, top * 0.95f, p1 = Drum.FILL),
            )
        }
    }

    /**
     * Carnival builds: the whistle calling over a shaker with the timbales rolling in, the güira
     * and cowbell grooving into a timbales roll, or a riser with the roll.
     */
    private fun latinBuild(t: TrackInfo, hit: Double, from: Double, low: Float, top: Float, turn: Int): List<FxEvent> {
        val lastBar = hit - t.barSec
        val roll = FxEvent(FxType.TIMBALES, lastBar, hit, top * 0.75f, top, p1 = Drum.FILL)
        return when (Math.floorMod(turn, 3)) {
            0 -> listOf(
                FxEvent(FxType.SHAKER, from, lastBar, low, top * 0.6f, p1 = Drum.FILL),
                FxEvent(FxType.WHISTLE, max(from, lastBar - t.barSec), lastBar, top * 0.5f, top * 0.7f, p1 = Drum.FILL),
                roll,
            )
            1 -> listOf(
                FxEvent(FxType.GUIRA, from, lastBar, low, top * 0.6f, p1 = Drum.FILL),
                FxEvent(FxType.COWBELL, from, lastBar, low, top * 0.6f, p1 = Drum.FILL),
                roll,
            )
            else -> listOf(FxEvent(FxType.RISER, from, hit, low, top * 0.75f), roll)
        }
    }

    /** Arabic wedding builds: the darbuka grooving into its roll, claps doubling up, or the mizmar over the last bars. */
    private fun arabicBuild(t: TrackInfo, hit: Double, from: Double, low: Float, top: Float, turn: Int): List<FxEvent> {
        val lastBar = hit - t.barSec
        return when (Math.floorMod(turn, 3)) {
            0 -> listOf(FxEvent(FxType.DARBUKA, from, hit, low * 1.4f, top, p1 = Drum.FILL))
            1 -> listOf(
                FxEvent(FxType.RISER, from, hit, low, top * 0.7f),
                FxEvent(FxType.CLAPS, from, hit, low, top * 0.8f, p1 = Drum.FILL),
                FxEvent(FxType.DARBUKA, lastBar, hit, top * 0.7f, top * 0.95f, p1 = Drum.FILL),
            )
            else -> listOf(
                mizmar(t, max(from, lastBar - t.barSec), lastBar, top * 0.8f),
                FxEvent(FxType.DARBUKA, lastBar, hit, top * 0.75f, top, p1 = Drum.FILL),
            )
        }
    }

    /** Horn and boom, timbales and cowbell slammed together, or the whistle's long blast with a horn. */
    private fun latinHit(t: TrackInfo, at: Double, ring: Double, top: Float, turn: Int): List<FxEvent> = when (Math.floorMod(turn, 3)) {
        0 -> listOf(FxEvent(FxType.HORN, at, at + t.barSec, top, top), FxEvent(FxType.IMPACT, at, at + ring, top, top))
        1 -> listOf(
            FxEvent(FxType.TIMBALES, at, at + t.barSec, top, top, p1 = Drum.HIT),
            FxEvent(FxType.COWBELL, at, at + t.barSec, top * 0.8f, top * 0.8f, p1 = Drum.HIT),
            FxEvent(FxType.SUB_DROP, at, at + ring, top * 0.9f, top * 0.9f),
        )
        else -> listOf(
            FxEvent(FxType.WHISTLE, at, at + t.barSec, top * 0.8f, top * 0.8f, p1 = Drum.HIT),
            FxEvent(FxType.HORN, at, at + t.barSec, top * 0.9f, top * 0.9f),
        )
    }

    /** "Dum, tek-tek" with a boom, a big clap with an impact, or a horn with the darbuka. */
    private fun arabicHit(t: TrackInfo, at: Double, ring: Double, top: Float, turn: Int): List<FxEvent> = when (Math.floorMod(turn, 3)) {
        0 -> listOf(
            FxEvent(FxType.DARBUKA, at, at + t.barSec, top, top, p1 = Drum.HIT),
            FxEvent(FxType.SUB_DROP, at, at + ring, top * 0.9f, top * 0.9f),
        )
        1 -> listOf(
            FxEvent(FxType.CLAPS, at, at + t.barSec, top, top, p1 = Drum.HIT),
            FxEvent(FxType.IMPACT, at, at + ring, top, top),
        )
        else -> listOf(
            FxEvent(FxType.HORN, at, at + t.barSec, top * 0.9f, top * 0.9f),
            FxEvent(FxType.DARBUKA, at, at + t.barSec, top, top, p1 = Drum.HIT),
        )
    }

    /** What lands on the downbeat of [bar], from [family]'s palette, sized by how big that moment is. */
    fun hit(t: TrackInfo, bar: Int, turn: Int, peak: Float = 0.9f, family: String = t.genreFamily): List<FxEvent> {
        val at = t.barTime(bar)
        val energy = energyAround(t, bar + 2)
        val pal = palette(family)
        val top = level(t, bar + 2, peak) * pal.level
        // Bigger drops ring longer.
        val ring = 2.0 + 3.0 * energy
        val flavour = flavour(t)
        if (flavour != null) {
            return when (flavour) {
                Flavour.LATIN -> latinHit(t, at, ring, top, turn)
                Flavour.ARABIC -> arabicHit(t, at, ring, top, turn)
                else -> indianHit(t, at, ring, top, turn)
            }
        }
        return pal.hits[Math.floorMod(turn, pal.hits.size)].map { type ->
            when (type) {
                FxType.HORN -> FxEvent(type, at, at + t.barSec, top, top)
                // "Pull up": three shots on the beat.
                FxType.GUNSHOT -> FxEvent(type, at, at + 3 * t.beatSec, top * 0.8f, top * 0.8f, p1 = 1f)
                FxType.DOWNLIFTER -> FxEvent(type, at, at + 2 * t.barSec, top * 0.6f, top * 0.6f)
                FxType.CRASH -> FxEvent(type, at, at + ring, top * 0.8f, top * 0.8f)
                else -> FxEvent(type, at, at + ring, top, top)
            }
        }
    }

    /** Horn and boom, the drum's accents ("teen maar" or a unison dhol hit), or a thavil "ta-ka-dhim" with a horn. */
    private fun indianHit(t: TrackInfo, at: Double, ring: Double, top: Float, turn: Int): List<FxEvent> {
        val drum = drum(t, turn)
        return when (Math.floorMod(turn, 3)) {
            0 -> listOf(FxEvent(FxType.HORN, at, at + t.barSec, top, top), FxEvent(FxType.IMPACT, at, at + ring, top, top))
            1 -> listOf(
                FxEvent(drum, at, at + t.barSec, top, top, p1 = Drum.HIT),
                FxEvent(FxType.SUB_DROP, at, at + ring, top * 0.9f, top * 0.9f),
            )
            else -> listOf(
                FxEvent(if (region(t) == Genres.Region.NORTH) FxType.DHOL else FxType.THAVIL, at, at + t.barSec, top, top, p1 = Drum.HIT),
                FxEvent(FxType.HORN, at, at + t.barSec, top * 0.9f, top * 0.9f),
            )
        }
    }
}
