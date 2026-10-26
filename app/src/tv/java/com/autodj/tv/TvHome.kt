package com.autodj.tv

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.autodj.ui.AnalysisStatus
import com.autodj.ui.Dj
import com.autodj.ui.DjViewModel
import com.autodj.ui.FireTheDjWordmark
import com.autodj.ui.tourTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The TV home screen, all in reach of the D-pad: the playing mix (if any), a row of genre crates
 * that start a mix when pressed, the mixing mode, and where music comes from.
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
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        val cancel = remember { FocusRequester() }
        Dialog(onDismissRequest = { confirmClear = false }) {
            Column(
                Modifier.background(Dj.Surface, RoundedCornerShape(24.dp)).padding(32.dp).width(560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Clear the library?", color = Dj.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Removes all ${library.size} songs, their analysis and artwork, and stops any mix. " +
                        "Music files on the TV and USB drives are not touched.",
                    color = Dj.Muted, fontSize = 17.sp, modifier = Modifier.padding(start = EDGE),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TvCard("Cancel", null, Modifier.width(200.dp).focusRequester(cancel)) { confirmClear = false }
                    TvCard("Clear library", null, Modifier.width(240.dp), accent = Dj.Warn) {
                        confirmClear = false
                        vm.clearLibrary()
                        scanNote = "Library cleared."
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
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
                added == 0 -> "Up to date: all ${found.size} songs on this TV are already in the library."
                else -> "Added $added songs. Each is analysed once before it can be mixed."
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp - EDGE, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(horizontal = EDGE)) {
            FireTheDjWordmark(40.sp)
            Text("Your AI DJ for every party", color = Dj.Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
            Text(
                buildString {
                    append("${library.size} songs")
                    if (analysing > 0) append(" · analysing $analysing")
                },
                color = Dj.Muted, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }

        if (state.running) {
            val deck = state.current
            TvCard(
                title = "Now playing · ${deck?.title.orEmpty()}",
                subtitle = listOfNotNull(deck?.artist?.takeIf { it.isNotBlank() }, state.crate, "Press OK to watch the visualizer").joinToString(" · "),
                modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = 8.dp).focusRequester(first),
                onClick = onWatch,
            )
        }

        Section("Start a mix", "tv_crates") {
            if (crates.isEmpty()) {
                Text(
                    if (library.isEmpty()) "Add music below to start: songs on this TV or a USB drive, or YT Music playlists."
                    else "Analysing your songs. Crates appear here as soon as tracks are ready.",
                    color = Dj.Muted, fontSize = 17.sp, modifier = Modifier.padding(start = EDGE),
                )
            } else {
                // Room around every card for it to grow and ring when the remote lands on it.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 16.dp, horizontal = EDGE)) {
                    items(crates, key = { it.first }) { (crate, count) ->
                        val focus = if (!state.running && crate == crates.first().first) Modifier.focusRequester(first) else Modifier
                        TvCard(
                            title = if (crate == ALL) "All genres" else crate,
                            subtitle = "$count tracks · AI mix",
                            modifier = Modifier.width(230.dp).then(focus),
                        ) { vm.startSet(crate) }
                    }
                }
            }
        }

        Section("Mode", "tv_mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(horizontal = EDGE, vertical = 12.dp)) {
                TvCard("Smooth", "Long blends and bass swaps", Modifier.width(300.dp), selected = mode == MixMode.SMOOTH) { vm.setMode(MixMode.SMOOTH) }
                TvCard("Trickster", "Scratches, rolls, air horns", Modifier.width(300.dp), selected = mode == MixMode.TRICKSTER) { vm.setMode(MixMode.TRICKSTER) }
            }
        }

        Section("Add music", "tv_add") {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(horizontal = EDGE, vertical = 12.dp)) {
                TvCard("This TV & USB", "Find songs on the TV and USB drives", Modifier.width(320.dp), accent = Dj.Accent) { scanWithPermission() }
                TvCard("YT Music", "Curated playlists by mood and genre", Modifier.width(320.dp), accent = YT_RED, onClick = onYtMusic)
                if (library.isNotEmpty()) {
                    TvCard("Clear library", "Remove all ${library.size} songs", Modifier.width(300.dp), accent = Dj.Warn) { confirmClear = true }
                }
            }
            scanNote?.let { Text(it, color = Dj.BlueText, fontSize = 16.sp, modifier = Modifier.padding(start = EDGE)) }
        }
    }
}

@Composable
private fun Section(title: String, target: String, content: @Composable () -> Unit) {
    Column(Modifier.tourTarget(target), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Dj.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = EDGE))
        content()
    }
}

private val YT_RED = androidx.compose.ui.graphics.Color(0xFFFF4E45)

/** Space beside cards in a row, so a focused card can grow and ring without being clipped. */
private val EDGE = 20.dp
