package com.autodj.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.ui.Dj
import com.autodj.ui.DjViewModel
import com.autodj.ui.StreamScreen
import com.autodj.ui.LocalTour
import com.autodj.ui.SplashScreen
import com.autodj.ui.TourMemory
import com.autodj.ui.whatsNew
import com.autodj.BuildConfig
import com.autodj.ui.TourOverlay
import com.autodj.ui.TourState
import com.autodj.ui.TourStep
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/** FireTheDJ TV: the home screen, Stream, and while a mix plays the decks and the full-screen visualizer. */
@UnstableApi
class TvActivity : ComponentActivity() {
    private val vm: DjViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.autodj.ui.Appearance.load(this)
        setContent {
            val dark = !com.autodj.ui.Appearance.isLight()
            com.autodj.ui.GlassTheme(dark = dark) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Dj.Accent, background = Dj.Bg, surface = Dj.Surface) else androidx.compose.material3.lightColorScheme(primary = Dj.Accent, background = Dj.Bg, surface = Dj.Surface)) {
                // Splash on every launch (not on recreation), then the tour once ever; after an
                // update, a short tour of what's new since the last one this TV saw.
                var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                var tour by remember { mutableStateOf(!TourMemory.done(this)) }
                val news = remember { if (TourMemory.done(this)) whatsNew(TV_WHATS_NEW, TourMemory.seenVersion(this)) else emptyList() }
                var showNews by remember { mutableStateOf(news.isNotEmpty()) }
                val tourState = remember { TourState() }
                CompositionLocalProvider(LocalTour provides tourState) {
                    Box(Modifier.fillMaxSize().background(Dj.Bg)) {
                        TvApp(vm)
                        // Updates are looked for once a day; Settings offers them.
                        LaunchedEffect(Unit) { com.autodj.update.Updates.autoCheck(this@TvActivity) }
                        if (splash) {
                            SplashScreen(tv = true) { splash = false }
                        } else if (tour) {
                            TourOverlay(tourState, TV_TOUR, tv = true) {
                                tour = false
                                TourMemory.markDone(this@TvActivity)
                                TourMemory.markSeen(this@TvActivity, BuildConfig.VERSION_NAME)
                            }
                        } else if (showNews) {
                            TourOverlay(tourState, news, tv = true) {
                                showNews = false
                                TourMemory.markSeen(this@TvActivity, BuildConfig.VERSION_NAME)
                            }
                        }
                    }
                }
            }
            }
        }
    }

    /** Back from Android's "Install unknown apps" screen: an update waiting on it starts downloading. */
    override fun onResume() {
        super.onResume()
        com.autodj.update.Updates.resume(this)
    }
}

/** The TV app's first-launch tour, over the home screen, driven by the remote. */
private val TV_TOUR = listOf(
    TourStep(null, "Welcome to FireTheDJ TV", "Your AI DJ on the big screen. Use the remote: arrows to move, OK to choose. Here's a quick tour.", art = com.autodj.ui.TourArt.WELCOME),
    TourStep("tv_add", "Add music", "Scan this TV and any USB drive, or stream YT Music, Spotify and Apple Music playlists. Each song is analysed once, then sorted into crates."),
    TourStep("tv_crates", "Start a mix", "Press a crate to start: All genres, or one style. Songs still being analysed join the mix as soon as they're ready."),
    TourStep("tv_mode", "Smooth or Trickster", "Up here: Smooth blends long and clean. Trickster adds scratches, rolls, echo throws and air horns."),
    TourStep("tv_settings", "Settings", "Updates and what's new, supporting FireTheDJ, and clearing the library."),
    TourStep(null, "During the mix", "Home shows the set playing at the top. Now Playing shows both decks, the mix between them, the effects as they play and what's coming up. Back returns home; the music plays on."),
    TourStep(null, "Pick a look", "The visualizer button fills the screen. Press OK while it plays, then Up to the looks, and pick one; ◀ ▶ also step through them.", art = com.autodj.ui.TourArt.LOOKS),
)

/** What's new on the TV since 1.3, oldest first: each step shows once, to TVs that had an earlier version. */
private val TV_WHATS_NEW = listOf(
    TourStep(null, "Lyrics on the big screen", "Beat Pop puts the song's words up in time with the singing: ◀ ▶ while the visualizer plays.", since = "1.3.0"),
    TourStep("tv_mode", "Trickster, rebuilt", "A trick about every 30 seconds: bass kills that slam back on the drop, rising rolls, tape stops, key lifts, and sounds that follow each song's language.", since = "1.4.0"),
    TourStep(null, "Acid Trip", "A new look: a melting kaleidoscope. Effect names now show only on Club and LED Field.", since = "1.5.0"),
    TourStep("tv_add", "Stream", "YT Music and Spotify playlists, with one search across both. Spotify songs play from YouTube, and talking intros and skits are skipped.", since = "1.5.1"),
    TourStep(null, "Two decks", "Now Playing shows both decks, the mix and the effects as they play. The visualizer button takes over the screen; Back returns to the decks.", since = "1.5.1"),
    TourStep(null, "Two new looks", "Chill Drive cruises a neon highway with fireworks on the beat, and Lo-fi Vibe's rooftop city bounces along: ◀ ▶ while the visualizer plays. Karaoke has gone; Beat Pop shows the lyrics.", since = "1.6.1"),
    TourStep(null, "Now playing on Home", "Home shows the set that's playing, with what's next; OK opens the decks. Crates show a few of their covers.", since = "2.0.0"),
    TourStep(null, "Effects and looks", "On the decks, the Effects box shows the effect playing now and every effect in the mix, with what's coming up. In the visualizer, press OK, then Up, and pick a look by its picture.", since = "2.0.0", art = com.autodj.ui.TourArt.LOOKS),
)

private enum class Page { HOME, DECKS, PLAYER, YT_MUSIC }

@UnstableApi
@Composable
private fun TvApp(vm: DjViewModel) {
    val state by vm.engine.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(if (state.running) Page.DECKS else Page.HOME) }
    // A mix starting goes straight to the decks; a mix ending comes back home.
    LaunchedEffect(state.running) {
        if (state.running) page = Page.DECKS else if (page == Page.DECKS || page == Page.PLAYER) page = Page.HOME
    }
    when (page) {
        Page.HOME -> TvHome(vm, onWatch = { page = Page.DECKS }, onYtMusic = { page = Page.YT_MUSIC })
        // Back walks home one step at a time: visualizer → decks → home.
        // The decks sit in the song's colour and the player over the visualizer: dark glass in either mode.
        Page.DECKS -> TvDecks(vm, onHome = { page = Page.HOME }, onVisualizer = { page = Page.PLAYER })
        Page.PLAYER -> com.autodj.ui.DarkGlass { TvPlayer(vm, onHome = { page = Page.HOME }, onBack = { page = Page.DECKS }) }
        Page.YT_MUSIC -> {
            val folders by vm.folders.collectAsStateWithLifecycle()
            val importState by vm.importState.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                StreamScreen(folders, importState, vm::importPlaylist, vm::resetImport, onBack = { page = Page.HOME })
            }
        }
    }
}
