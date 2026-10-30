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
            "1.5.2",
            "A fix for searching on the Stream page.",
            listOf(
                Section(
                    "Fixes",
                    listOf(
                        Change("Stream search", "Typing a search no longer flashes \"Couldn't search YouTube Music\": an earlier search finishing late could replace the results with that error."),
                    ),
                ),
            ),
        ),
        Version(
            "1.5.1",
            "Spotify playlists played from YouTube, a Stream page with search, and no more talking intros in the mix.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Spotify playlists", "Paste a Spotify playlist or album link, or pick one of Spotify's own playlists, and its songs are found on YouTube Music and played from there. No Spotify account needed; songs with no matching upload are left out."),
                        Change("Stream page", "YT Music and Spotify on one page, with one search across both for playlists to add."),
                        Change("Skips the talk in YouTube songs", "Talking intros, skits and outros that SponsorBlock's community has marked are left out: songs come in where the music starts and mix out before the talk. Songs with skipped talk say so in Music. Only a 4-character piece of a code made from the video's id is sent, so SponsorBlock never learns which song is playing."),
                        Change("Two decks on the TV", "Now Playing on the TV shows both decks, the mix and the controls, like a phone held sideways, with Home and the visualizer one press away."),
                    ),
                ),
            ),
        ),
        Version(
            "1.5.0",
            "Turn your phone sideways for two decks, and a new Acid Trip visualizer.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Sideways on the phone", "Turn the phone and the app turns with it. Now Playing shows both decks with their records and waveforms, the mix in the middle with the crossfader and the playback controls, and the Mix tab splits into two panes."),
                        Change("Effects panel", "Sideways, one panel shows the effects playing right now, with how far along each one is. Tap it for the full effects view: crossfade, tempo, key and every effect in the mix."),
                        Change("Acid Trip visualizer", "A melting kaleidoscope on phone and TV: petals and eyes, spirals, sacred geometry and fractal tendrils take turns every 8 bars and burst in on a drop, while shapes fly out of a black hole. It drifts in a breakdown and goes all out on the drop, with no strobes."),
                        Change("AI in control", "Upright, the AI in control label now sits under play/pause."),
                    ),
                ),
                Section(
                    "Changes",
                    listOf(
                        Change("Fewer words on screen", "Effect names like BASS BOOST now show only on the Club and LED Field looks; the others show effects as light and motion."),
                        Change("Haze & Beams removed", "If it was your look, the visualizer opens on Club."),
                    ),
                ),
            ),
        ),
        Version(
            "1.4.0",
            "Trickster tricks you can hear, lyrics ready as songs are analysed, sounds that follow the music's language, and a skip button.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Trickster, rebuilt", "A trick about every 30 seconds, stacked into drops, with a showpiece at the biggest one. Over the singing, only tricks that sit on top of the voice. Mixes stay clean: never two songs at full volume, one bassline at a time."),
                        Change("Bass boost and EQ tricks", "Bass boosts on choruses, bass kills that slam back on the drop, mids out, and a muffle that opens up on the downbeat."),
                        Change("Pitch tricks", "Loops that rise in pitch into a drop, tape stops, a half-time bar, and a key lift on the last chorus."),
                        Change("Sets that follow the language", "Each song's language comes from the YT Music chip its playlist was picked from, the playlist's name, its lyrics or its tags, and shows on the song and the set."),
                        Change("Latin and Arabic sounds", "Spanish and Portuguese songs get timbales, cowbell, güira, shaker and samba whistles; Arabic songs get darbuka, claps and the mizmar; Telugu and Tamil songs get whistle calls with their drums. Always on top of the genre's own tricks."),
                        Change("Skip button", "Next to play: the song mixes out at its next phrase, tap again for the next bar. The song's tricks still to come are dropped; the next song and the mix stay. Next buttons on headphones, cars and the notification skip too."),
                        Change("Lyrics ready sooner", "Lyrics are fetched and lined up while each song is analysed, so word-play mixes can happen from the first set. When the lyrics catalogue is busy, FireTheDJ asks again later."),
                        Change("Better word play (phone)", "Words that sound alike or rhyme count too, and the song's title and hook words count most."),
                        Change("Analysis in the background", "Keeps going with the screen off, 4 songs at a time (2 while a set plays). Settings › Analysis turns it off, sets how many songs at once and shows how many have lyrics."),
                    ),
                ),
                Section(
                    "Fixes",
                    listOf(
                        Change("Hi-res FLAC", "Large and 24-bit FLAC libraries no longer crash the app while it reads them."),
                        Change("Full songs from YouTube", "Songs that came through as a 59-second preview are swapped for a full-length upload."),
                        Change("Right lyrics for remixes", "A remix shows its own lyrics, never the original's."),
                        Change("Full titles", "YouTube titles with a dash inside brackets, like \"(Club Mix - Extended Version)\", keep their whole title."),
                    ),
                ),
            ),
        ),
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
