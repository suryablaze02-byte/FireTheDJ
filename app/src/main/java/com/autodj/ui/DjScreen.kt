package com.autodj.ui

import android.Manifest
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.cast.CastControl
import com.autodj.update.UpdateState
import com.autodj.update.Updates
import com.autodj.cast.TvPresentation

@UnstableApi
@Composable
fun DjScreen(vm: DjViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val setLanguage by vm.setLanguage.collectAsStateWithLifecycle()
    val artwork by vm.artwork.collectAsStateWithLifecycle()
    val state by vm.engine.state.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (state.running) Tab.NOW_PLAYING else Tab.MIX) }
    // The mixing view opens by itself for each transition; Back hides it until the next one.
    var preview by remember { mutableStateOf(false) }
    var hiddenFor by remember { mutableStateOf(-1) }
    LaunchedEffect(state.running) {
        if (state.running) tab = Tab.NOW_PLAYING
        // A new set counts transitions from 0 again: forget what was hidden in the last one.
        hiddenFor = -1
        preview = false
    }
    // Full-screen visualizer: closes by itself when the set ends.
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.running) { if (!state.running) fullscreen = false }
    // A preview opened from "Up next" ends with that transition.
    LaunchedEffect(state.index) { preview = false }

    // The Stream page (YT Music or Spotify) opens over the Library, from its tile.
    var ytMusic by rememberSaveable { mutableStateOf(false) }

    // Casting: a wireless-display TV shows the visualizer; a Chromecast gets the mix streamed.
    var castDialog by remember { mutableStateOf(false) }
    val visualizerStyle by vm.visualizerStyle.collectAsStateWithLifecycle()
    TvPresentation(vm.engine, vm.visualizerStyle)
    CastCapturePrompts(artworkFor = { uri -> vm.artwork.value[uri] })
    LaunchedEffect(visualizerStyle) { CastControl.style = visualizerStyle }
    if (castDialog) CastDialog(onClose = { castDialog = false })
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::addFolder) }
    val folders by vm.folders.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    var pendingStart by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The playback notification needs this on Android 13+; the mix plays either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStart?.let { (crate, source) -> vm.startSet(crate, source) }
        pendingStart = null
    }
    val context = LocalContext.current
    val start = { crate: String, source: String ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = crate to source
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startSet(crate, source)
        }
    }

    if (fullscreen && state.running) {
        val style by vm.visualizerStyle.collectAsStateWithLifecycle()
        FullscreenVisualizer(
            state, artwork, vm.engine::visual, onToggle = vm::togglePause, onClose = { fullscreen = false },
            style = style, onStyle = vm::setVisualizerStyle,
        )
        return
    }

    // What the Mix tab has picked; kept here so it survives switching tabs.
    var pickedSource by rememberSaveable { mutableStateOf(ALL_SOURCES) }
    var pickedCrate by rememberSaveable { mutableStateOf<String?>(null) }

    // Settings (updates, support) opens over the tabs. Updates are looked for once a day on launch.
    var settings by rememberSaveable { mutableStateOf(false) }
    val updateState by Updates.state.collectAsStateWithLifecycle()
    val update = (updateState as? UpdateState.Available)?.release
    var dismissed by remember { mutableStateOf(Updates.dismissed(context)) }
    LaunchedEffect(Unit) { Updates.autoCheck(context) }
    if (settings) {
        Box(Modifier.fillMaxSize()) {
            AmbientLight(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().safeDrawingPadding()) { SettingsScreen(onBack = { settings = false }) }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Light behind the glass; Now Playing lays the track's own art over it.
        AmbientLight(Modifier.fillMaxSize())
        if (tab == Tab.NOW_PLAYING) NowPlayingBackdrop(state, artwork)
        // Sideways, the tabs move to a rail down the left and Now Playing shows both decks.
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val tabs = Tab.entries.map { GlassTab(it.label, it.tourKey, it.icon) }
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (landscape) {
                GlassTabRail(
                    tabs, tab.ordinal, onSelect = { tab = Tab.entries[it] }, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    // Cast, then the visualizer (while a set plays): from any tab.
                    CastButton(rememberCasting(), diameter = 44.dp, onClick = { castDialog = true })
                    if (state.running) GlassIconButton("Open the full-screen visualizer", onClick = { fullscreen = true }) { ExpandGlyph(Glass.Text) }
                }
            }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.MIX -> MixScreen(
                        library, folders,
                        mode = mode,
                        onMode = vm::setMode,
                        pickedSource = pickedSource,
                        onPickSource = { pickedSource = it },
                        pickedCrate = pickedCrate,
                        onPickCrate = { pickedCrate = it },
                        onCast = { castDialog = true },
                        onAddMusic = { tab = Tab.MUSIC },
                        onStart = start,
                        onSettings = { settings = true },
                        update = update,
                        showBanner = update != null && update.version != dismissed,
                        onDismissUpdate = {
                            update?.let { Updates.dismiss(context, it.version) }
                            dismissed = update?.version
                        },
                    )
                    Tab.MUSIC -> if (ytMusic) {
                        StreamScreen(folders, importState, onImport = vm::importPlaylist, onImportClosed = vm::resetImport, onBack = { ytMusic = false })
                    } else MusicScreen(
                        library, artwork,
                        folders = folders,
                        onAddFolder = { folderPicker.launch(null) },
                        onOpenYtMusic = { ytMusic = true },
                        importState = importState,
                        onImport = { vm.importPlaylist(it) },
                        onImportClosed = vm::resetImport,
                        onRemoveFolder = { uri ->
                            if (pickedSource == uri) pickedSource = ALL_SOURCES
                            vm.removeFolder(uri)
                        },
                        onClear = {
                            pickedSource = ALL_SOURCES
                            vm.clearLibrary()
                        },
                        onRemove = vm::remove,
                    )
                    Tab.NOW_PLAYING -> {
                        val showMix = state.running && (preview || (state.mixing && hiddenFor != state.index))
                        if (landscape && state.running && preview) {
                            // Sideways the decks are always on screen; the effects view opens only from its panel.
                            MixingScreenLandscape(state, onBack = { preview = false })
                        } else if (landscape) {
                            TwoDeckScreen(
                                state, artwork, visual = vm.engine::visual, onToggle = vm::togglePause, onStop = vm::stop, onSkip = vm::skip,
                                onOpenEffects = { preview = true },
                                languageOf = { uri -> library.firstOrNull { it.uri == uri }?.language },
                            )
                        } else if (showMix) {
                            MixingScreen(state, artwork, visual = vm.engine::visual, onBack = {
                                preview = false
                                hiddenFor = state.index
                            })
                        } else {
                            NowPlayingScreen(state, artwork, onToggle = vm::togglePause, onStop = vm::stop, onOpenMix = { preview = true }, onSkip = vm::skip, visual = vm.engine::visual,
                                onFullscreen = { fullscreen = true },
                                onCast = { castDialog = true },
                                setLanguage = setLanguage,
                                languageOf = { uri -> library.firstOrNull { it.uri == uri }?.language },
                            )
                        }
                    }
                }
            }
            if (!landscape) {
                GlassTabBar(
                    tabs,
                    tab.ordinal,
                    onSelect = { tab = Tab.entries[it] },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                )
            }
        }
        }
    }
}

/** The tabs; [tourKey] is what the guided tour spotlights. */
private enum class Tab(val label: String, val tourKey: String?, val icon: @Composable (Color) -> Unit) {
    MIX("Mix", null, { FlameGlyph(it) }),
    MUSIC("Music", "tab_music", { NoteGlyph(it) }),
    NOW_PLAYING("Now Playing", "tab_now", { BarsGlyph(it) }),
}
