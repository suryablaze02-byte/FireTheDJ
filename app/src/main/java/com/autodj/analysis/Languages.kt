package com.autodj.analysis

/**
 * Which language a song is sung in, for styling a set: Telugu songs get Telugu party touches,
 * Latin songs Latin ones. From what names it (a YT Music chip, a playlist called
 * "Tollywood…", a genre tag), the script its lyrics are written in, or failing those, the
 * everyday words its lyrics use. Null when nothing says.
 */
object Languages {
    const val ENGLISH = "English"
    const val TELUGU = "Telugu"
    const val TAMIL = "Tamil"
    const val HINDI = "Hindi"
    const val KANNADA = "Kannada"
    const val MALAYALAM = "Malayalam"
    const val BENGALI = "Bengali"
    const val PUNJABI = "Punjabi"
    const val MARATHI = "Marathi"
    const val GUJARATI = "Gujarati"
    const val BHOJPURI = "Bhojpuri"
    const val SPANISH = "Spanish"
    const val PORTUGUESE = "Portuguese"
    const val FRENCH = "French"
    const val ARABIC = "Arabic"
    const val KOREAN = "Korean"
    const val JAPANESE = "Japanese"

    val INDIAN = setOf(TELUGU, TAMIL, HINDI, KANNADA, MALAYALAM, BENGALI, PUNJABI, MARATHI, GUJARATI, BHOJPURI)

    /** Words in a chip, shelf, playlist name or tag that name a language (or its film industry). */
    private val NAMED = listOf(
        Regex("""\b(telugu|tollywood)\b""", RegexOption.IGNORE_CASE) to TELUGU,
        Regex("""\b(tamil|kollywood)\b""", RegexOption.IGNORE_CASE) to TAMIL,
        Regex("""\b(kannada|sandalwood)\b""", RegexOption.IGNORE_CASE) to KANNADA,
        Regex("""\b(malayalam|mollywood)\b""", RegexOption.IGNORE_CASE) to MALAYALAM,
        Regex("""\b(hindi|bollywood)\b""", RegexOption.IGNORE_CASE) to HINDI,
        Regex("""\b(bengali|bangla)\b""", RegexOption.IGNORE_CASE) to BENGALI,
        Regex("""\bpunjabi\b""", RegexOption.IGNORE_CASE) to PUNJABI,
        Regex("""\bmarathi\b""", RegexOption.IGNORE_CASE) to MARATHI,
        Regex("""\bgujarati\b""", RegexOption.IGNORE_CASE) to GUJARATI,
        Regex("""\bbhojpuri\b""", RegexOption.IGNORE_CASE) to BHOJPURI,
        Regex("""\b(arabic|khaleeji)\b""", RegexOption.IGNORE_CASE) to ARABIC,
        Regex("""\b(k-?pop|korean)\b""", RegexOption.IGNORE_CASE) to KOREAN,
        Regex("""\b(j-?pop|japanese|anime)\b""", RegexOption.IGNORE_CASE) to JAPANESE,
        Regex("""\b(latin|latino|reggaet[oó]n|spanish|bachata|cumbia|salsa)\b""", RegexOption.IGNORE_CASE) to SPANISH,
        Regex("""\b(brazilian|sertanejo|funk carioca|portuguese)\b""", RegexOption.IGNORE_CASE) to PORTUGUESE,
        Regex("""\b(french|fran[cç]ais)\b""", RegexOption.IGNORE_CASE) to FRENCH,
    )

    /** The language a piece of text names: "Telugu › Party today", "Tollywood Dance Essentials". */
    fun named(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return NAMED.firstOrNull { (re, _) -> re.containsMatchIn(text) }?.second
    }

    /** Unicode blocks of the scripts lyrics come in, and the language each means. */
    private val SCRIPTS = listOf(
        0x0C00..0x0C7F to TELUGU,
        0x0B80..0x0BFF to TAMIL,
        0x0C80..0x0CFF to KANNADA,
        0x0D00..0x0D7F to MALAYALAM,
        0x0980..0x09FF to BENGALI,
        0x0A00..0x0A7F to PUNJABI,
        0x0A80..0x0AFF to GUJARATI,
        0x0900..0x097F to HINDI,
        0x0600..0x06FF to ARABIC,
        0xAC00..0xD7AF to KOREAN,
        0x3040..0x30FF to JAPANESE,
    )

    /** The language of lyrics written in its own script (before they're turned into English letters); null for Latin letters. */
    fun fromScript(text: String): String? {
        val counts = HashMap<String, Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            SCRIPTS.firstOrNull { cp in it.first }?.let { counts.merge(it.second, 1, Int::plus) }
            i += Character.charCount(cp)
        }
        val (lang, n) = counts.maxByOrNull { it.value } ?: return null
        return lang.takeIf { n >= MIN_SCRIPT_CHARS }
    }

    /** Everyday words each language uses a lot, as written in English letters. */
    private val COMMON = mapOf(
        ENGLISH to setOf("the", "and", "you", "i", "me", "my", "your", "love", "is", "it", "in", "we", "what", "don't", "know", "just", "baby", "be", "all"),
        SPANISH to setOf("que", "el", "la", "de", "mi", "tu", "yo", "te", "amor", "es", "y", "en", "con", "por", "para", "corazon", "corazón", "quiero", "como", "pero"),
        PORTUGUESE to setOf("que", "eu", "você", "voce", "não", "nao", "meu", "minha", "amor", "é", "pra", "com", "uma", "do", "da", "coração", "quero"),
        FRENCH to setOf("je", "tu", "le", "la", "les", "et", "mon", "ma", "pas", "est", "dans", "pour", "avec", "moi", "toi", "c'est", "amour"),
        HINDI to setOf("hai", "main", "mein", "tum", "tera", "teri", "mera", "meri", "dil", "pyaar", "pyar", "ho", "na", "kya", "yeh", "tujhe", "mujhe", "hum", "ke", "ki"),
        TELUGU to setOf("nuvvu", "nenu", "naa", "nee", "ninne", "nannu", "prema", "ra", "vaste", "chesi", "emo", "oo", "ani", "kosam", "manasu", "andam"),
        TAMIL to setOf("nee", "naan", "enna", "kadhal", "un", "en", "unna", "ennai", "unnai", "di", "da", "illa", "vaa", "nenjam", "kanna"),
        PUNJABI to setOf("ni", "tenu", "mainu", "kudi", "munda", "yaar", "nal", "vich", "sohniye", "tera", "dil"),
        ARABIC to setOf("habibi", "ya", "ana", "inta", "enta", "albi", "galbi", "wallah", "ah", "leh"),
        KOREAN to setOf("saranghae", "neo", "na", "nae", "oppa", "geurae"),
    )

    /**
     * The language of lyrics already in English letters, from the everyday words they use: the
     * language whose words make up the biggest share, if it's clearly ahead. Null when unsure.
     */
    fun fromWords(words: List<String>): String? {
        if (words.size < MIN_WORDS) return null
        val lower = words.map { it.lowercase() }
        val scores = COMMON.mapValues { (_, set) -> lower.count { it in set } }
        val sorted = scores.entries.sortedByDescending { it.value }
        val (best, n) = sorted.first().let { it.key to it.value }
        val second = sorted.getOrNull(1)?.value ?: 0
        return best.takeIf { n >= words.size * MIN_SHARE && n >= second * 1.5 }
    }

    /**
     * A set's language: the one most of its songs are sung in, when at least half of the songs
     * whose language is known share it. Null for a mixed set, or one with too little to go on.
     */
    fun ofSet(languages: List<String?>): String? {
        val known = languages.filterNotNull()
        if (known.size < 3) return null
        val (lang, n) = known.groupingBy { it }.eachCount().maxBy { it.value }
        return lang.takeIf { n * 2 >= known.size }
    }

    private const val MIN_SCRIPT_CHARS = 20
    private const val MIN_WORDS = 30
    private const val MIN_SHARE = 0.06
}
