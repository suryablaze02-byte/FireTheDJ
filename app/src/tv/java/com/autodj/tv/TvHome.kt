package com.autodj.tv

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.unit.Dp
import com.autodj.update.Updates
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.analysis.Genres
import com.autodj.dj.MixMode
import com.autodj.ui.ALL
import com.autodj.ui.AmbientLight
import com.autodj.ui.AnalysisStatus
import com.autodj.ui.Dj
import com.autodj.ui.DjViewModel
import com.autodj.ui.FireTheDjWordmark
import com.autodj.ui.Glass
import com.autodj.ui.GlassSegmented
import com.autodj.ui.SourceGlyph
import com.autodj.ui.SourceKind
import com.autodj.ui.glass
import com.autodj.ui.songs
import com.autodj.ui.tourTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The TV home screen, all in reach of the D-pad: the mode and settings along the top, the playing
 * mix (if any), a row of genre crates that start a mix when pressed, and where music comes from.
 */
@UnstableApi
@Composable
fun TvHome(vm: DjViewModel, onWatch: () -> Unit, onYtMusic: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by vm.library.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val state by vm.engine.state.collectAsStateWithLifecycle()
    var scanNote by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    var support by remember { mutableStateOf(false) }
    val updateState by Updates.state.collectAsStateWithLifecycle()

    if (settings) {
        TvSettingsDialog(
            updateState, library.size,
            onSupport = { settings = false; support = true },
            onClear = {
                settings = false
                if (library.isNotEmpty()) confirmClear = true
            },
            onDismiss = { settings = false },
        )
    }
    if (support) TvSupportDialog(onDismiss = { support = false })
    if (confirmClear) {
        GlassDialog(
            "Clear the library?",
            "Removes all ${songs(library.size)}, their analysis and artwork, and stops any mix. " +
                "Music files on the TV and USB drives are not touched.",
            onDismiss = { confirmClear = false },
        ) { first ->
            TvCard("Cancel", null, Modifier.width(200.dp).focusRequester(first)) { confirmClear = false }
            TvCard("Clear library", null, Modifier.width(240.dp), titleColor = Dj.Warn) {
                confirmClear = false
                vm.clearLibrary()
                scanNote = "Library cleared."
            }
        }
    }

    val ready = library.mapNotNull { it.info }.filter { it.genreFamily != Genres.UNCLASSIFIED }
    val analysing = library.count { it.status == AnalysisStatus.QUEUED || it.status == AnalysisStatus.ANALYZING }
    val crates = buildList {
        if (ready.isNotEmpty()) add(ALL to ready.size)
        ready.groupingBy { it.genreFamily }.eachCount().entries.sortedByDescending { it.value }.forEach { add(it.key to it.value) }
    }

    val scan = {
        scope.launch {
            scanNote = "Looking for music on this TV…"
            val found = withContext(Dispatchers.IO) { runCatching { DeviceMusic.scan(context) }.getOrDefault(emptyList()) }
            val added = vm.addDeviceTracks(found)
            scanNote = when {
                found.isEmpty() -> "No music found. Copy songs to the TV or plug in a USB drive, then scan again."
                added == 0 -> "Up to date: all ${songs(found.size)} on this TV are already in the library."
                else -> "Added ${songs(added)}. Each is analysed once before it can be mixed."
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else scanNote = "FireTheDJ needs permission to read music on this TV."
    }
    val scanWithPermission = {
        if (ContextCompat.checkSelfPermission(context, DeviceMusic.permission) == PackageManager.PERMISSION_GRANTED) scan()
        else permission.launch(DeviceMusic.permission)
    }

    // The remote starts on the first thing worth pressing.
    val first = remember { FocusRequester() }
    LaunchedEffect(crates.isNotEmpty(), state.running) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        AmbientLight(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp - EDGE, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(horizontal = EDGE)) {
                FireTheDjWordmark(32.sp)
                Spacer(Modifier.weight(1f))
                GlassSegmented(
                    MixMode.entries, mode, { it.label }, vm::setMode,
                    Modifier.width(260.dp).tourTarget("tv_mode"), height = 40.dp, fontSize = 16.sp,
                )
                Text(
                    buildString {
                        append(songs(library.size))
                        if (analysing > 0) append(" · $analysing analysing")
                    },
                    color = Glass.Muted, fontSize = 15.sp,
                )
                TvRoundButton(40.dp, Glass.Fill, "Settings", Modifier.tourTarget("tv_settings"), onClick = { settings = true }) {
                    Icon(Icons.Filled.Settings, null, tint = Glass.Text, modifier = Modifier.size(22.dp))
                }
            }

            if (state.running) {
                val deck = state.current
                TvCard(
                    title = "Now playing · ${deck?.title.orEmpty()}",
                    subtitle = listOfNotNull(deck?.artist?.takeIf { it.isNotBlank() }, state.crate, "Press OK to watch the visualizer").joinToString(" · "),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = 8.dp).focusRequester(first),
                    selected = true,
                    onClick = onWatch,
                )
            }

            Column(Modifier.tourTarget("tv_crates"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Start a mix", color = Glass.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
                    modifier = Modifier.padding(start = EDGE, top = 6.dp))
                if (crates.isEmpty()) {
                    Text(
                        if (library.isEmpty()) "Add music below to start: songs on this TV or a USB drive, or YT Music playlists."
                        else "Analysing your songs. Crates appear here as soon as tracks are ready.",
                        color = Glass.Muted, fontSize = 16.sp, modifier = Modifier.padding(start = EDGE, top = 4.dp, bottom = 12.dp),
                    )
                } else {
                    Text("Press a crate. FireTheDJ picks every song from there.", color = Glass.Muted, fontSize = 16.sp, modifier = Modifier.padding(start = EDGE))
                    // Room around every card for it to grow and ring when the remote lands on it.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 16.dp, horizontal = EDGE)) {
                        items(crates, key = { it.first }) { (crate, count) ->
                            val focus = if (!state.running && crate == crates.first().first) Modifier.focusRequester(first) else Modifier
                            TvCard(
                                title = if (crate == ALL) "All genres" else crate,
                                subtitle = if (crate == ALL) "${songs(count)} · across genres" else songs(count),
                                modifier = Modifier.width(230.dp).height(116.dp).then(focus),
                            ) { vm.startSet(crate) }
                        }
                    }
                }
            }

            Column(Modifier.tourTarget("tv_add"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Add music", color = Glass.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = EDGE))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(horizontal = EDGE, vertical = 12.dp)) {
                    TvCard("This TV & USB", "Scan for songs", Modifier.width(300.dp), accent = SourceKind.PHONE.color,
                        icon = { SourceGlyph(SourceKind.PHONE, 30.dp) }) { scanWithPermission() }
                    TvCard("YT Music", "Playlists by mood and genre", Modifier.width(300.dp), accent = SourceKind.YOUTUBE.color,
                        icon = { SourceGlyph(SourceKind.YOUTUBE, 30.dp) }, onClick = onYtMusic)
                }
                scanNote?.let { Text(it, color = Glass.EmberText, fontSize = 15.sp, modifier = Modifier.padding(start = EDGE)) }
            }
        }
    }
}

/** A glass dialog for the remote; [buttons] get a requester for the one that should take focus first. */
@Composable
private fun GlassDialog(title: String, body: String? = null, onDismiss: () -> Unit, buttons: @Composable (FocusRequester) -> Unit) {
    TvGlassDialog(onDismiss) { first ->
        Text(title, color = Glass.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = EDGE))
        body?.let { Text(it, color = Glass.Muted, fontSize = 16.sp, modifier = Modifier.padding(start = EDGE, top = 12.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 20.dp, start = EDGE, end = EDGE, bottom = 8.dp)) {
            buttons(first)
        }
    }
}

/**
 * A glass dialog for the remote, [width] wide; [content] gets a requester for whatever should
 * take focus first.
 */
@Composable
fun TvGlassDialog(onDismiss: () -> Unit, width: Dp = 620.dp, content: @Composable ColumnScope.(FocusRequester) -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width).background(Color(0xFF241A20), RoundedCornerShape(30.dp)).glass(RoundedCornerShape(30.dp), Glass.Fill).padding(28.dp),
        ) { content(first) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Space beside cards in a row, so a focused card can grow and ring without being clipped. */
val TV_EDGE = 20.dp
private val EDGE = TV_EDGE
