package com.autodj.ui

import android.Manifest
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.cast.CastControl
import com.autodj.cast.TvPresentation

@UnstableApi
@Composable
fun DjScreen(vm: DjViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val artwork by vm.artwork.collectAsStateWithLifecycle()
    val state by vm.engine.state.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (state.running) Tab.NOW_PLAYING else Tab.LIBRARY) }
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

    // The YT Music page opens over the Library, from its tile.
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

    Column(Modifier.fillMaxSize().background(Dj.Bg).safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.LIBRARY -> if (ytMusic) {
                    YtMusicScreen(folders, importState, onImport = vm::importPlaylist, onImportClosed = vm::resetImport, onBack = { ytMusic = false })
                } else LibraryScreen(
                    library, artwork,
                    mode = mode,
                    onMode = vm::setMode,
                    folders = folders,
                    onAddFolder = { folderPicker.launch(null) },
                    onOpenYtMusic = { ytMusic = true },
                    onCast = { castDialog = true },
                    importState = importState,
                    onImport = vm::importPlaylist,
                    onImportClosed = vm::resetImport,
                    onRemoveFolder = vm::removeFolder,
                    onClear = vm::clearLibrary,
                    onStart = start,
                    onRemove = vm::remove,
                )
                Tab.NOW_PLAYING -> {
                    val showMix = state.running && (preview || (state.mixing && hiddenFor != state.index))
                    if (showMix) {
                        MixingScreen(state, artwork, visual = vm.engine::visual, onBack = {
                            preview = false
                            hiddenFor = state.index
                        })
                    } else {
                        NowPlayingScreen(state, artwork, onToggle = vm::togglePause, onStop = vm::stop, onOpenMix = { preview = true }, visual = vm.engine::visual,
                            onFullscreen = { fullscreen = true },
                            onCast = { castDialog = true },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Dj.Divider))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            Tab.entries.forEach { t ->
                TextButton(onClick = { tab = t }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        t.label,
                        fontSize = 14.sp,
                        color = if (t == tab) Dj.Accent else Dj.Muted,
                        fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private enum class Tab(val label: String) { LIBRARY("Library"), NOW_PLAYING("Now Playing") }
