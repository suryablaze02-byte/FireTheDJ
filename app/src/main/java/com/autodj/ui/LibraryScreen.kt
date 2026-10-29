package com.autodj.ui

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.analysis.Genres
import com.autodj.dj.MixMode
import kotlin.math.roundToInt

/** The cross-genre "crate": every classified track. */
const val ALL = "All"

/** The Mix tab: what to mix from, which crate, which mode, and Start. */
@Composable
fun MixScreen(
    library: List<LibraryItem>,
    folders: List<MusicFolder>,
    mode: MixMode,
    onMode: (MixMode) -> Unit,
    pickedSource: String,
    onPickSource: (String) -> Unit,
    pickedCrate: String?,
    onPickCrate: (String) -> Unit,
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
    val crates = Genres.families.filter { (counts[it] ?: 0) > 0 } +
        listOfNotNull(Genres.UNCLASSIFIED.takeIf { (counts[it] ?: 0) > 0 })
    val largest = counts.filterKeys { it != Genres.UNCLASSIFIED }.maxByOrNull { it.value }?.key ?: ALL
    val crate = pickedCrate?.takeIf { it == ALL || it in crates } ?: largest
    val allCount = ready.count { it.genreFamily != Genres.UNCLASSIFIED }
    val mixCount = if (crate == ALL) allCount else counts[crate] ?: 0
    val mixable = crate != Genres.UNCLASSIFIED && mixCount > 0
    val analysing = inSource.count { it.status == AnalysisStatus.QUEUED || it.status == AnalysisStatus.ANALYZING }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FireTheDjWordmark(20.sp, Modifier.weight(1f))
            Box {
                GlassIconButton(if (update != null) "Settings, an update is available" else "Settings", onClick = onSettings) {
                    Icon(Icons.Filled.Settings, null, tint = Glass.Text, modifier = Modifier.size(22.dp))
                }
                if (update != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(9.dp).background(Glass.Ember, CircleShape))
            }
            Box(Modifier.tourTarget("cast")) { CastButton(rememberCasting(), diameter = 44.dp, onClick = onCast) }
        }
        if (update != null && showBanner) UpdateBanner(update, onOpen = onSettings, onDismiss = onDismissUpdate, Modifier.padding(top = 12.dp))
        Text("Pick a crate", color = Glass.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(top = 10.dp))

        SourcePicker(library.size, folders, folder, onPickSource, Modifier.padding(top = 8.dp).tourTarget("from"))

        GlassSegmented(
            MixMode.entries, mode, { it.label }, onMode,
            Modifier.fillMaxWidth().padding(top = 14.dp).tourTarget("mode"),
        )

        if (library.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No music yet", color = Glass.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Add a folder from this phone, YT Music playlists or a link. Each song is analysed once, then sorted into crates.",
                    color = Glass.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                Box(
                    Modifier.height(48.dp).glass(RoundedCornerShape(24.dp), Glass.FillStrong)
                        .clickable(role = Role.Button, onClick = onAddMusic).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Add music", color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.weight(1f).padding(top = 16.dp).tourTarget("crates"),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    CrateCard("All genres", "${songs(allCount)} · moves across genres", crate == ALL, allCount > 0) { onPickCrate(ALL) }
                }
                items(crates) { c ->
                    val n = counts[c] ?: 0
                    val sub = if (c == Genres.UNCLASSIFIED) "${songs(n)} · not mixed" else songs(n)
                    CrateCard(c, sub, crate == c, c != Genres.UNCLASSIFIED) { onPickCrate(c) }
                }
                if (analysing > 0) {
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Glass.Ember, trackColor = Glass.FillStrong, strokeWidth = 3.dp)
                            Text("Analysing ${songs(analysing)}. They join the set as they're ready.", color = Glass.Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        StartButton(
            when {
                !mixable -> "Pick a crate to mix"
                crate == ALL -> "Start mix · All genres · $mixCount"
                else -> "Start mix · $crate · $mixCount"
            },
            mixable,
            Modifier.padding(top = 8.dp, bottom = 12.dp).tourTarget("start"),
        ) { onStart(crate, source) }
    }
}

/** "From: All music ▾": which music the set draws from, the whole library or one folder or playlist. */
@Composable
private fun SourcePicker(total: Int, folders: List<MusicFolder>, picked: MusicFolder?, onPick: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("From", color = Glass.Muted, fontSize = 15.sp)
        Box {
            Row(
                Modifier.height(44.dp).glass(RoundedCornerShape(22.dp))
                    .clickable(enabled = folders.isNotEmpty(), role = Role.Button, onClickLabel = "Choose the music to mix from") { open = true }
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (picked == null) "All music · $total" else "${picked.name} · ${picked.tracks.size}",
                    color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 2.dp),
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
}

/** A genre crate as a glass card; the picked one glows ember with a check. */
@Composable
private fun CrateCard(name: String, sub: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier.fillMaxWidth().height(84.dp)
            .glass(
                shape,
                if (selected) Glass.FireSelected else Glass.Fill,
                if (selected) androidx.compose.ui.graphics.SolidColor(Glass.Ember) else Glass.Edge,
                if (selected || focused) 1.5.dp else 1.dp,
            )
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = if (enabled) Glass.Text else Glass.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(sub, color = if (selected) Color.White.copy(alpha = 0.85f) else Glass.Muted, fontSize = 13.sp, maxLines = 1)
    }
}

/** The one fire-coloured control on the screen. */
@Composable
private fun StartButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(shape)
            .then(if (enabled) Modifier.background(Glass.Fire, shape) else Modifier.glass(shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else Glass.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The Music tab: add music, the sources you've added, and every song. */
@Composable
fun MusicScreen(
    library: List<LibraryItem>,
    artwork: Map<String, String>,
    folders: List<MusicFolder>,
    onAddFolder: () -> Unit,
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        item {
            Text("Music", color = Glass.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddTile(SourceKind.PHONE, "This phone", Modifier.weight(1f), onAddFolder)
                AddTile(SourceKind.YOUTUBE, "YT Music", Modifier.weight(1f), onOpenYtMusic)
                AddTile(SourceKind.LINK, "Paste a link", Modifier.weight(1f)) {
                    // Start the dialog clean, not on the result of an import made on the YT Music page.
                    if (importState != ImportState.Working) onImportClosed()
                    importing = true
                }
            }
        }
        if (folders.isNotEmpty()) {
            item { GlassHeading("Your sources", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)) }
            itemsIndexed(folders, key = { _, f -> "folder:" + f.uri }) { i, f ->
                val done = f.tracks.count { status[it]?.status.let { s -> s == AnalysisStatus.READY || s == AnalysisStatus.FAILED } }
                SourceRow(f, done, groupShape(i, folders.size), languages = languageSummary(f.tracks.mapNotNull { status[it]?.language })) { onRemoveFolder(f.uri) }
            }
        }
        item {
            GlassHeading(if (library.isEmpty()) "Songs" else "Songs · ${library.size}", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
            if (library.isEmpty()) {
                Text(
                    "Add a folder from this phone, YT Music playlists or a link. Each song is analysed once for tempo, beats, structure and genre.",
                    color = Glass.Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        itemsIndexed(library, key = { _, it -> it.uri }) { i, item ->
            TrackRow(item, artwork[item.uri], groupShape(i, library.size), onRemove)
        }
        if (library.isNotEmpty() || folders.isNotEmpty()) {
            item {
                Box(
                    Modifier.padding(top = 24.dp).fillMaxWidth().height(52.dp).glass(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = { confirmClear = true }),
                    contentAlignment = Alignment.Center,
                ) { Text("Clear library", color = Dj.Warn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
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
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) Glass.Text else Glass.Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(item: LibraryItem, art: String?, shape: Shape, onRemove: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val info = item.info
    Box(Modifier.background(Glass.Fill, shape)) {
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
                Text(info?.title ?: item.name, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (item.status) {
                    AnalysisStatus.READY -> {
                        info!!
                        val pct = (info.genreConfidence * 100).roundToInt()
                        if (info.genreFamily == Genres.UNCLASSIFIED) {
                            Text("Genre unclear ($pct%) · left out of mixes", color = Dj.Warn, fontSize = 12.sp)
                        } else {
                            val who = info.artist.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                            val lang = item.language?.let { " · $it" }.orEmpty()
                            Text("$who${info.genre} · ${"%.0f".format(info.bpm)} BPM$lang", color = Glass.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove from library") }, onClick = {
                menu = false
                onRemove(item.uri)
            })
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 70.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

/**
 * Paste a playlist link; the view model validates it, fetches it and adds it as a source. The
 * [youTube] dialog only words things for YouTube Music; any supported link works in either.
 */
@Composable
internal fun ImportDialog(youTube: Boolean, state: ImportState, onImport: (String) -> Unit, onClose: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    val working = state == ImportState.Working
    val submit = { if (link.isNotBlank() && !working) onImport(link.trim()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (youTube) "Add from YouTube Music" else "Add a playlist link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (youTube) {
                        "In YouTube Music, open a playlist or album, tap Share, then Copy link, and paste it here. " +
                            "Its tracks stream when they're analysed and played, and are never saved on the phone."
                    } else {
                        "An Audius or Jamendo playlist or album, an Internet Archive release, an M3U playlist, " +
                            "or a direct link to an audio file. Its tracks are analysed like your own, then mixed."
                    },
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    enabled = !working && state !is ImportState.Done,
                    placeholder = { Text(if (youTube) "https://music.youtube.com/playlist?list=…" else "https://audius.co/…/playlist/…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                when (state) {
                    ImportState.Working -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Dj.Blue, trackColor = Dj.Border)
                    is ImportState.Failed -> Text(state.reason, color = Dj.Warn, fontSize = 13.sp)
                    is ImportState.Done -> Text("Added \"${state.name}\" · ${state.tracks} tracks. Pick it under From on the Mix tab once they're analysed.", color = Dj.BlueText, fontSize = 13.sp)
                    ImportState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            if (state is ImportState.Done) {
                TextButton(onClick = onClose) { Text("Done") }
            } else {
                TextButton(onClick = submit, enabled = link.isNotBlank() && !working) { Text("Import") }
            }
        },
        dismissButton = { if (state !is ImportState.Done) TextButton(onClick = onClose) { Text("Cancel") } },
    )
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
