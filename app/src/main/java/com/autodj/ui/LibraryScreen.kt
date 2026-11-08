package com.autodj.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autodj.analysis.Genres
import com.autodj.dj.MixMode
import com.autodj.model.TrackInfo
import com.autodj.remote.SpotifyProvider
import kotlin.math.roundToInt

/** The cross-genre "crate": every classified track. */
const val ALL = "All"

/** Whether a song of [family] is in [crate]: one genre, [ALL], or several joined by CRATE_SEP. */
fun inCrate(family: String, crate: String) =
    family != Genres.UNCLASSIFIED && (crate == ALL || family in crate.split(com.autodj.dj.CRATE_SEP))

fun crateLabel(crate: String) = if (crate == ALL) "All genres" else com.autodj.dj.cratesLabel(crate.split(com.autodj.dj.CRATE_SEP))

/** The Mix tab: your set (what it would make, the mode and Start), then the crates to pick from. */
@Composable
fun MixScreen(
    library: List<LibraryItem>,
    artwork: Map<String, String>,
    folders: List<MusicFolder>,
    mode: MixMode,
    onMode: (MixMode) -> Unit,
    pickedSource: String,
    onPickSource: (String) -> Unit,
    pickedCrate: String?,
    onPickCrate: (String) -> Unit,
    pickSeveral: Boolean,
    onPickSeveral: (Boolean) -> Unit,
    onCast: () -> Unit,
    onAddMusic: () -> Unit,
    onStart: (crate: String, source: String) -> Unit,
    onSettings: () -> Unit,
    /** A newer version to tell about (dot on Settings); [showBanner] also puts it at the top. */
    update: com.autodj.update.Release? = null,
    showBanner: Boolean = false,
    onDismissUpdate: () -> Unit = {},
) {
    // Source first (whole library or one folder), then genre crates within it.
    val folder = folders.firstOrNull { it.uri == pickedSource }
    val source = folder?.uri ?: ALL_SOURCES
    val inSource = if (folder == null) library else library.filter { it.uri in folder.tracks }
    val ready = inSource.mapNotNull { it.info }
    val counts = ready.groupingBy { it.genreFamily }.eachCount()
    // Biggest crates first, as on TV and Android Auto; ties keep the genre order.
    val crates = Genres.families.filter { (counts[it] ?: 0) > 0 }.sortedByDescending { counts[it] ?: 0 }
    val largest = counts.filterKeys { it != Genres.UNCLASSIFIED }.maxByOrNull { it.value }?.key ?: ALL
    val picks = pickedCrate?.split(com.autodj.dj.CRATE_SEP).orEmpty()
    val picked = if (ALL in picks) listOf(ALL) else crates.filter { it in picks }.ifEmpty { listOf(largest) }
    val crate = picked.joinToString(com.autodj.dj.CRATE_SEP)
    val allCount = ready.count { it.genreFamily != Genres.UNCLASSIFIED }
    val mixCount = if (crate == ALL) allCount else picked.sumOf { counts[it] ?: 0 }
    val mixable = crate != Genres.UNCLASSIFIED && mixCount > 0
    val analysing = inSource.count { it.status == AnalysisStatus.QUEUED || it.status == AnalysisStatus.ANALYZING }
    val unclassified = counts[Genres.UNCLASSIFIED] ?: 0
    fun tracksOf(c: String) = ready.filter { inCrate(it.genreFamily, c) }
    // With Pick several on, a tap adds or removes a crate; the last one stays, so Start has something to play.
    fun tapCrate(c: String) {
        if (!pickSeveral || c == ALL) return onPickCrate(c)
        val others = picked - ALL
        val next = if (c in others) others - c else others + c
        if (next.isNotEmpty()) onPickCrate(next.joinToString(com.autodj.dj.CRATE_SEP))
    }

    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val roomy = landscape && isRoomyLandscape()
    // A tablet held upright: the set card goes wide and the crates take three columns.
    val tablet = !landscape && config.screenWidthDp >= 600
    val big = roomy || tablet
    val columns = if (landscape || tablet) 3 else 2

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = if (landscape) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The logo in a floating glass capsule.
            Row(
                Modifier.height(44.dp).glass(CircleShape).padding(start = 7.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                AppIcon(30.dp, 9.dp)
                FireTheDjWordmark(18.sp)
            }
            Spacer(Modifier.weight(1f))
            // Sideways, Settings and Cast live at the foot of the tab rail.
            if (!landscape) Box {
                GlassIconButton(if (update != null) "Settings, an update is available" else "Settings", onClick = onSettings, modifier = Modifier.tourTarget("settings")) {
                    Icon(Icons.Filled.Settings, null, tint = Glass.Text, modifier = Modifier.size(22.dp))
                }
                if (update != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(9.dp).background(Glass.Ember, CircleShape))
            }
            if (!landscape) Box(Modifier.tourTarget("cast")) { CastButton(rememberCasting(), diameter = 44.dp, onClick = onCast) }
        }
        if (update != null && showBanner) UpdateBanner(update, onOpen = onSettings, onDismiss = onDismissUpdate, Modifier.padding(top = 12.dp))
    }
    val setCard: @Composable (Modifier) -> Unit = { modifier ->
        val tracks = if (mixable) tracksOf(crate) else emptyList()
        SetCard(
            name = if (!mixable) null else crateLabel(crate),
            tracks = tracks,
            library = inSource,
            artwork = artwork,
            mode = mode,
            onMode = onMode,
            wide = tablet,
            big = big,
            compact = landscape && !roomy,
            onStart = { onStart(crate, source) },
            modifier = modifier.tourTarget("set"),
        )
    }
    val cratesHeading: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Crates", color = Glass.Text, fontSize = if (big) 26.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.weight(1f, fill = false).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (crates.size > 1) {
                    val shape = RoundedCornerShape(20.dp)
                    Box(
                        Modifier.height(40.dp)
                            .glass(shape, if (pickSeveral) Glass.FireSelected else Glass.Fill, if (pickSeveral) androidx.compose.ui.graphics.SolidColor(Glass.Ember) else Glass.Edge)
                            .selectable(pickSeveral, role = Role.Switch) {
                                // Back to one: the biggest of those picked.
                                if (pickSeveral) onPickCrate(picked.first())
                                onPickSeveral(!pickSeveral)
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Pick several", color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
                SourcePicker(library.size, folders, folder, onPickSource, Modifier.weight(1f, fill = false).tourTarget("from"))
            }
        }
    }
    val grid: @Composable () -> Unit = {
        if (library.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No music yet", color = Glass.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Add a folder from this phone, YT Music playlists or a link. Each song is analysed once, then sorted into crates.",
                    color = Glass.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                Box(
                    Modifier.height(48.dp).glass(RoundedCornerShape(24.dp), Glass.FillStrong)
                        .clickable(role = Role.Button, onClick = onAddMusic).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Add music", color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        } else Column(Modifier.tourTarget("crates"), verticalArrangement = Arrangement.spacedBy(if (big) 16.dp else 12.dp)) {
            val cardHeight = if (big) 176.dp else 132.dp
            val cells: List<Pair<String, String>> = listOf(ALL to "${songs(allCount)} · across genres") +
                crates.map { it to songs(counts[it] ?: 0) }
            cells.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (big) 16.dp else 12.dp)) {
                    row.forEach { (c, sub) ->
                        CrateCard(
                            if (c == ALL) "All genres" else c, sub, c in picked, if (c == ALL) allCount > 0 else true,
                            coversFor(tracksOf(c), artwork), artwork, cardHeight, big, Modifier.weight(1f),
                            checkbox = pickSeveral && c != ALL,
                        ) { tapCrate(c) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // Songs of unclear genre are never mixed, so they're a note rather than a crate.
            if (unclassified > 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "See them on the Music tab", onClick = onAddMusic)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Info, null, tint = Glass.Muted, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                    Text(
                        buildAnnotatedString {
                            append("${songs(unclassified).replaceFirstChar { it.uppercase() }} ${if (unclassified == 1) "has" else "have"} an unclear genre, so ")
                            append(if (unclassified == 1) "it's" else "they're")
                            append(" left out of mixes. ")
                            withStyle(SpanStyle(color = Glass.EmberText, fontWeight = FontWeight.SemiBold)) { append("See them") }
                        },
                        color = Glass.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
            if (analysing > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Glass.Ember, trackColor = Glass.FillStrong, strokeWidth = 2.5.dp)
                    Text("Analysing ${songs(analysing)}. They join the set as they're ready.", color = Glass.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }

    if (landscape) {
        // Sideways: the set and Start in a column on the left, the crates scrolling on the right.
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (roomy) 20.dp else 14.dp)) {
            Column(Modifier.width(if (roomy) 400.dp else 290.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                header()
                setCard(Modifier.padding(top = 12.dp, bottom = 12.dp))
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cratesHeading(Modifier)
                    grid()
                }
                // More crates below: the grid fades out at the bottom edge so it reads as scrolling, not cut off.
                if (scroll.canScrollForward) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Glass.Ground))),
                    )
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (tablet) 36.dp else 18.dp)
                .padding(bottom = LocalTabBarInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header()
            if (library.isNotEmpty()) setCard(Modifier)
            cratesHeading(Modifier.padding(top = 4.dp))
            grid()
        }
    }
}

/** A set's numbers: its songs, how long they run together and their tempo range. */
private class SetNumbers(tracks: List<TrackInfo>) {
    val songs = tracks.size
    val length: String
    val bpm: String

    init {
        val minutes = (tracks.sumOf { it.durationSec } / 60).roundToInt()
        length = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} m" else "$minutes min"
        val bpms = tracks.map { it.bpm.roundToInt() }.filter { it > 0 }
        bpm = when {
            bpms.isEmpty() -> "–"
            bpms.min() == bpms.max() -> "${bpms.min()}"
            else -> "${bpms.min()}–${bpms.max()}"
        }
    }
}

/**
 * Your set, before it starts: the picked crate's name and covers, its songs, length and tempo range,
 * the mode with a line on what it does, and Start, all in one card so nothing hides under a floating
 * button. [wide] lays it out in two columns (a tablet upright); [big] scales it up; [compact] trims
 * it for a phone on its side. [name] null: no crate to mix yet.
 */
@Composable
private fun SetCard(
    name: String?,
    tracks: List<TrackInfo>,
    library: List<LibraryItem>,
    artwork: Map<String, String>,
    mode: MixMode,
    onMode: (MixMode) -> Unit,
    wide: Boolean,
    big: Boolean,
    compact: Boolean,
    onStart: () -> Unit,
    modifier: Modifier,
) {
    val numbers = SetNumbers(tracks)
    val uris = tracks.mapTo(HashSet()) { it.uri }
    val languages = library.filter { it.uri in uris }.mapNotNull { it.language }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
    val how = when (mode) {
        MixMode.SMOOTH -> "Long blends, bass swaps and tempo ramps, with no tricks inside songs."
        MixMode.TRICKSTER -> "A trick about every 30 seconds: drops, rolls, tape stops and key lifts."
    } + if (big) when (languages.size) {
        0 -> ""
        1 -> " Mostly ${languages[0]}."
        else -> " Mostly ${languages[0]} and ${languages[1]}."
    } else ""
    val covers = coversFor(tracks, artwork)
    val glow = coverGlow(covers.firstOrNull()?.let { artwork[it.uri] })
    val shape = RoundedCornerShape(if (big) 32.dp else 26.dp)
    val info: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR SET", color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        name ?: "Pick a crate", color = Glass.Text, fontSize = if (big) 40.sp else if (compact) 22.sp else 28.sp,
                        lineHeight = if (big) 44.sp else if (compact) 26.sp else 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!wide && covers.isNotEmpty()) CoverFan(covers, artwork, if (compact) 34.dp else 46.dp, Modifier.padding(start = 8.dp))
            }
            if (name != null) Row(horizontalArrangement = Arrangement.spacedBy(if (big) 32.dp else 20.dp)) {
                listOf("${numbers.songs}" to "songs", numbers.length to "of music", numbers.bpm to "BPM").forEach { (value, what) ->
                    Column {
                        Text(value, color = Glass.Text, fontSize = if (big) 26.sp else if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(what, color = Glass.Muted, fontSize = 13.sp)
                    }
                }
            }
            GlassSegmented(MixMode.entries, mode, { it.label }, onMode, Modifier.fillMaxWidth().tourTarget("mode"), height = if (big) 44.dp else 40.dp)
            Text(how, color = Glass.Muted, fontSize = if (big) 15.sp else 14.sp, lineHeight = if (big) 21.sp else 19.sp)
        }
    }
    val start: @Composable (Modifier) -> Unit = { m ->
        StartButton(if (name != null) "Start mix" else "Pick a crate to mix", name != null, big, m.tourTarget("start"), onStart)
    }
    Box(
        modifier.fillMaxWidth().floatingGlass(shape, Glass.FillStrong)
            .drawBehind { if (glow != null) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), Offset(size.width, 0f), size.width * 0.55f), size.width * 0.55f, Offset(size.width, 0f)) },
    ) {
        if (wide) {
            Row(Modifier.padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(Modifier.weight(1f)) { info() }
                Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { if (covers.isNotEmpty()) CoverFan(covers, artwork, 104.dp) }
                    start(Modifier)
                }
            }
        } else {
            Column(Modifier.padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                info()
                start(Modifier)
            }
        }
    }
}

/** Up to three songs to show for a crate: ones with a cover first, in a stable order. */
internal fun coversFor(tracks: List<TrackInfo>, artwork: Map<String, String>): List<TrackInfo> =
    tracks.sortedWith(compareBy<TrackInfo> { artwork[it.uri] == null }.thenBy { it.uri.hashCode() }).take(3)

/** Three covers fanned out, each the size of [size]: a crate or a set at a glance. */
@Composable
internal fun CoverFan(covers: List<TrackInfo>, artwork: Map<String, String>, size: Dp, modifier: Modifier = Modifier) {
    val turns = listOf(-9f, -2f, 8f)
    val step = size * 0.45f
    Box(modifier.width(size + step * 2).height(size * 1.15f)) {
        covers.forEachIndexed { i, t ->
            val shape = RoundedCornerShape(size * 0.22f)
            Artwork(
                artwork[t.uri], t.uri, t.title,
                Modifier.offset(x = step * i, y = if (i == 1) 0.dp else size * 0.08f).size(size)
                    .graphicsLayer { rotationZ = turns[i] }
                    .shadow(6.dp, shape),
                shape,
            )
        }
    }
}

/** A genre crate: a fan of its covers, a glow in their colour, its name and song count. The picked one is ringed in ember. */
@Composable
private fun CrateCard(
    name: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean,
    covers: List<TrackInfo>,
    artwork: Map<String, String>,
    height: Dp,
    big: Boolean,
    modifier: Modifier,
    /** Shows an empty ring while unpicked, so it reads as one of several choices. */
    checkbox: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (big) 26.dp else 22.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val glow = coverGlow(covers.firstOrNull()?.let { artwork[it.uri] })
    val glowAlpha = if (LocalGlassDark.current) 0.5f else 0.32f
    Box(
        modifier.height(height)
            .glass(
                shape,
                if (selected) Glass.FireSelected else Glass.Fill,
                if (selected) androidx.compose.ui.graphics.SolidColor(Glass.Ember) else Glass.Edge,
                if (selected || focused) 2.dp else 1.dp,
            )
            .drawBehind {
                if (glow != null) {
                    val c = Offset(size.width, 0f)
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = glowAlpha), Color.Transparent), c, size.minDimension), size.minDimension, c)
                }
            }
            .border(if (focused) 2.dp else 0.dp, if (focused) Glass.Text else Color.Transparent, shape)
            .selectable(selected, interactionSource = interaction, indication = null, role = if (checkbox) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .padding(if (big) 18.dp else 14.dp),
    ) {
        if (covers.isNotEmpty()) CoverFan(covers, artwork, if (big) 56.dp else 40.dp, Modifier.align(Alignment.TopStart))
        if (selected) Box(Modifier.align(Alignment.TopEnd).size(26.dp).background(Glass.Ember, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Glass.OnAccent, modifier = Modifier.size(16.dp))
        } else if (checkbox) Box(Modifier.align(Alignment.TopEnd).size(26.dp).border(2.dp, Glass.Muted, CircleShape))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(name, color = if (enabled) Glass.Text else Glass.Muted, fontSize = if (big) 20.sp else 17.sp, fontWeight = FontWeight.Bold,
                lineHeight = if (big) 24.sp else 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Glass.Muted, fontSize = if (big) 14.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** "All music · 199 ▾": which music the set draws from, the whole library or one folder or playlist. */
@Composable
private fun SourcePicker(total: Int, folders: List<MusicFolder>, picked: MusicFolder?, onPick: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.height(40.dp).glass(RoundedCornerShape(20.dp))
                .clickable(enabled = folders.isNotEmpty(), role = Role.Button, onClickLabel = "Choose the music to mix from") { open = true }
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (picked == null) "All music · $total" else "${picked.name} · ${picked.tracks.size}",
                color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp).weight(1f, fill = false),
            )
            if (folders.isNotEmpty()) Icon(Icons.Filled.KeyboardArrowDown, null, tint = Glass.Text, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All music · $total") }, onClick = { open = false; onPick(ALL_SOURCES) })
            folders.forEach { f ->
                DropdownMenuItem(text = { Text("${f.name} · ${f.tracks.size}") }, onClick = { open = false; onPick(f.uri) })
            }
        }
    }
}

/** The one fire-coloured control on the screen, with a play mark. */
@Composable
private fun StartButton(label: String, enabled: Boolean, big: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier.fillMaxWidth().height(if (big) 60.dp else 54.dp).clip(shape)
            .then(if (enabled) Modifier.background(Glass.Fire, shape).border(1.dp, Color.White.copy(alpha = 0.45f), shape) else Modifier.glass(shape, Glass.FillStrong))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enabled) PlayIcon(Glass.OnAccent, Modifier.padding(end = 8.dp).size(18.dp))
        Text(label, color = if (enabled) Glass.OnAccent else Glass.Muted, fontSize = if (big) 18.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The Music tab: add music, the sources you've added, and every song. */
@Composable
fun MusicScreen(
    library: List<LibraryItem>,
    artwork: Map<String, String>,
    folders: List<MusicFolder>,
    onAddFolder: (android.net.Uri?) -> Unit,
    onOpenYtMusic: () -> Unit,
    importState: ImportState,
    onImport: (String) -> Unit,
    onImportClosed: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var fromPhone by remember { mutableStateOf(false) }
    if (fromPhone) AddFromPhoneSheet(onPick = { fromPhone = false; onAddFolder(it) }, onClose = { fromPhone = false })
    if (importing) {
        ImportDialog(youTube = false, importState, onImport) {
            importing = false
            onImportClosed()
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the library?") },
            text = { Text("Removes all ${library.size} tracks and ${folders.size} folders and playlists from FireTheDJ, with their analysis and artwork, and stops any set. Your music files are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Clear library", color = Dj.Warn) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    val status = library.associateBy { it.uri }
    val addTiles: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddTile(SourceKind.PHONE, "This phone", Modifier.weight(1f)) { fromPhone = true }
            AddTile(SourceKind.STREAM, "Stream", Modifier.weight(1f).tourTarget("stream"), onOpenYtMusic)
            AddTile(SourceKind.LINK, "Paste a link", Modifier.weight(1f)) {
                // Start the sheet clean, not on the result of an import made on the YT Music page.
                if (importState != ImportState.Working) onImportClosed()
                importing = true
            }
        }
    }
    val clear: @Composable (Modifier) -> Unit = { modifier ->
        if (library.isNotEmpty() || folders.isNotEmpty()) {
            Box(
                modifier.fillMaxWidth().height(52.dp).glass(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = { confirmClear = true }),
                contentAlignment = Alignment.Center,
            ) { Text("Clear library", color = Dj.Warn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    val empty: @Composable () -> Unit = {
        Text(
            "Add a folder from this phone, YT Music playlists or a link. Each song is analysed once for tempo, beats, structure and genre.",
            color = Glass.Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    fun analysed(f: MusicFolder) = f.tracks.count { status[it]?.status.let { s -> s == AnalysisStatus.READY || s == AnalysisStatus.FAILED } }

    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && isRoomyLandscape()) {
        // A tablet on its side: adding and the sources on the left, every song in two columns on the right.
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Music", color = Glass.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                addTiles()
                if (folders.isNotEmpty()) {
                    GlassHeading("Your sources", Modifier.padding(start = 4.dp, top = 8.dp))
                    folders.forEachIndexed { i, f ->
                        SourceRow(f, analysed(f), groupShape(i, folders.size), languages = languageSummary(f.tracks.mapNotNull { status[it]?.language })) { onRemoveFolder(f.uri) }
                    }
                }
                clear(Modifier.padding(top = 8.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(28.dp), GlassOverContent).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(if (library.isEmpty()) "Songs" else "All songs · ${library.size}", color = Glass.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                if (library.isEmpty()) empty()
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(library, key = { it.uri }) { item -> TrackRow(item, artwork[item.uri], RoundedCornerShape(0.dp), onRemove, plain = true) }
                }
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + LocalTabBarInset.current)) {
        item {
            Text("Music", color = Glass.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 14.dp))
            addTiles()
        }
        if (folders.isNotEmpty()) {
            item {
                GlassHeading("Your sources", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
                // Each source as a card with a collage of its covers, side by side.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    folders.forEach { f ->
                        SourceCard(f, analysed(f), artwork, languageSummary(f.tracks.mapNotNull { status[it]?.language })) { onRemoveFolder(f.uri) }
                    }
                }
            }
        }
        item {
            GlassHeading(if (library.isEmpty()) "Songs" else "Songs · ${library.size}", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
            if (library.isEmpty()) empty()
        }
        itemsIndexed(library, key = { _, it -> it.uri }) { i, item ->
            TrackRow(item, artwork[item.uri], groupShape(i, library.size), onRemove)
        }
        item { clear(Modifier.padding(top = 24.dp)) }
    }
}

/** A folder or playlist as a card: a collage of its first covers, its name, and its songs (or how far analysis is). Long-press to remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceCard(folder: MusicFolder, analysed: Int, artwork: Map<String, String>, languages: String?, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val total = folder.tracks.size
    Box {
        Column(
            Modifier.width(168.dp).glass(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }, onClickLabel = "Options for ${folder.name}")
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shown = folder.tracks.take(4)
            Column(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        pair.forEach { uri -> Artwork(artwork[uri], uri, folder.name, Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Column {
                Text(folder.name, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (analysed < total) {
                    LinearProgressIndicator(
                        progress = { analysed / total.toFloat() }, modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = Glass.Ember, trackColor = Glass.FillSelected,
                    )
                    Text("$analysed of $total analysed", color = Glass.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(listOfNotNull(songs(total), languages).joinToString(" · "), color = Glass.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove from FireTheDJ") }, onClick = {
                menu = false
                onRemove()
            })
        }
    }
}

/** Rows of one list share a glass panel: rounded at its top and bottom only. */
private fun groupShape(i: Int, n: Int): Shape {
    val r = 20.dp
    return RoundedCornerShape(
        topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
        bottomStart = if (i == n - 1) r else 0.dp, bottomEnd = if (i == n - 1) r else 0.dp,
    )
}

@Composable
private fun AddTile(kind: SourceKind, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp).glass(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Add music: $label", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        SourceGlyph(kind)
        Text(label, color = Glass.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A folder or playlist: its kind, name, song count (or analysis progress) and a menu to remove it. */
@Composable
private fun SourceRow(folder: MusicFolder, analysed: Int, shape: Shape, languages: String? = null, onRemove: () -> Unit) {
    val kind = when {
        !folder.remote -> SourceKind.PHONE
        "youtube" in folder.uri -> SourceKind.YOUTUBE
        "spotify" in folder.uri -> SourceKind.SPOTIFY
        "music.apple.com" in folder.uri -> SourceKind.APPLE_MUSIC
        else -> SourceKind.LINK
    }
    val total = folder.tracks.size
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Glass.Fill, shape).padding(start = 14.dp, end = 4.dp).height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(kind.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            SourceGlyph(kind, 20.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (analysed < total) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { analysed / total.toFloat() }, modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                        color = Glass.Ember, trackColor = Glass.FillSelected,
                    )
                    Text("$analysed of $total", color = Glass.Muted, fontSize = 12.sp)
                }
            } else {
                Text(
                    (if (kind == SourceKind.PHONE) "This phone" else "Streams") + " · ${songs(total)}" + languages?.let { " · $it" }.orEmpty(),
                    color = Glass.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Box {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }
                    .semantics { contentDescription = "Options for ${folder.name}" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreVert, null, tint = Glass.Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove from FireTheDJ") }, onClick = {
                    menu = false
                    onRemove()
                })
            }
        }
    }
}

/** A selectable pill (YT Music's categories). Remote focus shows as a white ring. */
@Composable
internal fun CrateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .height(44.dp)
            .glass(shape, if (selected) Glass.FillSelected else Glass.Fill)
            .border(if (focused) 2.dp else 0.dp, if (focused) Glass.Text else Color.Transparent, shape)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) Glass.Text else Glass.Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(item: LibraryItem, art: String?, shape: Shape, onRemove: (String) -> Unit, plain: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    val info = item.info
    Box(if (plain) Modifier else Modifier.background(Glass.Fill, shape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Track options")
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (info != null) {
                Artwork(art, info.uri, info.title, Modifier.size(44.dp), RoundedCornerShape(8.dp))
            } else {
                Box(Modifier.size(44.dp).background(Glass.FillStrong, RoundedCornerShape(8.dp)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info?.title?.let(::displayTitle) ?: item.name, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (item.status) {
                    AnalysisStatus.READY -> {
                        info!!
                        val pct = (info.genreConfidence * 100).roundToInt()
                        if (info.genreFamily == Genres.UNCLASSIFIED) {
                            Text("Genre unclear ($pct%) · left out of mixes", color = Dj.Warn, fontSize = 12.sp)
                        } else {
                            val lang = item.language?.let { " · $it" }.orEmpty()
                            Text(listOf(info.artist, info.genre).filter { it.isNotBlank() }.joinToString(" · ") + lang, color = Glass.Muted, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    AnalysisStatus.FAILED -> Text("Couldn't analyse: ${item.error}", color = Dj.Warn, fontSize = 12.sp, maxLines = 2)
                    else -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(CircleShape), color = Glass.Ember, trackColor = Glass.FillSelected)
                        Text(
                            if (item.status == AnalysisStatus.ANALYZING) "Analysing beats, genre and artwork…" else "Waiting to analyse",
                            color = Glass.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            // The tempo as a chip, and the talk SponsorBlock skips (marked in a YouTube upload) as a small note under it.
            if (item.status == AnalysisStatus.READY && info != null && info.genreFamily != Genres.UNCLASSIFIED) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${"%.0f".format(info.bpm)} BPM", color = Glass.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(Glass.FillStrong).padding(horizontal = 8.dp, vertical = 2.dp))
                    val talk = (info.durationSec - (info.musicEndSec - info.musicStartSec)).roundToInt()
                    if (talk > 0) Text("skips ${talk / 60}:${"%02d".format(talk % 60)}", color = Glass.Muted, fontSize = 11.sp, maxLines = 1,
                        modifier = Modifier.semantics { contentDescription = "SponsorBlock skips ${talk / 60} minutes ${talk % 60} seconds of talk" })
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove from library") }, onClick = {
                menu = false
                onRemove(item.uri)
            })
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 70.dp).fillMaxWidth().height(1.dp).background(Glass.Text.copy(alpha = 0.08f)))
    }
}

/**
 * Paste a playlist link, in a glass sheet; the view model validates it, fetches it and adds it as
 * a source. When something is copied, one tap pastes and adds it. The [youTube] sheet only words
 * things for YouTube Music; any supported link works in either.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ImportDialog(youTube: Boolean, state: ImportState, onImport: (String) -> Unit, onClose: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    val working = state == ImportState.Working
    val submit = { if (link.isNotBlank() && !working) onImport(link.trim()) }
    val context = LocalContext.current
    // Only whether text is copied, not the text itself: reading it would make Android announce a paste.
    val clipboard = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
    val copied = remember { clipboard.primaryClipDescription?.hasMimeType("text/*") == true }
    GlassSheet(onClose) {
        Text(if (youTube) "Add from YouTube Music" else "Add a playlist link", color = Glass.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (copied && link.isBlank() && state !is ImportState.Done) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Glass.FireSelected)
                    .border(1.dp, Glass.Ember.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClickLabel = "Paste the copied link and add it") {
                        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
                        if (text.isNotBlank()) {
                            link = text
                            onImport(text)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).background(Glass.Ember, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { SourceGlyph(SourceKind.LINK, 20.dp) }
                Column(Modifier.weight(1f)) {
                    Text("Paste the link you copied", color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("It's added straight away", color = Glass.Muted, fontSize = 13.sp)
                }
                Text(
                    "Paste", color = Glass.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Glass.Ember, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            singleLine = true,
            enabled = !working && state !is ImportState.Done,
            placeholder = { Text(if (youTube) "https://music.youtube.com/playlist?list=…" else "Or type a link", color = Glass.Muted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            shape = RoundedCornerShape(18.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Glass.Text, unfocusedTextColor = Glass.Text, disabledTextColor = Glass.Muted,
                focusedContainerColor = Glass.Fill, unfocusedContainerColor = Glass.Fill, disabledContainerColor = Glass.Fill,
                focusedBorderColor = Glass.Ember, unfocusedBorderColor = Glass.Text.copy(alpha = 0.14f), disabledBorderColor = Glass.Text.copy(alpha = 0.1f),
                cursorColor = Glass.Ember,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        when (state) {
            ImportState.Working -> {
                // A Spotify link: its songs are looked up on YouTube one by one.
                val found by SpotifyProvider.matching.collectAsStateWithLifecycle()
                found?.let { (done, of) -> Text("Finding songs on YouTube: $done of $of", color = Glass.Text, fontSize = 14.sp) }
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Glass.Ember, trackColor = Glass.FillStrong)
            }
            is ImportState.Failed -> Text(state.reason, color = Dj.Warn, fontSize = 14.sp)
            is ImportState.Done -> Text(
                "Added \"${state.name}\" · ${state.tracks} songs" + (if (state.missing > 0) " (${state.missing} not found on YouTube)" else "") +
                    ". Pick it under All music on the Mix tab once they're analysed.",
                color = Glass.Text, fontSize = 14.sp, lineHeight = 20.sp,
            )
            ImportState.Idle -> Unit
        }
        if (youTube) {
            Text(
                "In YouTube Music, open a playlist or album, tap Share, then Copy link, and paste it here. Its songs stream when they're analysed and played, and are never saved on the phone.",
                color = Glass.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
        } else {
            GlassHeading("Works with")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Spotify" to Color(0xFF1ED760), "Apple Music" to Color(0xFFFF5C7A), "YouTube Music" to Color(0xFFFF4E45),
                    "Audius" to Color(0xFFB794F6), "Jamendo" to Color(0xFF5AB0FF), "Internet Archive" to Glass.Muted, "M3U or audio file" to Color(0xFFFFC233),
                ).forEach { (name, dot) ->
                    Row(
                        Modifier.height(32.dp).background(Glass.Fill, RoundedCornerShape(16.dp)).padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(dot, CircleShape))
                        Text(name, color = Glass.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("Spotify and Apple Music songs stream from YouTube. Every song is analysed like your own, then mixed.", color = Glass.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state is ImportState.Done) {
                SheetButton("Done", primary = true, enabled = true, Modifier.weight(1f), onClose)
            } else {
                SheetButton("Cancel", primary = false, enabled = true, Modifier.weight(1f), onClose)
                SheetButton("Add playlist", primary = true, enabled = link.isNotBlank() && !working, Modifier.weight(1f), submit)
            }
        }
    }
}

/**
 * Add songs from this phone: a folder, opened right where music usually is. Android's own picker
 * does the choosing; it opens in the folder tapped here instead of at the phone's root, where it
 * won't let an app have the whole storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFromPhoneSheet(onPick: (android.net.Uri?) -> Unit, onClose: () -> Unit) {
    fun folder(path: String) = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$path")
    GlassSheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(SourceKind.PHONE.color.copy(alpha = 0.2f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { SourceGlyph(SourceKind.PHONE, 24.dp) }
            Column {
                Text("Add songs from this phone", color = Glass.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Pick a folder; every song inside joins.", color = Glass.Muted, fontSize = 14.sp)
            }
        }
        SheetRow("Music", "Where most music apps save songs", highlighted = true) { onPick(folder("Music")) }
        SheetRow("Download", "Pick a folder inside Download", highlighted = false) { onPick(folder("Download")) }
        SheetRow("Another folder", "An SD card or any folder you choose", highlighted = false) { onPick(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Icon(Icons.Outlined.Info, null, tint = Glass.Muted, modifier = Modifier.size(16.dp).padding(top = 1.dp))
            Text(
                "Android opens its folder picker there. Tap Use this folder, then Allow. Songs are analysed in the background and join the crates as they're ready.",
                color = Glass.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
        }
        SheetButton("Cancel", primary = false, enabled = true, Modifier.fillMaxWidth(), onClose)
    }
}

/** A sheet of liquid glass from the bottom, with a solid smoky base so the screen behind never shows through its words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassSheet(onClose: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Glass.Sheet,
        contentColor = Glass.Text,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetRow(title: String, sub: String, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .glass(shape, if (highlighted) Glass.FireSelected else Glass.Fill, if (highlighted) androidx.compose.ui.graphics.SolidColor(Glass.Ember) else Glass.Edge, if (highlighted) 2.dp else 1.dp)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Glass.Muted, fontSize = 13.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.Muted)
    }
}

@Composable
private fun SheetButton(label: String, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.height(52.dp).clip(shape)
            .background(if (primary && enabled) Glass.Ember else Glass.FillStrong, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary && enabled) Glass.OnAccent else if (enabled) Glass.Text else Glass.Muted, fontSize = 16.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** "1 song", "38 songs". */
internal fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

/**
 * The languages a source's songs are sung in, for its card: the main one, and a second with its
 * count when it's a real part of the mix ("English · Spanish 6"). Null while none is known.
 */
private fun languageSummary(languages: List<String>): String? {
    val counts = languages.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    val first = counts.firstOrNull() ?: return null
    val second = counts.getOrNull(1)?.takeIf { it.value >= 3 }
    return if (second != null) "${first.key} · ${second.key} ${second.value}" else first.key
}
