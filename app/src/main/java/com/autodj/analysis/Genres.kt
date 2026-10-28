package com.autodj.analysis

/**
 * Genre families: the crates AutoDJ mixes within. Free-text tags such as "Tech House" or
 * "Deep House" map into one family, so they count as close.
 */
object Genres {
    const val HOUSE = "House"
    const val TECHNO = "Techno"
    const val TRANCE = "Trance"
    const val DNB = "Drum & Bass"
    const val BASS = "Dubstep & Bass"
    const val HIPHOP = "Hip-Hop"
    const val LATIN = "Latin"
    const val CHILL = "Chill"
    const val ROCK = "Rock"
    const val POP = "Pop & Dance"
    const val UNCLASSIFIED = "Unclassified"

    // Indian regional tags (declared before the keyword table that uses them).
    private val SOUTH_WORDS = listOf(
        "telugu", "tollywood", "telangana", "andhra", "tamil", "kollywood", "kuthu", "teenmaar", "marfa",
        "kannada", "sandalwood", "malayalam", "mollywood", "carnatic", "janapada",
    )
    private val NORTH_WORDS = listOf(
        "bollywood", "hindi", "punjabi", "bhangra", "haryanvi", "bhojpuri", "rajasthani", "marathi", "gujarati",
        "garba", "sufi", "qawwali", "bengali",
    )
    private val INDIAN_WORDS = listOf("indian", "desi", "filmi")

    private val tagKeywords = linkedMapOf(
        HOUSE to listOf("house", "garage", "disco"),
        TECHNO to listOf("techno", "minimal", "industrial"),
        TRANCE to listOf("trance", "psy"),
        DNB to listOf("drum & bass", "drum and bass", "drum n bass", "drum'n'bass", "dnb", "jungle", "neurofunk"),
        BASS to listOf("dubstep", "riddim", "bass music", "future bass", "brostep", "breaks"),
        HIPHOP to listOf("hip", "rap", "r&b", "rnb", "trap", "grime", "drill", "boom bap", "soul"),
        LATIN to listOf("latin", "reggaeton", "dembow", "salsa", "bachata", "afro", "amapiano", "baile", "reggae", "dancehall", "soca"),
        CHILL to listOf("chill", "ambient", "downtempo", "lofi", "lo-fi", "lounge", "jazz", "classical", "instrumental", "new age", "easy listening"),
        ROCK to listOf("rock", "metal", "punk", "indie", "grunge", "alternative", "country", "folk", "singer"),
        POP to listOf("pop", "dance", "edm", "electro", "big room", "electronic", "club") +
            // Store genres for Indian film and regional music, and catch-alls the audio refines.
            SOUTH_WORDS + NORTH_WORDS + INDIAN_WORDS + listOf("soundtrack", "world"),
    )

    /** Crate order in the library. */
    val families = tagKeywords.keys.toList()

    /** Strips ID3v1 numeric codes such as "(17)" and surrounding junk. */
    fun clean(raw: String?): String =
        raw.orEmpty().replace(Regex("""^\(\d+\)"""), "").trim()

    fun family(tag: String): String? {
        val g = tag.lowercase()
        if (g.isBlank()) return null
        // "Telugu folk" or "Telangana folk" is regional dance music, not Rock's folk.
        val indian = region(g) != null
        return tagKeywords.entries.firstOrNull { (family, keys) -> !(indian && family == ROCK) && keys.any { it in g } }?.key
    }

    /** Indian regional music, by the drums a DJ there reaches for. */
    enum class Region {
        /** Telugu, Tamil, Kannada, Malayalam: dappu, marfa, thavil, nadaswaram. */
        SOUTH,

        /** Hindi, Punjabi and the north and west: dhol. */
        NORTH,

        /** Indian, region unknown (an untagged film song): any of them. */
        INDIAN,
    }

    /**
     * How Indian film songs are titled when no genre tag says so: "Song (From \"Film\")" on
     * stores, "Song Full Video Song | Film | Actor" or "Song Lyrical" on YouTube.
     */
    private val FILM_SONG = Regex("""\(\s*from\s+["“'_]|\bvideo song\b|\blyrical\b|\bfull song\b""", RegexOption.IGNORE_CASE)

    /** Labels, composers and singers of South Indian film music, seen in titles and artists. */
    private val SOUTH_NAMES = Regex(
        """\b(aditya music|lahari|mango music|sony music south|think music|sun music|t-series (telugu|tamil)|""" +
            """devi sri prasad|dsp|thaman|anirudh|keeravani|ilaiyaraaja|ilayaraja|sid sriram|mickey j meyer|yuvan|""" +
            """harris jayaraj|g\.?\s?v\.? prakash|a\.?\s?r\.? rahman|bheems|mangli|rahul sipligunj)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Regional words as whole words in a title ("Telugu" yes, "Tamilnadu" no). */
    private val SOUTH_WORD = Regex("""\b(${SOUTH_WORDS.joinToString("|")})\b""")
    private val NORTH_WORD = Regex("""\b(${NORTH_WORDS.joinToString("|")})\b""")

    /** The same for Hindi and Punjabi music. */
    private val NORTH_NAMES = Regex(
        """\b(t-series|zee music|tips official|speed records|yrf|pritam|arijit singh|badshah|diljit|honey singh|""" +
            """shreya ghoshal|vishal-shekhar|neha kakkar)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The Indian region of a track from its genre [tag], or failing that its [title] and
     * [artist] (labels, composers, film-song titling), or null when it isn't Indian.
     */
    fun region(tag: String, title: String = "", artist: String = ""): Region? {
        val g = tag.lowercase()
        val names = "$title $artist"
        val words = names.lowercase()
        return when {
            SOUTH_WORDS.any { it in g } -> Region.SOUTH
            NORTH_WORDS.any { it in g } -> Region.NORTH
            INDIAN_WORDS.any { it in g } -> Region.INDIAN
            SOUTH_NAMES.containsMatchIn(names) || SOUTH_WORD.containsMatchIn(words) -> Region.SOUTH
            NORTH_NAMES.containsMatchIn(names) || NORTH_WORD.containsMatchIn(words) -> Region.NORTH
            FILM_SONG.containsMatchIn(names) -> Region.INDIAN
            else -> null
        }
    }

    /** Words that say "some kind of popular/electronic music" without naming a style. */
    private val genericWords = setOf(
        "dance", "electronic", "electronica", "pop", "club", "edm", "dj", "mix", "hits", "top", "40",
        "other", "misc", "various", "music", "unknown", "genre", "general", "and", "soundtrack", "world",
    )

    /**
     * True for catch-all tags such as "Dance", "Electronic", "Pop/Dance" or "Club": compilations
     * stamp these on every track, so they say little about the actual style.
     */
    fun isGeneric(tag: String): Boolean {
        val words = tag.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all { it in genericWords }
    }

    /** Families that share tempo, groove or crowd, so a cross-genre move between them sounds natural. */
    private val neighbours: Map<String, Set<String>> = mapOf(
        HOUSE to setOf(TECHNO, TRANCE, POP, LATIN, CHILL),
        TECHNO to setOf(HOUSE, TRANCE),
        TRANCE to setOf(TECHNO, HOUSE, POP),
        DNB to setOf(BASS, HIPHOP),
        BASS to setOf(DNB, HIPHOP, POP),
        HIPHOP to setOf(LATIN, BASS, DNB, POP, CHILL),
        LATIN to setOf(HIPHOP, HOUSE, POP),
        POP to setOf(HOUSE, TRANCE, LATIN, HIPHOP, BASS, ROCK),
        CHILL to setOf(HIPHOP, HOUSE),
        ROCK to setOf(POP),
    )

    /** 0 same family, 1 neighbours, 2 far apart. */
    fun distance(a: String, b: String): Int = when {
        a == b -> 0
        neighbours[a]?.contains(b) == true || neighbours[b]?.contains(a) == true -> 1
        else -> 2
    }

    /** 1.0 same genre label, 0.75 same family, 0.5 unknown, 0.0 different. */
    fun similarity(a: String, b: String): Double = when {
        a.isBlank() || b.isBlank() -> 0.5
        a.equals(b, ignoreCase = true) -> 1.0
        (family(a) ?: a) == (family(b) ?: b) -> 0.75
        else -> 0.0
    }
}
