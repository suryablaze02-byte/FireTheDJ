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
            "2.4.1",
            "Settings is back on the TV.",
            listOf(
                Section(
                    "Fixes",
                    listOf(
                        Change("Settings on TV", "Only bangers had pushed the Settings button off the TV's Home screen. It's back, and the song count beside Smooth and Trickster is gone; the crates show theirs."),
                    ),
                ),
            ),
        ),
        Version(
            "2.4.0",
            "Only bangers, Change the vibe mid-set, Play full song and a shorter visualizer panel.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Only bangers", "A switch beside Smooth and Trickster: the set plays its big songs only, no slow ones. On the Mix tab and TV Home."),
                        Change("Change the vibe", "Mid-set, pick an energy (as planned, warm up, peak, wind down or only bangers), the style and the languages. The song playing carries on; every song after it is picked again. Tap the set label on Now Playing, the Vibe card on the decks, or Up next on the TV visualizer."),
                        Change("Play full song", "Let the playing song run to its end before the DJ mixes on. Tap again to undo."),
                        Change("A shorter Look panel", "In the full-screen visualizer the looks and the buttons share one strip, so more of the show is on screen. Stop and Skip sit beside Pause."),
                    ),
                ),
                Section(
                    "Changes",
                    listOf(
                        Change("Cleaner decks", "The crossfader card is gone; whether the beats are locked now shows in Next mix. On TV, Coming up has more room."),
                        Change("Foldables", "An unfolded foldable on its side gets the big-screen layouts."),
                        Change("Clearer switches", "On and off are easy to tell apart in light mode."),
                    ),
                ),
            ),
        ),
        Version(
            "2.3.0",
            "Your listening stats and Wrapped cards, a new Club, and album search.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Your stats", "Hours mixed, sets played, your longest set, favourite crate, genre and language, top artists and most-played songs. Open it from the Music tab, or the bars button on TV. Your history stays on the device, and it starts from this version."),
                        Change("Wrapped cards", "Swipe through cards of your month or all time, and share one or all of them."),
                        Change("Albums and singles", "Search in Stream now finds YouTube Music albums and singles as well as playlists."),
                        Change("Club, redrawn", "You're in the crowd now: an LED wall with side wings, beams through haze, a backlit DJ and a floor that reflects the wall. It goes dark in the breakdowns and lights up on the drops."),
                    ),
                ),
                Section(
                    "Changes",
                    listOf(
                        Change("No more effect names", "The visualizer no longer shows effect names on screen. Effects still play on Club's LED wall."),
                    ),
                ),
            ),
        ),
        Version(
            "2.2.0",
            "Mix from several crates at once, on your phone, TV and Android Auto.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Pick several crates", "On the Mix tab, turn on Pick several and tap the crates you want. The set card adds them up, and the mix moves between those genres."),
                        Change("Mix several on TV", "A new tile after All genres opens a list: tick crates with the remote, then Start. One press on a crate still starts it right away."),
                        Change("Your set in Android Auto", "The crates you last mixed together appear as one row at the top of the list."),
                    ),
                ),
            ),
        ),
        Version(
            "2.1.2",
            "Crates are sorted by size, biggest first.",
            listOf(
                Section(
                    "Changes",
                    listOf(
                        Change("Biggest crates first", "On the Mix tab, crates are listed by how many songs they hold, most first, with All genres still on top. The TV already did this."),
                    ),
                ),
            ),
        ),
        Version(
            "2.1.1",
            "TV fixes: a slim look switcher, smoother effects, and YT Music playlists that scroll.",
            listOf(
                Section(
                    "Changes",
                    listOf(
                        Change("Look switcher on TV", "Press left or right on the remote to change looks; a small pill shows the look's name. OK or down opens a slim bar along the bottom instead of the big panel."),
                        Change("Effects stay on Club's LED wall", "Mix effects like rises, crashes and sweeps now play only on the LED wall behind the DJ in Club. No more screen shakes or full-screen flashes on any look."),
                        Change("Acid Trip and Rabbit Hole are phone-only", "TV graphics chips can't run them smoothly, so the TV app leaves them out. Phones and tablets keep both."),
                    ),
                ),
                Section(
                    "Fixes",
                    listOf(
                        Change("YT Music on TV", "Playlists scroll again, and their titles are no longer cut off under the mood and language filters."),
                        Change("Effect names centred", "In Club, effect names sit in the middle of the screen again."),
                    ),
                ),
            ),
        ),
        Version(
            "2.1.0",
            "New looks: a moonlit Chill Drive, Acid Trip as a breathing fractal, and Rabbit Hole. Calmer effects on every look.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Chill Drive, redrawn", "A coast road at night under a big moon, behind a dark sports coupe. A street lamp passes on every beat, the tail-light strip moves with the music, and an aurora unrolls on the drop."),
                        Change("Acid Trip, redrawn", "A mirrored fractal that keeps unfolding, breathing with the kick and leaving trails, in deep colours that drift slowly. Android 13 and later; older phones keep the previous Acid Trip."),
                        Change("Rabbit Hole", "A new look: drift down a tunnel lined with a living lattice, its walls breathing; on the drop you surge forward. Android 13 and later; older phones show Neon Tunnel."),
                    ),
                ),
                Section(
                    "Changes",
                    listOf(
                        Change("Effect names only in Club", "LED Field no longer shows effect names; Club still does. The other looks show effects as light and motion."),
                        Change("No more fire cannons", "The flame columns from the bottom of the screen are gone from every look."),
                    ),
                ),
                Section(
                    "Fixes",
                    listOf(
                        Change("Go to Mix sideways", "With nothing playing, Now Playing held sideways (on a tablet especially) now shows Go to Mix, as it does upright."),
                        Change("Look panel", "Upright, the LOOK label no longer squeezes into a column when a look's description is long."),
                    ),
                ),
            ),
        ),
        Version(
            "2.0.1",
            "Light mode on the playing screens, the song's colour in both modes, real album covers, and a tab bar that snaps the way you swipe.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Light mode while playing", "Now Playing, the mixing screen and the sideways decks follow Phone / Light / Dark, on phone, tablet and TV."),
                        Change("The song's colour", "Those screens take a soft gradient from the cover: a deep shade in dark mode, a pale tint in light mode."),
                        Change("Real album covers", "Streamed songs show their album's cover instead of a frame from the music video. Covers already saved are looked up again once, a few a minute."),
                        Change("Tabs snap the way you swipe", "Let go part-way along the tab bar or side rail and the glass carries on to the next tab in that direction."),
                    ),
                ),
                Section(
                    "Fixes",
                    listOf(
                        Change("Tab glass bouncing back", "Sliding from Mix to Now Playing, or back, no longer springs the glass to the tab you started on."),
                        Change("TV Home", "This TV & USB and Stream are the same size."),
                    ),
                ),
            ),
        ),
        Version(
            "2.0.0",
            "A redesign: crates with covers, a set card with Start, a mini player, live effects, looks you pick by picture, and real tablet layouts.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Your set", "Mix opens on a card for the crate you pick: its songs, hours and tempo range, Smooth or Trickster with a line on what it does, and Start."),
                        Change("Crates with covers", "Each crate shows a fan of covers from its songs and glows in their colour, on phone, tablet and TV."),
                        Change("Slide between tabs", "Press and slide along the tab bar, or up and down the side rail: the glass follows your finger and opens the tab you let go on."),
                        Change("Mini player", "While a set plays, Mix and Music show it above the tab bar: the song, what's next, and pause. Tap it for Now Playing."),
                        Change("Effects, live", "Sideways and on TV, the Effects box shows the effect playing now with the beats it has left, what's next, and every effect in the mix. Tap it for all of them."),
                        Change("Pick a look by its picture", "The visualizer's looks are tiles with a picture of each; on TV they're a shelf in the player bar. Stop and Skip are there too."),
                        Change("Tablets", "Upright: three columns of crates, a wide set card, the record big on Now Playing with what's coming up. Sideways: Music in two panes."),
                        Change("Adding music", "This phone opens the picker right in Music or Download. Paste a link is a sheet that adds a copied link in one tap. Stream shows YT Music, Spotify and Apple Music as tabs."),
                        Change("Settings", "Grouped with icons, Phone / Light / Dark in one choice, and stem separation in its own group. On TV, Settings is a side panel."),
                        Change("TV Home", "The set that's playing sits across the top, with what's next; OK opens the decks."),
                        Change("A new tour", "Back as well as Next, a step count, a ring that glides to each part, and pictures for the welcome and the looks."),
                    ),
                ),
            ),
        ),
        Version(
            "1.9.2",
            "Faster stem separation, with exactly the same stems.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Faster stem separation", "The stem model now stays loaded between songs instead of loading again for each one, so stems are ready 10–20% sooner and fewer mixes fall back to EQ. The stems are exactly the same as before. It lets the model go after 5 minutes without work."),
                    ),
                ),
            ),
        ),
        Version(
            "1.9.1",
            "Fixes for the TV's Settings.",
            listOf(
                Section(
                    "Fixes",
                    listOf(
                        Change("TV Settings fits the screen", "The Settings window no longer runs off the top and bottom of the TV, and the Done button shows again."),
                        Change("Light mode shows what's on screen", "While following the TV, the Light mode card now says on or off to match the TV's setting."),
                        Change("TV update check", "Opening Settings on the TV checks for updates instead of staying on \"Checking for updates…\"."),
                    ),
                ),
            ),
        ),
        Version(
            "1.9.0",
            "A light mode with liquid glass, a true dark mode, and FireTheDJ following your phone's light or dark setting.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Light mode", "A soft grey background with dark text, like Apple Music. Layouts, cards and the orange stay as they were."),
                        Change("A true dark mode", "The dark background is now near-black instead of plum."),
                        Change("Liquid glass", "Bars, buttons and the side rail are clear glass with a bright rim on the lit edge, and the floating ones lift off the background."),
                        Change("Match phone", "Settings > Appearance follows your phone's light or dark setting by default. Turn Match phone off to pick Light mode yourself. The TV has the same switches."),
                        Change("Now Playing keeps the song's colour", "In either mode, Now Playing and the decks stay in the playing song's colour, and the visualizers look exactly as before."),
                        Change("A tidier side rail", "Sideways, the visualizer button shows on Now Playing only."),
                    ),
                ),
            ),
        ),
        Version(
            "1.8.0",
            "FireTheDJ mixes like a club DJ: long blends by genre, a set that builds to a peak, new mixes and effects, and layouts for tablets.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Blends as long as the genre", "Techno blends over 64 bars, house and trance over 32 with the basslines swapping on the 16th, pop and Latin over 16, drum & bass, dubstep and hip-hop short and on the phrase. Trickster keeps them half as long."),
                        Change("EQ in every blend", "Only one song's voice and melody lead at a time, and the old song's highs fade out once the basslines have swapped."),
                        Change("A set with a shape", "It opens with one of your calmer songs, builds, peaks past the middle, gives the floor a breather, and ends on a big song kept back for the finale. Early evening gets a gentler peak; the small hours wind down."),
                        Change("Skips change direction", "Skip three songs in a row and the set heads somewhere else: another genre or another energy."),
                        Change("Four new mixes", "Reverb wash (the old song melts into a long reverb), loop mix (a song that fades out loops its last full bars so the blend still grooves), a cappella mashup (the new song's voice alone over the old one's music) and power cut (a dead stop, a beat of silence, then the drop)."),
                        Change("Effect combos in Trickster", "Riser swell, Chaos, Swirl and Afro echo: several effects at once, each in the genres it belongs to, never over the singing."),
                        Change("A swell in Smooth", "Each song gets one gentle riser or filter swell into its main drop, where nobody is singing. Nothing that stops the music."),
                        Change("Voices in tune", "Two songs that are both singing are never blended in clashing keys."),
                        Change("Landscape that adapts", "Sideways, the rail spreads Mix, Music and Now Playing evenly, and Settings sits at its foot on the Mix tab. On a tablet the layout grows: a wider Mix with a set summary and bigger crates, and Now Playing with bigger records, the mix plan, the songs coming up and how far into the set you are. The TV shows the same."),
                    ),
                ),
            ),
        ),
        Version(
            "1.7.1",
            "Apple Music playlists, and real covers on the Spotify and Apple Music cards.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Apple Music playlists", "Stream now has Apple Music beside YT Music and Spotify: pick one of Apple Music's own playlists (Telugu, Tamil, Bollywood, Punjabi, party, chill and more) or paste any public Apple Music playlist link. Its songs are found on YouTube Music and played from there, up to 100 a playlist. No Apple account needed."),
                        Change("Real playlist covers", "Spotify and Apple Music cards show each playlist's own cover, with its name underneath. Covers are kept on your phone, so they load once."),
                    ),
                ),
            ),
        ),
        Version(
            "1.7.0",
            "A new look: frosted glass, one flat orange, and Now Playing in the colour of each song.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Frosted glass", "Panels, buttons and the tab bar are frosted glass floating over one dark background colour, with no gradients. Lists scroll under the tab bar."),
                        Change("Now Playing in the song's colour", "The player and the decks take a deep shade of each song's cover, fading from song to song."),
                        Change("One orange", "A single flat orange for what you press, with black text on it so it's easy to read. The app icon and logo are flat orange too."),
                        Change("Spinning decks", "Sideways and on TV, both decks spin glass records with their spectrum round them, and the effects box between them shouts each effect as the Club look does."),
                        Change("Easier to read", "Cleaner song titles without \"Full Video Song\" and the like, larger small print, and cards that grow with large text."),
                        Change("Small things", "Songs of unclear genre are a note on Mix instead of a crate, Now Playing offers Go to Mix when nothing plays, and records hold still when Android's Remove animations is on."),
                    ),
                ),
            ),
        ),
        Version(
            "1.6.2",
            "Support FireTheDJ on Ko-fi or over UPI.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Ko-fi or UPI", "Support FireTheDJ in Settings now asks how: Ko-fi opens the Ko-fi page, from anywhere, and UPI pays from ₹50 with the UPI app of your choice. On TV, pick either and scan the code with your phone."),
                    ),
                ),
            ),
        ),
        Version(
            "1.6.1",
            "Two new visualizers: Chill Drive and Lo-fi Vibe.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Chill Drive", "A red convertible, top down, cruising a synthwave highway at night. Fireworks burst on every beat, more on the downbeat and a volley on the drop, and the tail lights are little spectrum meters."),
                        Change("Lo-fi Vibe", "A city rooftop at dusk. The skyline is an equaliser, each building lighting up floor by floor with the music, while the string lights glow and the boombox pumps with the bass."),
                        Change("Picking a look", "The look picker shows four to a row on the phone. On TV, the new looks come after Club with ◀ ▶, and cast screens show them too."),
                        Change("Karaoke removed", "Beat Pop is now the lyrics look. If Karaoke was your pick, the visualizer opens on Club."),
                    ),
                ),
            ),
        ),
        Version(
            "1.6.0",
            "Stem separation on your phone: bass swaps that keep the kick, and clean a cappella vocal swaps.",
            listOf(
                Section(
                    "What's new",
                    listOf(
                        Change("Stem separation (phone)", "Turn it on in Settings › Analysis: a one-time 163 MB download. During a set, FireTheDJ splits the bit of each song a mix plays over into drums, bass, vocals and the rest, in the background, then mixes the real parts."),
                        Change("Real bass swaps", "When the bass hands over from one song to the next, only the bassline swaps: the kick keeps driving instead of going thin with it."),
                        Change("Clean vocal swaps", "Vocal swaps take the singer out cleanly, or keep only the voice for a true a cappella over the other song."),
                        Change("Stems for tricks", "In Trickster, bass-kill drops drop just the bass, and word loops repeat the sung word alone. On by default with stems; turn it off to save battery."),
                        Change("Always a fallback", "Anything not separated in time uses the EQ moves as before, so a mix never waits and never changes sound halfway through. Needs a 64-bit phone with 6 GB of memory or more."),
                        Change("A tour of what's new", "After an update, a short guided tour shows what changed since your last one, on phone and TV. New users get the whole app, Stream and stem separation included."),
                    ),
                ),
            ),
        ),
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
