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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodj.remote.AppleMusicCard
import com.autodj.remote.AppleMusicCatalogue
import com.autodj.remote.PlaylistCovers
import com.autodj.remote.SpotifyCard
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
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

private enum class StreamPage { CHOOSE, YT_MUSIC, SPOTIFY, APPLE_MUSIC }

/**
 * Stream: YT Music, Spotify or Apple Music, and one search across all three. Whichever is
 * picked, the songs stream from YouTube; Spotify and Apple Music only supply the playlist.
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
    StreamHome(folders, importState, onImport, onImportClosed, onBack)
}

/** The search box, then either the two services or what the search found in each. */
@Composable
private fun StreamHome(
    folders: List<MusicFolder>,
    importState: ImportState,
    onImport: (String, String?) -> Unit,
    onImportClosed: () -> Unit,
    onBack: () -> Unit,
    vm: StreamSearchViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val query by vm.query.collectAsStateWithLifecycle()
    val youTube by vm.youTube.collectAsStateWithLifecycle()
    var pasting by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf<String?>(null) }
    val added = remember(folders) { folders.filter { it.remote }.map { it.uri }.toSet() }
    val focus = LocalFocusManager.current
    var service by rememberSaveable { mutableStateOf(StreamPage.YT_MUSIC) }

    Column(Modifier.fillMaxSize()) {
        Header("Stream", onBack) {
            if (importState != ImportState.Working) onImportClosed()
            pasting = true
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::search,
            singleLine = true,
            placeholder = { Text("Search playlists on any service", color = Glass.Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Glass.Muted) },
            trailingIcon = if (query.isNotEmpty()) ({ TextButton(onClick = { vm.search("") }) { Text("Clear", color = Glass.Muted) } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            shape = RoundedCornerShape(26.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Glass.Text, unfocusedTextColor = Glass.Text, cursorColor = Glass.Ember,
                focusedContainerColor = Glass.FillStrong, unfocusedContainerColor = Glass.FillStrong,
                focusedBorderColor = Glass.Ember, unfocusedBorderColor = Glass.Text.copy(alpha = 0.12f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        )
        if (query.isBlank()) {
            GlassSegmented(
                listOf(StreamPage.YT_MUSIC, StreamPage.SPOTIFY, StreamPage.APPLE_MUSIC), service,
                { when (it) { StreamPage.YT_MUSIC -> "YT Music"; StreamPage.SPOTIFY -> "Spotify"; else -> "Apple Music" } },
                { service = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), height = 42.dp, fontSize = 14.sp,
            )
            Box(Modifier.weight(1f)) {
                when (service) {
                    StreamPage.SPOTIFY -> CatalogueScreen(
                        "Spotify", SourceKind.SPOTIFY, "",
                        SpotifyCatalogue.sections.map { s -> s.title to s.cards.map { it.pick() } },
                        folders, importState, onImport, onImportClosed, onBack = onBack, embedded = true,
                    )
                    StreamPage.APPLE_MUSIC -> CatalogueScreen(
                        "Apple Music", SourceKind.APPLE_MUSIC, "",
                        AppleMusicCatalogue.sections.map { s -> s.title to s.cards.map { it.pick() } },
                        folders, importState, onImport, onImportClosed, onBack = onBack, embedded = true,
                    )
                    else -> YtMusicScreen(folders, importState, onImport, onImportClosed, onBack = onBack, embedded = true)
                }
            }
        }
        if (query.isNotBlank()) {
            StatusLine(importState, tapped)
            val spotify = remember(query) { SpotifyCatalogue.search(query).map { it.pick() } }
            val appleMusic = remember(query) { AppleMusicCatalogue.search(query).map { it.pick() } }
            val pick = { card: PickCard ->
                if (importState != ImportState.Working && card.link !in added) {
                    tapped = card.title
                    onImport(card.link, card.origin)
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + LocalTabBarInset.current),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ResultRow("Spotify", SourceKind.SPOTIFY) {
                        if (spotify.isEmpty()) {
                            Note("No Spotify playlists match. Paste a Spotify link to add any public one.")
                        } else {
                            CardRow(spotify, key = { it.key }) { card ->
                                PickTile(card, card.link in added, busy = importState == ImportState.Working && tapped == card.title) { pick(card) }
                            }
                        }
                    }
                }
                item {
                    ResultRow("Apple Music", SourceKind.APPLE_MUSIC) {
                        if (appleMusic.isEmpty()) {
                            Note("No Apple Music playlists match. Paste an Apple Music link to add any public one.")
                        } else {
                            CardRow(appleMusic, key = { it.key }) { card ->
                                PickTile(card, card.link in added, busy = importState == ImportState.Working && tapped == card.title) { pick(card) }
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

/** A service's own playlists by section (Spotify, Apple Music); tapping one finds its songs on YouTube and adds it. */
@Composable
private fun CatalogueScreen(
    title: String,
    kind: SourceKind,
    blurb: String,
    sections: List<Pair<String, List<PickCard>>>,
    folders: List<MusicFolder>,
    importState: ImportState,
    onImport: (String, String?) -> Unit,
    onImportClosed: () -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    if (!embedded) BackHandler(onBack = onBack)
    var pasting by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf<String?>(null) }
    val added = remember(folders) { folders.filter { it.remote }.map { it.uri }.toSet() }
    Column(Modifier.fillMaxSize()) {
        if (!embedded) {
            Header(title, onBack) {
                if (importState != ImportState.Working) onImportClosed()
                pasting = true
            }
        }
        if (blurb.isNotBlank()) Text(blurb, color = Glass.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        StatusLine(importState, tapped, kind.color)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp + LocalTabBarInset.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(sections, key = { it.first }) { (section, cards) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section, color = Glass.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp))
                    CardRow(cards, key = { it.key }) { card ->
                        PickTile(card, card.link in added, busy = importState == ImportState.Working && tapped == card.title, showSection = false) {
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
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassIconButton("Back", onClick = onBack) { BackGlyph(Glass.Text) }
        Text(title, color = Glass.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Row(
            Modifier.height(40.dp).glass(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onPaste).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SourceGlyph(SourceKind.LINK, 16.dp)
            Text("Paste a link", color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One service's search results: its mark and name over a row of cards. */
@Composable
private fun ResultRow(title: String, kind: SourceKind, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceGlyph(kind, 18.dp)
            Text(title, color = Glass.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
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

/** A Spotify or Apple Music playlist as a card: what it's called, where it's listed, and how to get its cover. */
private class PickCard(
    val title: String,
    val section: String,
    val link: String,
    val origin: String,
    val kind: SourceKind,
    /** Unique within its service: the row's key and the cover's. */
    val key: String,
    val cover: (PlaylistCovers) -> File?,
)

private fun SpotifyCard.pick() = PickCard(title, section, link, origin, SourceKind.SPOTIFY, id) { it.spotify(id) }
private fun AppleMusicCard.pick() = PickCard(title, section, link, origin, SourceKind.APPLE_MUSIC, path) { it.appleMusic(path) }

/** One cover store for the app; it only keeps a folder. */
private var coverStore: PlaylistCovers? = null

/** The card's cover, looked up and kept the first time it shows; null until it's there, or when there's none. */
@Composable
private fun rememberCover(card: PickCard): ImageBitmap? {
    val context = LocalContext.current
    val path by produceState<String?>(null, card.kind, card.key) {
        val store = coverStore ?: PlaylistCovers(context.applicationContext).also { coverStore = it }
        value = withContext(Dispatchers.IO) { runCatching { card.cover(store) }.getOrNull()?.path }
    }
    return rememberArtwork(path)
}

/**
 * A Spotify or Apple Music playlist card: the playlist's own cover (a colour per section until
 * it loads, or offline) with the service's mark, and its name and section underneath.
 */
@Composable
private fun PickTile(card: PickCard, isAdded: Boolean, busy: Boolean, showSection: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val (fallback, _) = SECTION_COLOURS[Math.floorMod(card.section.hashCode(), SECTION_COLOURS.size)]
    val cover = rememberCover(card)
    Column(
        Modifier.width(144.dp).scale(if (focused) 1.07f else 1f).clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = if (isAdded) "Already added" else "Add ${card.title}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .border(3.dp, if (focused) card.kind.color else Color.Transparent, RoundedCornerShape(17.dp))
                .padding(5.dp)
                .clip(shape).background(fallback.copy(alpha = 0.55f), shape),
        ) {
            // The real cover carries the service's own mark; the stand-in gets ours.
            if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x73000000), RoundedCornerShape(7.dp)).padding(3.dp)) {
                SourceGlyph(card.kind, 16.dp)
            }
            when {
                busy -> Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Glass.Text, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                isAdded -> Text(
                    "Added", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(card.kind.color, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            card.title, color = if (focused) card.kind.color else Dj.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 5.dp),
        )
        if (showSection) Text(
            card.section, color = Dj.Muted, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 5.dp),
        )
    }
}

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
