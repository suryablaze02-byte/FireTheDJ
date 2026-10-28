package com.autodj.update

/**
 * What changed in each version, newest first, shipped inside the app so "What's new" shows
 * the installed version's changes offline and straight after an update. The same items go
 * in that version's GitHub release notes; add a [Version] here with every release.
 */
object Changelog {
    class Change(val title: String, val detail: String)

    class Section(val title: String, val changes: List<Change>)

    class Version(val version: String, val summary: String, val sections: List<Section>)

    val versions = listOf(
        Version(
            "1.3.2",
            "Updates start downloading as soon as installing is allowed.",
            listOf(
                Section(
                    "Fixes",
                    listOf(
                        Change("Updating for the first time", "After you allow FireTheDJ to install apps, the update starts downloading by itself when you come back, instead of asking again."),
                        Change("A simpler What's new", "Settings shows just the changes in the version you have."),
                    ),
                ),
            ),
        ),
        Version(
            "1.3.1",
            "See what changed in every version, right in Settings.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("What's new in Settings", "Settings shows every change in the version you have, and the changes in earlier versions, on phone and TV. It works offline, straight after an update."),
                    ),
                ),
            ),
        ),
        Version(
            "1.3.0",
            "Lyrics on screen, mixes on the words, transitions that sound like the genre, and a fix for sets going silent.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Lyric visualizers", "Beat Pop builds each line word by word on the beat, the hook word in flames; Karaoke lights each word as it's sung. All lyrics are shown in English letters. On TV, pick the look with ◀ ▶ or the Look button; it no longer changes on its own."),
                        Change("Lyrics in sync", "Each song's lyrics are lined up with the singing in your own copy, so video and lyric-video versions with longer intros stay in time. Lyrics pause through loops, spins and brakes, then catch up."),
                        Change("Word play mixes (phone)", "When two songs share a word, AutoDJ mixes on it, the next song answering the word the last one sang. Key words also loop into drops."),
                        Change("Transitions by genre", "A crate's mixes and sound effects suit its genre; in All genres, each mix suits the incoming song. Telugu and other Indian songs get dappu, marfa, dhol, thavil and nadaswaram fills. Scratches are kept for hip-hop."),
                        Change("Smoother sets", "Shorter overlaps when the next song drops in, a few well-placed tricks per song instead of one every phrase, no tricks over the singing, and a blend after two cuts in a row."),
                    ),
                ),
                Section(
                    "Fixes",
                    listOf(
                        Change("No more silent sets", "A sound effect could stop the player, leaving the set showing as playing with no sound. If a song ever fails to play now, AutoDJ opens it again or moves on to the next one."),
                        Change("Right lyrics for the right song", "Lyrics must match both the title and the artist, so a song never shows another song's words."),
                    ),
                ),
            ),
        ),
        Version(
            "1.2.1",
            "A small fix for the splash screen on TV.",
            listOf(
                Section(
                    "Fixes",
                    listOf(Change("Cleaner splash on TV", "The credit line no longer overlaps the tagline, and the record is sized to fit the screen.")),
                ),
            ),
        ),
        Version(
            "1.2.0",
            "A new look, and updates from inside the app.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("A new frosted-glass look", "Glass panels over soft stage light, on phone and TV."),
                        Change("Mix and Music tabs", "Pick a crate, mode and what to mix from on Mix; add and manage music on Music."),
                        Change("Mix from one folder or playlist", "The \"From\" picker on the Mix tab."),
                        Change("A spinning glass record", "On Now Playing, with the live spectrum around it over the blurred cover art."),
                        Change("Update from inside the app", "Settings shows new versions, what's new, and installs them."),
                        Change("Support FireTheDJ over UPI", "In Settings, from ₹50, with the UPI app of your choice (on TV, scan a code)."),
                    ),
                ),
            ),
        ),
        Version(
            "1.1.0",
            "FireTheDJ comes to Android TV.",
            listOf(
                Section(
                    "FireTheDJ TV",
                    listOf(
                        Change("Start a mix from the couch", "Pick a genre crate (or All genres) and Smooth or Trickster mode with the remote."),
                        Change("Full-screen visualizers", "They start on their own when the mix plays; ◀ ▶ switch looks."),
                        Change("Music from the TV and USB drives", "Or YT Music curated playlists by mood and genre."),
                    ),
                ),
                Section(
                    "Phone and TV",
                    listOf(
                        Change("Guided tour", "The first launch walks you through adding music, crates, mode and starting the mix."),
                        Change("Songs join the running mix", "Tracks that finish analysing while a set plays are queued into it if they belong to its crate."),
                        Change("Smoother playback while analysing", "Background analysis runs at low priority so the mix and visualizers don't stutter."),
                    ),
                ),
            ),
        ),
        Version(
            "1.0.0",
            "The first release.",
            listOf(
                Section(
                    "Highlights",
                    listOf(
                        Change("Hands-free sets", "From one genre crate or all genres, a different route every time."),
                        Change("Transition recipes", "Bass swaps, phaser and flanger blends, filter and lo-fi fades, echo-outs, gate builds, backspins, vocal swaps and loop ramps."),
                        Change("Smooth or Trickster mode", "Clean blends, or scratches, brakes, echo throws, sirens and air horns."),
                        Change("On-device analysis", "Tempo, beat grid, key, phrases, drops, vocals and genre for every track."),
                        Change("Music from anywhere", "Folders on your phone, YT Music playlists, Audius, Jamendo, Internet Archive and M3U links."),
                        Change("Visualizers, casting and Android Auto", "Five full-screen looks, casting to a TV, and sets in the car."),
                    ),
                ),
            ),
        ),
    )

    /** The changes in [version] (the installed one by default), or null if it isn't listed. */
    fun of(version: String): Version? = versions.firstOrNull { it.version == version }
}
