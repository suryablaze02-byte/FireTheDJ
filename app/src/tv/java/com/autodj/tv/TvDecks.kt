package com.autodj.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.ui.AmbientLight
import com.autodj.ui.DjViewModel
import com.autodj.ui.MixingScreenLandscape
import com.autodj.ui.NowPlayingBackdrop
import com.autodj.ui.TvDeckButtons
import com.autodj.ui.TwoDeckScreen

/**
 * Now Playing on the TV: both decks, the mix between them and the controls, as on a phone held
 * sideways. The remote starts on the visualizer button; OK on the effects panel opens the full
 * effects view, and Back from it returns here. Back from here goes home; the mix plays on.
 */
@UnstableApi
@Composable
fun TvDecks(vm: DjViewModel, onHome: () -> Unit, onVisualizer: () -> Unit) {
    val state by vm.engine.state.collectAsStateWithLifecycle()
    val artwork by vm.artwork.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var effects by rememberSaveable { mutableStateOf(false) }
    val buttons = remember { TvDeckButtons(onHome, onVisualizer) }
    KeepScreenOn()

    Box(Modifier.fillMaxSize()) {
        AmbientLight(Modifier.fillMaxSize())
        NowPlayingBackdrop(state, artwork)
        // Inside the TV's overscan margin.
        Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
            if (effects) {
                BackHandler { effects = false }
                MixingScreenLandscape(state, onBack = { effects = false })
            } else {
                BackHandler(onBack = onHome)
                TwoDeckScreen(
                    state, artwork, visual = vm.engine::visual,
                    onToggle = vm::togglePause, onStop = vm::stop, onSkip = vm::skip,
                    onOpenEffects = { effects = true },
                    languageOf = { uri -> library.firstOrNull { it.uri == uri }?.language },
                    tv = buttons,
                )
                // Each time the decks show (first, or back from the effects view), the remote starts on the visualizer button.
                LaunchedEffect(Unit) { runCatching { buttons.visualizerFocus.requestFocus() } }
            }
        }
    }
}
