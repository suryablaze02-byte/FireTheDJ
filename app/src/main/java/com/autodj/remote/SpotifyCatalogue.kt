package com.autodj.remote

/** One of Spotify's own playlists, as a card: imported from YouTube like a pasted link. */
data class SpotifyCard(
    val title: String,
    val id: String,
    val section: String,
    /** Extra words it's found by ("edm", "telugu", "gym"). */
    val tags: String = "",
) {
    val link get() = "https://open.spotify.com/playlist/$id"

    /** Where it was picked, kept with the playlist: names its language ("Spotify › India › Hot Hits Telugu"). */
    val origin get() = "Spotify › $section › $title"
}

/** A titled row of playlists. */
data class SpotifySection(val title: String, val cards: List<SpotifyCard>)

/**
 * Spotify's curated playlists FireTheDJ offers, by section. Spotify has no public way to list
 * or search them without signing in, so they're a fixed list: each id was checked against its
 * public page (2026-10-30). Spotify refreshes their songs; a retired playlist fails to import
 * and should be dropped here.
 */
object SpotifyCatalogue {
    val sections: List<SpotifySection> = listOf(
        section(
            "Top & trending",
            "Today's Top Hits" to "37i9dQZF1DXcBWIGoYBM5M",
            "Viral Hits" to "37i9dQZF1DX2L0iB23Enbq",
            "Pop Rising" to "37i9dQZF1DWUa8ZRTfalHk",
            "New Music Friday" to "37i9dQZF1DX4JAvHpjipBk",
            "Mega Hit Mix" to "37i9dQZF1DXbYM3nMM0oPk",
            "big on the internet" to "37i9dQZF1DX5Vy6DFOcx00",
            "Pop Up" to "37i9dQZF1DX6aTaZa0K6VA",
            "Happy Pop Hits" to "37i9dQZF1DWVlYsZJXqdym",
            "Mood Booster" to "37i9dQZF1DX3rxVfibe1L0",
            "Happy Hits!" to "37i9dQZF1DXdPec7aLTmlC",
            "Fresh Finds" to "37i9dQZF1DWWjGdmeTyeJ6",
            tags = "pop hits new chart trending",
        ),
        section(
            "Party & dance",
            "Dance Party" to "37i9dQZF1DXaXB8fQg7xif",
            "Summer Party" to "37i9dQZF1DX5Ozry5U6G0d",
            "Dance Hits" to "37i9dQZF1DX0BcQWzuB7ZO",
            "Massive Dance Hits" to "37i9dQZF1DX5uokaTN4FTR",
            "Dance Rising" to "37i9dQZF1DX8tZsk68tuDw",
            "Main Stage" to "37i9dQZF1DX7ZUug1ANKRP",
            "EDM Hits" to "37i9dQZF1DX3Kdv0IChEm9",
            "mint" to "37i9dQZF1DX4dyzvuaRJ0n",
            "Dance Classics" to "37i9dQZF1DX8a1tdzq5tbM",
            "Throwback Jams" to "37i9dQZF1DX8ky12eWIvcW",
            "Rock Party" to "37i9dQZF1DX8FwnYE6PRvL",
            "songs to scream in the car" to "37i9dQZF1DX4mWCZw6qYIw",
            tags = "party dance edm club festival",
        ),
        section(
            "Electronic",
            "Housewerk" to "37i9dQZF1DXa8NOEUWPn9W",
            "Tech House Operator" to "37i9dQZF1DWVY4eLfA3XFQ",
            "Techno Bunker" to "37i9dQZF1DX6J5NfMJS675",
            "Lowkey Tech" to "37i9dQZF1DX0r3x8OtiwEM",
            "Trance Mission" to "37i9dQZF1DX91oIci4su1D",
            "Hardstyle Bangers" to "37i9dQZF1DX0pH2SQMRXnC",
            "Massive Drum & Bass" to "37i9dQZF1DX5wDmLW735Yd",
            "Tropical House" to "37i9dQZF1DX0AMssoUKCz7",
            "Deep House Relax" to "37i9dQZF1DX2TRYkJECvfC",
            "Lo-Fi House" to "37i9dQZF1DXbXD9pMSZomS",
            tags = "electronic edm house techno trance dnb drum bass hardstyle",
        ),
        section(
            "Hip-hop & R&B",
            "RapCaviar" to "37i9dQZF1DX0XUsuxWHRQd",
            "Get Turnt" to "37i9dQZF1DWY4xHQp97fN6",
            "Most Necessary" to "37i9dQZF1DX2RxBh64BHjQ",
            "Gold School" to "37i9dQZF1DWVA1Gq4XHa6U",
            "I Love My '90s Hip-Hop" to "37i9dQZF1DX186v583rmzp",
            "Beast Mode Hip-Hop" to "37i9dQZF1DX9oh43oAzkyx",
            "Feelin' Myself" to "37i9dQZF1DX6GwdWRQMQpq",
            "RNB X" to "37i9dQZF1DX4SBhb3fqCJd",
            "I Love My '00s R&B" to "37i9dQZF1DWYmmr74INQlb",
            "Workout Twerkout" to "37i9dQZF1DX0HRj9P7NxeE",
            tags = "hip hop rap rnb r&b trap",
        ),
        section(
            "India",
            "Hot Hits Hindi" to "37i9dQZF1DX0XUfTFmNBRM",
            "Hot Hits Telugu" to "37i9dQZF1DX6XE7HRLM75P",
            "Hot Hits Tamil" to "37i9dQZF1DX1i3hvzHpcQV",
            "Punjabi 101" to "37i9dQZF1DX5cZuAHLNjGz",
            "Bollywood Dance Music" to "37i9dQZF1DX8xfQRRX1PDm",
            "Bollywood Mush" to "37i9dQZF1DXdpQPPZq3F7n",
            "New Music Hindi" to "37i9dQZF1DXd8cOUiye1o2",
            "Indie India" to "37i9dQZF1DX5q67ZpWyRrZ",
            tags = "india indian desi bollywood tollywood kollywood hindi telugu tamil punjabi",
        ),
        section(
            "World",
            "Viva Latino" to "37i9dQZF1DX10zKzsJ2jva",
            "Baila Reggaeton" to "37i9dQZF1DWY7IeIP1cdjF",
            "Mansión Reggaetón" to "37i9dQZF1DWZjqjZMudx9T",
            "Éxitos México" to "37i9dQZF1DX5BAPG29mHS8",
            "All New Latin" to "37i9dQZF1DX1hVRardJ30X",
            "Funk Hits (Brazil)" to "37i9dQZF1DWTkIwO2HDifB",
            "Afropop" to "37i9dQZF1DWYs2pvwxWA7l",
            "African Heat" to "37i9dQZF1DWYkaDif7Ztbp",
            "Dancehall Official" to "37i9dQZF1DXan38dNVDdl4",
            "Yalla (Arabic)" to "37i9dQZF1DX5cO1uP1XC1g",
            "K-Pop ON!" to "37i9dQZF1DX9tPFwDMOaN1",
            "GLOW (K-Pop)" to "37i9dQZF1DX3LyU0mhfqgP",
            "TrenChill K-R&B" to "37i9dQZF1DXbirtHQBuwCo",
            "Tokyo Super Hits!" to "37i9dQZF1DXafb0IuPwJyF",
            "Hits du Moment (French)" to "37i9dQZF1DWVuV87wUBNwc",
            tags = "world latin spanish reggaeton portuguese brazil afro afrobeats arabic korean kpop japanese french",
        ),
        section(
            "Decades",
            "All Out 70s" to "37i9dQZF1DWTJ7xPn4vNaz",
            "All Out 80s" to "37i9dQZF1DX4UtSsGT1Sbe",
            "All Out 90s" to "37i9dQZF1DXbTxeAdrVG2l",
            "All Out 2000s" to "37i9dQZF1DX4o1oenSJRJd",
            "All Out 2010s" to "37i9dQZF1DX5Ejj0EkURtP",
            "Rock Classics" to "37i9dQZF1DWXRqgorJj26U",
            "90s Rock Anthems" to "37i9dQZF1DX1rVvRgjX59F",
            tags = "decades throwback retro classic oldies 70s 80s 90s 2000s 2010s",
        ),
        section(
            "Workout",
            "Beast Mode" to "37i9dQZF1DX76Wlfdnj7AP",
            "Workout" to "37i9dQZF1DX70RN3TfWWJh",
            "Power Workout" to "37i9dQZF1DWUVpAXiEPK8P",
            "Cardio" to "37i9dQZF1DWSJHnPb1f0X3",
            "Gym Hits" to "37i9dQZF1DXdxcBWuJkbcy",
            "Hype" to "37i9dQZF1DX4eRPd9frC1m",
            tags = "workout gym fitness running energy",
        ),
        section(
            "Rock, country & jazz",
            "MARROW" to "37i9dQZF1DXcF6B6QPhFDv",
            "Kickass Metal" to "37i9dQZF1DWTcqUzwhNmKv",
            "Emo Forever" to "37i9dQZF1DX9wa6XirBPv8",
            "Hot Country" to "37i9dQZF1DX1lVhptIYRda",
            "Jazz Vibes" to "37i9dQZF1DX0SM0LYsmbMT",
            tags = "rock metal emo country jazz",
        ),
        section(
            "Chill",
            "Chill Hits" to "37i9dQZF1DX4WYpdgoIcn6",
            "Chill Tracks" to "37i9dQZF1DX6VdMW310YC7",
            "lofi beats" to "37i9dQZF1DWWQRwui0ExPn",
            tags = "chill relax lofi lo-fi calm",
        ),
    )

    val all: List<SpotifyCard> get() = sections.flatMap { it.cards }

    /**
     * The playlists whose name, section or tags hold every word of [query] (a word may be the
     * start of one: "bolly" finds Bollywood), names that start with the query first.
     */
    fun search(query: String): List<SpotifyCard> {
        val words = query.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.filter { card ->
            val text = "${card.title} ${card.section} ${card.tags}".lowercase().split(Regex("""[^\p{L}\p{N}&']+"""))
            words.all { w -> text.any { it.startsWith(w) } }
        }.sortedByDescending { it.title.lowercase().startsWith(words.first()) }
    }

    private fun section(title: String, vararg cards: Pair<String, String>, tags: String) =
        SpotifySection(title, cards.map { (name, id) -> SpotifyCard(name, id, title, tags) })
}
