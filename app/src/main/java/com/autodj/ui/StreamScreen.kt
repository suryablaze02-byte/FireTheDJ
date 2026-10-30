package com.autodj.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodj.remote.SpotifyCard
import com.autodj.remote.SpotifyCatalogue
import com.autodj.remote.YtMusicSearch
import com.autodj.remote.YtPlaylistCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StreamPage { CHOOSE, YT_MUSIC, SPOTIFY }

/**
 * Stream: YT Music or Spotify, and one search across both. Whichever is picked, the songs
 * stream from YouTube; Spotify only supplies the playlist.
 */
@Composable
fun StreamScreen(
    folders: List<MusicFolder>,
    importState: ImportState,
    /** A playlist link, and where it was picked ("Spotify › India › Hot Hits Telugu"; null when pasted). */
    onImport: (String, String?) -> Unit,
    onImportClosed: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(StreamPage.CHOOSE) }
    when (page) {
        StreamPage.YT_MUSIC -> YtMusicScreen(folders, importState, onImport, onImportClosed, onBack = { page = StreamPage.CHOOSE })
        StreamPage.SPOTIFY -> SpotifyScreen(folders, importState, onImport, onImportClosed, onBack = { page = StreamPage.CHOOSE })
        StreamPage.CHOOSE -> StreamHome(folders, importState, onImport, onImportClosed, onBack, onPick = { page = it })
    }
}

/** The search box, then either the two services or what the search found in each. */
@Composable
private fun StreamHome(
    folders: List<MusicFolder>,
    importState: ImportState,
    onImport: (String, String?) -> Unit,
    onImportClosed: () -> Unit,
    onBack: () -> Unit,
    onPick: (StreamPage) -> Unit,
    vm: StreamSearchViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val query by vm.query.collectAsStateWithLifecycle()
    val youTube by vm.youTube.collectAsStateWithLifecycle()
    var pasting by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf<String?>(null) }
    val added = remember(folders) { folders.filter { it.remote }.map { it.uri }.toSet() }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        Header("Stream", onBack) {
            if (importState != ImportState.Working) onImportClosed()
            pasting = true
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::search,
            singleLine = true,
            placeholder = { Text("Search playlists: telugu, party, edm…") },
            trailingIcon = if (query.isNotEmpty()) ({ TextButton(onClick = { vm.search("") }) { Text("Clear", color = Dj.Muted) } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Dj.Text, unfocusedTextColor = Dj.Text, focusedBorderColor = Dj.Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (query.isBlank()) {
            // Both tiles as tall as the taller one, which grows with large text.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceTile(SourceKind.YOUTUBE, "YT Music", "YouTube Music's curated playlists, by mood and genre", Modifier.weight(1f)) { onPick(StreamPage.YT_MUSIC) }
                ServiceTile(SourceKind.SPOTIFY, "Spotify", "Spotify's own playlists, played from YouTube", Modifier.weight(1f)) { onPick(StreamPage.SPOTIFY) }
            }
            Text(
                "Search finds Spotify playlists and any public YouTube Music playlist. Got a Spotify or YouTube Music link? " +
                    "Paste it. Nothing plays from Spotify and there's nothing to sign in to: every song streams from YouTube.",
                color = Dj.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            StatusLine(importState, tapped)
            val spotify = remember(query) { SpotifyCatalogue.search(query) }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ResultRow("Spotify", SourceKind.SPOTIFY) {
                        if (spotify.isEmpty()) {
                            Note("No Spotify playlists match. Paste a Spotify link to add any public one.")
                        } else {
                            CardRow(spotify, key = { it.id }) { card ->
                                SpotifyTile(card, card.link in added, busy = importState == ImportState.Working && tapped == card.title) {
                                    if (importState != ImportState.Working && card.link !in added) {
                                        tapped = card.title
                                        onImport(card.link, card.origin)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    ResultRow("YouTube Music", SourceKind.YOUTUBE) {
                        when (val r = youTube) {
                            Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = YT_RED, modifier = Modifier.size(28.dp))
                            }
                            is Loadable.Failed -> Note("Couldn't search YouTube Music: ${r.reason}")
                            is Loadable.Ready -> if (r.value.isEmpty()) {
                                Note("No YouTube Music playlists match.")
                            } else {
                                CardRow(r.value, key = { it.playlistId }) { card ->
                                    PlaylistCard(card, isAdded = card.link in added, busy = importState == ImportState.Working && tapped == card.title) {
                                        if (importState != ImportState.Working && card.link !in added) {
                                            tapped = card.title
                                            onImport(card.link, null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (pasting) {
        ImportDialog(youTube = false, importState, { link -> onImport(link, null) }) {
            pasting = false
            onImportClosed()
        }
    }
}

/** Spotify's own playlists by section; tapping one finds its songs on YouTube and adds it. */
@Composable
private fun SpotifyScreen(
    folders: List<MusicFolder>,
    importState: ImportState,
    onImport: (String, String?) -> Unit,
    onImportClosed: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var pasting by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf<String?>(null) }
    val added = remember(folders) { folders.filter { it.remote }.map { it.uri }.toSet() }
    Column(Modifier.fillMaxSize()) {
        Header("Spotify", onBack) {
            if (importState != ImportState.Working) onImportClosed()
            pasting = true
        }
        Text(
            "Spotify's playlists, played from YouTube: each song is found on YouTube Music. Up to 100 songs a playlist; " +
                "songs YouTube doesn't have are left out.",
            color = Dj.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        StatusLine(importState, tapped, SPOTIFY_GREEN)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(SpotifyCatalogue.sections, key = { it.title }) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section.title, color = Dj.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    CardRow(section.cards, key = { it.id }) { card ->
                        SpotifyTile(card, card.link in added, busy = importState == ImportState.Working && tapped == card.title) {
                            if (importState != ImportState.Working && card.link !in added) {
                                tapped = card.title
                                onImport(card.link, card.origin)
                            }
                        }
                    }
                }
            }
        }
    }
    if (pasting) {
        ImportDialog(youTube = false, importState, { link -> onImport(link, null) }) {
            pasting = false
            onImportClosed()
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, onPaste: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { BackGlyph(Dj.Text) }
        Text(title, color = Dj.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onPaste, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text("Paste a link", color = Dj.Text, fontSize = 13.sp)
        }
    }
}

/** A big tile for one service; grows and rings in its colour when the remote is on it. */
@Composable
private fun ServiceTile(kind: SourceKind, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxHeight().heightIn(min = 150.dp).scale(if (focused) 1.04f else 1f).clip(shape)
            .background(Brush.verticalGradient(listOf(kind.color.copy(alpha = 0.28f), Dj.Surface)), shape)
            .border(if (focused) 3.dp else 1.dp, kind.color.copy(alpha = if (focused) 1f else 0.45f), shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Open $title", onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(40.dp).background(kind.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            SourceGlyph(kind, 22.dp)
        }
        Text(title, color = Dj.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = Dj.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ResultRow(title: String, kind: SourceKind, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceGlyph(kind, 18.dp)
            Text(title, color = Dj.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun <T> CardRow(cards: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    // Vertical room too: the remote-focused card grows a little.
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = key) { card(it) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Dj.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

/**
 * A Spotify playlist card. Spotify's covers aren't fetched (that's a page load per card), so
 * each section gets its own colours, with the name set large on the cover.
 */
@Composable
private fun SpotifyTile(card: SpotifyCard, isAdded: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val (top, bottom) = SECTION_COLOURS[Math.floorMod(card.section.hashCode(), SECTION_COLOURS.size)]
    Column(
        Modifier.width(144.dp).scale(if (focused) 1.07f else 1f).clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = if (isAdded) "Already added" else "Add ${card.title}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .border(3.dp, if (focused) SPOTIFY_GREEN else Color.Transparent, RoundedCornerShape(17.dp))
                .padding(5.dp)
                .clip(shape).background(Brush.linearGradient(listOf(top, bottom)), shape),
        ) {
            Text(
                card.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 19.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { SourceGlyph(SourceKind.SPOTIFY, 18.dp) }
            when {
                busy -> Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                isAdded -> Text(
                    "Added", color = Dj.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(SPOTIFY_GREEN, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            card.section, color = if (focused) SPOTIFY_GREEN else Dj.Muted, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 5.dp),
        )
    }
}

private val SPOTIFY_GREEN = SourceKind.SPOTIFY.color

private val SECTION_COLOURS = listOf(
    Color(0xFF1DB954) to Color(0xFF0B3D2A),
    Color(0xFFE8115B) to Color(0xFF4A0A24),
    Color(0xFF8D67AB) to Color(0xFF2B1C3A),
    Color(0xFFE1118C) to Color(0xFF3A0B3A),
    Color(0xFF1E3264) to Color(0xFF0A1226),
    Color(0xFFBA5D07) to Color(0xFF3A1C02),
    Color(0xFF148A08) to Color(0xFF052E03),
    Color(0xFF509BF5) to Color(0xFF13305A),
    Color(0xFFAF2896) to Color(0xFF3A0D32),
    Color(0xFF477D95) to Color(0xFF15262E),
)

/**
 * The Stream page's search: Spotify's catalogue is searched on the spot (in the screen);
 * YouTube Music is asked once typing pauses, and each query's answer kept for the session.
 */
class StreamSearchViewModel : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _youTube = MutableStateFlow<Loadable<List<YtPlaylistCard>>>(Loadable.Ready(emptyList()))
    val youTube: StateFlow<Loadable<List<YtPlaylistCard>>> = _youTube

    private val cache = HashMap<String, List<YtPlaylistCard>>()
    private var job: Job? = null

    fun search(text: String) {
        _query.value = text
        job?.cancel()
        val q = text.trim().lowercase()
        if (q.length < 2) {
            _youTube.value = Loadable.Ready(emptyList())
            return
        }
        cache[q]?.let {
            _youTube.value = Loadable.Ready(it)
            return
        }
        _youTube.value = Loadable.Loading
        job = viewModelScope.launch {
            delay(450)
            val result = runCatching { withContext(Dispatchers.IO) { YtMusicSearch.playlists(q) } }
            result.getOrNull()?.let { cache[q] = it }
            // A newer search replaced this one: its blocking call still ran to the end, but it
            // mustn't overwrite the newer answer (or show its cancellation as an error).
            ensureActive()
            _youTube.value = result.fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }
}
