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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    var stats by remember { mutableStateOf(false) }
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

    val artwork by vm.artwork.collectAsStateWithLifecycle()
    val ready = library.mapNotNull { it.info }.filter { it.genreFamily != Genres.UNCLASSIFIED }
    val analysing = library.count { it.status == AnalysisStatus.QUEUED || it.status == AnalysisStatus.ANALYZING }
    val crates = buildList {
        if (ready.isNotEmpty()) add(ALL to ready.size)
        ready.groupingBy { it.genreFamily }.eachCount().entries.sortedByDescending { it.value }.forEach { add(it.key to it.value) }
    }

    if (stats) TvStatsScreen(artwork, onDismiss = { stats = false })
    val families = crates.filter { it.first != ALL }
    var mixSeveral by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf(emptySet<String>()) }
    if (mixSeveral) TvMixSeveral(
        families, picks,
        onToggle = { c -> picks = if (c in picks) picks - c else picks + c },
        onStart = { names -> mixSeveral = false; vm.startSet(names.joinToString(com.autodj.dj.CRATE_SEP)) },
        onDismiss = { mixSeveral = false },
    )

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
    val touring = com.autodj.ui.LocalTour.current?.active == true
    LaunchedEffect(crates.isNotEmpty(), state.running, touring) { if (!touring) runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        AmbientLight(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp - EDGE, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(horizontal = EDGE)) {
                Row(
                    Modifier.height(48.dp).glass(CircleShape).padding(start = 8.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.autodj.ui.AppIcon(32.dp, 10.dp)
                    FireTheDjWordmark(22.sp)
                }
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
                TvRoundButton(40.dp, Glass.Fill, "Your stats", onClick = { stats = true }) { com.autodj.ui.StatsGlyph(Glass.Text) }
                TvRoundButton(40.dp, Glass.Fill, "Settings", Modifier.tourTarget("tv_settings"), onClick = { settings = true }) {
                    Icon(Icons.Filled.Settings, null, tint = Glass.Text, modifier = Modifier.size(22.dp))
                }
            }

            if (state.running) {
                NowPlayingBanner(state, artwork, Modifier.padding(horizontal = EDGE, vertical = 10.dp).focusRequester(first), onWatch)
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
                        crates.forEachIndexed { i, (crate, count) ->
                            item(key = crate) {
                                val focus = if (!state.running && i == 0) Modifier.focusRequester(first) else Modifier
                                val tracks = if (crate == ALL) ready else ready.filter { it.genreFamily == crate }
                                CrateTile(
                                    if (crate == ALL) "All genres" else crate,
                                    if (crate == ALL) "${songs(count)} · across genres" else songs(count),
                                    com.autodj.ui.coversFor(tracks, artwork), artwork,
                                    Modifier.width(250.dp).height(180.dp).then(focus),
                                ) { vm.startSet(crate) }
                            }
                            if (i == 0 && families.size > 1) item(key = "mix several") {
                                CrateTile(
                                    "Mix several", if (picks.isEmpty()) "Pick your crates" else "${picks.size} picked", emptyList(), artwork,
                                    Modifier.width(250.dp).height(180.dp), plus = true,
                                ) { mixSeveral = true }
                            }
                        }
                    }
                }
            }

            Column(Modifier.tourTarget("tv_add"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Add music", color = Glass.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = EDGE))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(horizontal = EDGE, vertical = 12.dp)) {
                    // The two the same size, whatever their words.
                    TvCard("This TV & USB", "Scan for songs", Modifier.width(320.dp).height(112.dp), accent = SourceKind.PHONE.color,
                        icon = { SourceGlyph(SourceKind.PHONE, 30.dp) }) { scanWithPermission() }
                    TvCard("Stream", "YT Music, Spotify or Apple Music", Modifier.width(320.dp).height(112.dp), accent = SourceKind.STREAM.color,
                        icon = { SourceGlyph(SourceKind.STREAM, 30.dp) }, onClick = onYtMusic)
                }
                scanNote?.let { Text(it, color = Glass.EmberText, fontSize = 15.sp, modifier = Modifier.padding(start = EDGE)) }
            }
        }
    }
}

/**
 * The set that's playing, across the top of Home: its record, the song, what the AI does next and
 * how far into the song it is, in the song's own colour. OK opens the decks.
 */
@Composable
private fun NowPlayingBanner(state: com.autodj.dj.EngineState, artwork: Map<String, String>, modifier: Modifier, onOpen: () -> Unit) {
    val deck = state.current ?: return
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(30.dp)
    val ground = com.autodj.ui.songGround(artwork[deck.uri])
    com.autodj.ui.DarkGlass {
        Box(
            modifier.fillMaxWidth().scale(if (focused) 1.02f else 1f).clip(shape).background(ground)
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.16f), shape)
                .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Open the decks", onClick = onOpen),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 28.dp, top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                com.autodj.ui.Artwork(artwork[deck.uri], deck.uri, deck.title, Modifier.size(96.dp).border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape), CircleShape)
                Column(Modifier.weight(1f)) {
                    Text("NOW PLAYING" + state.crate?.let { " · ${it.uppercase()}" }.orEmpty(), color = Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                    Text(com.autodj.ui.displayTitle(deck.title), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val next = when {
                        state.mixing -> "mixing into ${state.next?.title?.let { com.autodj.ui.displayTitle(it) } ?: "the next song"}"
                        state.recipe != null && state.secondsToMix != null -> "next: ${state.recipe} in ${com.autodj.ui.formatTime(state.secondsToMix)}"
                        else -> null
                    }
                    Text(listOfNotNull(deck.artist.ifBlank { null }, next).joinToString(" · "), color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Open the decks ›", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            val progress = if (deck.durationSec > 0) (deck.positionSec / deck.durationSec).toFloat().coerceIn(0f, 1f) else 0f
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(Glass.Ember))
        }
    }
}

/** A crate for the remote: a fan of its covers, a glow in their colour, its name and songs. Grows and rings in white when focused. */
@Composable
private fun CrateTile(name: String, sub: String, covers: List<com.autodj.model.TrackInfo>, artwork: Map<String, String>, modifier: Modifier, plus: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(26.dp)
    val glow = com.autodj.ui.coverGlow(covers.firstOrNull()?.let { artwork[it.uri] })
    Box(
        modifier.scale(if (focused) 1.08f else 1f)
            .glass(shape, if (focused) Glass.FillSelected else Glass.Fill)
            .drawBehind {
                if (glow != null) {
                    val c = androidx.compose.ui.geometry.Offset(size.width, 0f)
                    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), c, size.minDimension), size.minDimension, c)
                }
            }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = if (plus) "Choose crates to mix" else "Start a mix from $name", onClick = onClick)
            .padding(20.dp),
    ) {
        if (covers.isNotEmpty()) com.autodj.ui.CoverFan(covers, artwork, 58.dp, Modifier.align(Alignment.TopStart))
        if (plus) Text("+", color = Glass.EmberText, fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(name, color = Glass.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Glass.Muted, fontSize = 16.sp, maxLines = 1)
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
            Modifier.width(width).background(Glass.Sheet, RoundedCornerShape(30.dp)).glass(RoundedCornerShape(30.dp), Glass.Fill)
                // Scrolls rather than squashing the last row when a dialog is taller than the TV screen.
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp),
        ) { content(first) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Space beside cards in a row, so a focused card can grow and ring without being clipped. */
val TV_EDGE = 20.dp
private val EDGE = TV_EDGE

/** Slim list at the side: tick crates with the remote, then Start. [crates] is biggest first, with song counts. */
@Composable
private fun TvMixSeveral(crates: List<Pair<String, Int>>, picks: Set<String>, onToggle: (String) -> Unit, onStart: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    val chosen = crates.filter { it.first in picks }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.padding(20.dp).fillMaxHeight().width(440.dp)
                    .background(Glass.Sheet, RoundedCornerShape(32.dp)).glass(RoundedCornerShape(32.dp), Glass.Fill)
                    .padding(horizontal = 14.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Mix several", color = Glass.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    crates.forEachIndexed { i, (crate, count) ->
                        TvCard(
                            crate, songs(count), Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                            selected = crate in picks,
                            icon = {
                                Box(
                                    Modifier.size(28.dp).then(if (crate in picks) Modifier.background(Glass.Ember, CircleShape) else Modifier.border(2.dp, Glass.Muted, CircleShape)),
                                    contentAlignment = Alignment.Center,
                                ) { if (crate in picks) Text("✓", color = Glass.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            },
                        ) { onToggle(crate) }
                    }
                }
                if (chosen.isEmpty()) Text("Tick a crate to start", color = Glass.Muted, fontSize = 16.sp, modifier = Modifier.padding(10.dp))
                else TvCard("Start · ${songs(chosen.sumOf { it.second })}", com.autodj.dj.cratesLabel(chosen.map { it.first }), Modifier.fillMaxWidth().padding(horizontal = 10.dp), accent = Glass.Ember, subtitleLines = 1) {
                    onStart(chosen.map { it.first })
                }
            }
        }
    }
}
