package com.autodj.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.analysis.Genres
import com.autodj.dj.MixMode
import kotlin.math.roundToInt

/** The cross-genre "crate": every classified track. */
const val ALL = "All"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    library: List<LibraryItem>,
    artwork: Map<String, String>,
    mode: MixMode,
    onMode: (MixMode) -> Unit,
    folders: List<MusicFolder>,
    onAddFolder: () -> Unit,
    onOpenYtMusic: () -> Unit,
    onCast: () -> Unit,
    importState: ImportState,
    onImport: (String) -> Unit,
    onImportClosed: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onClear: () -> Unit,
    onStart: (crate: String, source: String) -> Unit,
    onRemove: (String) -> Unit,
) {
    // Source first (whole library or one folder), then genre crates within it.
    var pickedSource by rememberSaveable { mutableStateOf(ALL_SOURCES) }
    val folder = folders.firstOrNull { it.uri == pickedSource }
    val source = folder?.uri ?: ALL_SOURCES
    val inSource = if (folder == null) library else library.filter { it.uri in folder.tracks }
    val ready = inSource.mapNotNull { it.info }
    val counts = ready.groupingBy { it.genreFamily }.eachCount()
    val crates = Genres.families.filter { (counts[it] ?: 0) > 0 } +
        listOfNotNull(Genres.UNCLASSIFIED.takeIf { (counts[it] ?: 0) > 0 })
    val largest = counts.filterKeys { it != Genres.UNCLASSIFIED }.maxByOrNull { it.value }?.key ?: ALL
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val crate = picked?.takeIf { it == ALL || it in crates } ?: largest
    val shown = inSource.filter { crate == ALL || it.info?.genreFamily == crate || it.info == null }
    val mixCount = if (crate == ALL) ready.count { it.genreFamily != Genres.UNCLASSIFIED } else counts[crate] ?: 0
    val mixable = crate != Genres.UNCLASSIFIED && mixCount > 0
    var confirmClear by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Baselines, not boxes: text of two sizes only lines up along the bottom of its letters.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Library", color = Dj.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
                Text("Add music from", color = Dj.Muted, fontSize = 12.sp, modifier = Modifier.alignByBaseline())
            }
            Box(Modifier.tourTarget("cast")) { CastButton(rememberCasting(), diameter = 36.dp, onClick = onCast) }
        }
        // Tiles grow with the system font size; all four match the tallest.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).tourTarget("sources"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceTile(SourceKind.PHONE, Modifier.weight(1.05f), onAddFolder)
            SourceTile(SourceKind.YOUTUBE, Modifier.weight(1.3f), onOpenYtMusic)
            SourceTile(SourceKind.LINK, Modifier.weight(0.95f)) {
                // Start the dialog clean, not on the result of an import made on the YT Music page.
                if (importState != ImportState.Working) onImportClosed()
                importing = true
            }
            ClearTile(library.isNotEmpty() || folders.isNotEmpty(), Modifier.weight(0.6f)) { confirmClear = true }
        }
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
                        pickedSource = ALL_SOURCES
                        onClear()
                    }) { Text("Clear library", color = Dj.Warn) }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            )
        }

        // Everything below the add tiles scrolls as one list, so the tracks always have room.
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (folders.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Source · long-press a folder or playlist to remove it", color = Dj.Muted, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CrateChip("All music ${library.size}", folder == null) { pickedSource = ALL_SOURCES }
                                folders.forEach { f ->
                                    FolderChip("${f.name} ${f.tracks.size}", f.uri == pickedSource, onClick = { pickedSource = f.uri }) {
                                        if (pickedSource == f.uri) pickedSource = ALL_SOURCES
                                        onRemoveFolder(f.uri)
                                    }
                                }
                            }
                        }
                    }

                    Column(Modifier.tourTarget("crates"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Genre crates · detected from the audio", color = Dj.Muted, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CrateChip("$ALL ${ready.size}", crate == ALL) { picked = ALL }
                            crates.forEach { c -> CrateChip("$c ${counts[c]}", crate == c) { picked = c } }
                        }
                        Text(
                            if (crate == ALL) {
                                "All genres: FireTheDJ moves between neighbouring genres, using tempo ramps and half-time links to get there."
                            } else {
                                "One crate: FireTheDJ stays inside this genre family for the whole set."
                            },
                            color = Dj.Muted,
                            fontSize = 12.sp,
                        )
                    }

                    Column(Modifier.tourTarget("mode"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Mode", color = Dj.Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                            MixMode.entries.forEach { m -> CrateChip(m.label, mode == m) { onMode(m) } }
                        }
                        Text(
                            when (mode) {
                                MixMode.SMOOTH -> "Long crossfades, bass-drop mixes and tempo ramp-ups. Nothing interrupts the music."
                                MixMode.TRICKSTER -> "Scratches, rolls, brakes, echo throws, sirens and air horns all through the set."
                            },
                            color = Dj.Muted,
                            fontSize = 12.sp,
                        )
                    }

                    if (library.isEmpty()) {
                        Text(
                            "Add songs or a whole folder from your phone. Each track is analysed once for tempo, beats, structure and genre.",
                            color = Dj.Muted,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            items(shown, key = { it.uri }) { item -> TrackRow(item, artwork[item.uri], onRemove) }
        }

        Button(
            onClick = { onStart(crate, source) },
            enabled = mixable,
            modifier = Modifier.fillMaxWidth().height(54.dp).tourTarget("start"),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Dj.Accent,
                contentColor = Dj.Bg,
                disabledContainerColor = Dj.Surface,
                disabledContentColor = Dj.Muted,
            ),
        ) {
            Text(
                when {
                    !mixable -> "Pick a genre crate to mix"
                    crate == ALL -> "Start AI mix · All genres ($mixCount)"
                    else -> "Start AI mix · $crate ($mixCount)"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(0.dp))
    }
}

/** A source chip; long-press offers to remove the folder. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box {
        Box(
            Modifier
                .height(36.dp)
                .background(if (selected) Dj.Accent else Dj.Surface, shape)
                .border(1.dp, if (selected) Dj.Accent else Dj.Border, shape)
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "Folder options")
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 13.sp, color = if (selected) Dj.Bg else Dj.Text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove folder") }, onClick = {
                menu = false
                onRemove()
            })
        }
    }
}

@Composable
internal fun CrateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    // Remote focus (TV) shows as a bright edge; touch never focuses it.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .height(36.dp)
            .background(if (selected) Dj.Accent else Dj.Surface, shape)
            // Remote focus: an accent ring (white on the already-orange selected chip).
            .border(if (focused) 3.dp else 1.dp, if (focused) (if (selected) Dj.Text else Dj.Accent) else if (selected) Dj.Accent else Dj.Border, shape)
            .clip(shape)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) Dj.Bg else Dj.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(item: LibraryItem, art: String?, onRemove: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val info = item.info
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Track options")
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (info != null) {
                Artwork(art, info.uri, info.title, Modifier.size(48.dp), RoundedCornerShape(8.dp))
            } else {
                Box(Modifier.size(48.dp).background(Dj.Surface, RoundedCornerShape(8.dp)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info?.title ?: item.name, color = Dj.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (item.status) {
                    AnalysisStatus.READY -> {
                        info!!
                        if (info.artist.isNotBlank()) Text(info.artist, color = Dj.Muted, fontSize = 13.sp, maxLines = 1)
                        val pct = (info.genreConfidence * 100).roundToInt()
                        if (info.genreFamily == Genres.UNCLASSIFIED) {
                            Text("Genre unclear ($pct%) · left out of mixes", color = Dj.Warn, fontSize = 12.sp)
                        } else {
                            val from = when (info.genreSource) { "tag" -> " · from tag"; "online", "online+audio" -> " · from web"; else -> "" }
                            Text("${info.genre} · $pct% match$from", color = Dj.BlueText, fontSize = 12.sp)
                        }
                    }
                    AnalysisStatus.FAILED -> Text("Couldn't analyse: ${item.error}", color = Dj.Warn, fontSize = 12.sp)
                    else -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Dj.Blue, trackColor = Dj.Border)
                        Text(
                            if (item.status == AnalysisStatus.ANALYZING) "Analysing beats, genre and artwork…" else "Waiting to analyse",
                            color = Dj.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (info != null) {
                Text("%.1f".format(info.bpm), color = Dj.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove from library") }, onClick = {
                menu = false
                onRemove(item.uri)
            })
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Dj.Divider))
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
                    is ImportState.Done -> Text("Added \"${state.name}\" · ${state.tracks} tracks. Pick it under Source once they're analysed.", color = Dj.BlueText, fontSize = 13.sp)
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
