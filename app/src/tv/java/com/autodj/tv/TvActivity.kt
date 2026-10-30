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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Dj.Accent, background = Dj.Bg, surface = Dj.Surface)) {
                // Splash on every launch (not on recreation), then the tour once ever.
                var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                var tour by remember { mutableStateOf(!TourMemory.done(this)) }
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
    TourStep(null, "Welcome to FireTheDJ TV", "Your AI DJ on the big screen. Use the remote: arrows to move, OK to choose. Here's a quick tour."),
    TourStep("tv_add", "Add music", "Scan this TV and any USB drive, or stream YT Music and Spotify playlists. Each song is analysed once, then sorted into crates."),
    TourStep("tv_crates", "Start a mix", "Press a crate to start: All genres, or one style. Songs still being analysed join the mix as soon as they're ready."),
    TourStep("tv_mode", "Smooth or Trickster", "Up here: Smooth blends long and clean. Trickster adds scratches, rolls, echo throws and air horns."),
    TourStep("tv_settings", "Settings", "Clear the library from here."),
    TourStep(null, "During the mix", "Now Playing shows both decks, the mix between them and the effects as they play. The visualizer button takes the screen over with the look you pick; ◀ ▶ change it, and Back returns to the decks."),
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
        Page.DECKS -> TvDecks(vm, onHome = { page = Page.HOME }, onVisualizer = { page = Page.PLAYER })
        Page.PLAYER -> TvPlayer(vm, onHome = { page = Page.HOME }, onBack = { page = Page.DECKS })
        Page.YT_MUSIC -> {
            val folders by vm.folders.collectAsStateWithLifecycle()
            val importState by vm.importState.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                StreamScreen(folders, importState, vm::importPlaylist, vm::resetImport, onBack = { page = Page.HOME })
            }
        }
    }
}
