package com.autodj.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodj.remote.SpotifyProvider
import com.autodj.remote.YtPlaylistCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * YT Music: YouTube Music's own curated playlists by mood and genre. Tapping one imports it
 * like a pasted link; a link can still be pasted for anything else.
 */
@Composable
fun YtMusicScreen(
    folders: List<MusicFolder>,
    importState: ImportState,
    /** A playlist link, and where in YT Music it was picked ("Telugu › Party today"; null when pasted). */
    onImport: (String, String?) -> Unit,
    onImportClosed: () -> Unit,
    onBack: () -> Unit,
    vm: YtMusicViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    var pasting by remember { mutableStateOf(false) }
    /** The card last tapped, so the status line can name it. */
    var tapped by remember { mutableStateOf<YtPlaylistCard?>(null) }
    val added = remember(folders) { folders.filter { it.remote }.map { it.uri }.toSet() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back to Library", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { BackGlyph(Dj.Text) }
            Text("YT Music", color = Dj.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    // Start the dialog clean, not on the result of a playlist tapped here.
                    if (importState != ImportState.Working) onImportClosed()
                    pasting = true
                },
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text("Paste a link", color = Dj.Text, fontSize = 13.sp)
            }
        }
        Text(
            "Curated playlists from YouTube Music. Tap one to add it; its tracks are analysed, then mixed.",
            color = Dj.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )

        when (val c = categories) {
            Loadable.Loading -> Centered { CircularProgressIndicator(color = YT_RED) }
            is Loadable.Failed -> Centered { Retry("Couldn't load YouTube Music: ${c.reason}", vm::loadCategories) }
            is Loadable.Ready -> {
                c.value.forEach { section ->
                    Text(
                        section.title, color = Dj.Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp),
                    )
                    // The picked category is scrolled into view, so it's clear what the shelves below are.
                    val row = rememberLazyListState()
                    LaunchedEffect(selected) {
                        val at = section.categories.indexOf(selected)
                        if (at >= 0) row.animateScrollToItem((at - 1).coerceAtLeast(0))
                    }
                    LazyRow(state = row, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(section.categories) { cat ->
                            CrateChip(cat.title, cat == selected) { vm.select(cat) }
                        }
                    }
                }
                StatusLine(importState, tapped?.title)
                when (val s = shelves) {
                    Loadable.Loading -> Centered { CircularProgressIndicator(color = YT_RED) }
                    is Loadable.Failed -> Centered { Retry(s.reason) { selected?.let(vm::select) } }
                    is Loadable.Ready -> LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(s.value) { shelf ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    shelf.title, color = Dj.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                                // Vertical room too: the remote-focused card grows a little.
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(shelf.playlists, key = { it.playlistId }) { card ->
                                        val busy = importState == ImportState.Working && tapped == card
                                        PlaylistCard(card, isAdded = card.link in added, busy = busy) {
                                            if (importState != ImportState.Working && card.link !in added) {
                                                tapped = card
                                                onImport(card.link, listOfNotNull(selected?.title, shelf.title).joinToString(" › "))
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
    }
    if (pasting) {
        ImportDialog(youTube = true, importState, { link -> onImport(link, null) }) {
            pasting = false
            onImportClosed()
        }
    }
}

/**
 * What happened to the last playlist tapped ([tapped], its name): being added (with how many
 * Spotify songs have been looked up on YouTube so far), added, or why it couldn't be.
 */
@Composable
internal fun StatusLine(state: ImportState, tapped: String?, color: Color = YT_RED) {
    if (tapped == null) return
    val matching by SpotifyProvider.matching.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state) {
            ImportState.Working -> {
                val found = matching
                Text(
                    if (found == null) "Adding \"$tapped\"…" else "Adding \"$tapped\": finding songs on YouTube, ${found.first} of ${found.second}",
                    color = Dj.Text, fontSize = 13.sp,
                )
                if (found == null || found.second == 0) LinearProgressIndicator(Modifier.fillMaxWidth(), color = color, trackColor = Dj.Border)
                else LinearProgressIndicator({ found.first / found.second.toFloat() }, Modifier.fillMaxWidth(), color = color, trackColor = Dj.Border)
            }
            is ImportState.Done -> Text(
                "Added \"${state.name}\" · ${state.tracks} tracks" +
                    (if (state.missing > 0) " (${state.missing} not found on YouTube)" else "") +
                    ". They show up in the Library as they're analysed.",
                color = Dj.BlueText, fontSize = 13.sp,
            )
            is ImportState.Failed -> Text(state.reason, color = Dj.Warn, fontSize = 13.sp)
            ImportState.Idle -> Unit
        }
    }
}

@Composable
internal fun PlaylistCard(card: YtPlaylistCard, isAdded: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    // On a TV the remote moves focus: the focused card grows and gets a fire-coloured edge.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier.width(144.dp).scale(if (focused) 1.07f else 1f).clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = if (isAdded) "Already added" else "Add ${card.title}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                // An accent ring around the cover, with a gap, on the card the remote is on.
                .border(3.dp, if (focused) Dj.Accent else Color.Transparent, RoundedCornerShape(17.dp))
                .padding(5.dp)
                .clip(shape).background(Dj.Surface).border(1.dp, Dj.Border, shape),
        ) {
            rememberRemoteImage(card.thumbnailUrl)?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            when {
                busy -> Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                isAdded -> Text(
                    "Added", color = Dj.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Dj.Accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            card.title, color = if (focused) Dj.Accent else Dj.Text, fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun Retry(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = Dj.Warn, fontSize = 13.sp)
        TextButton(onClick = onRetry) { Text("Try again", color = Dj.Accent) }
    }
}

@Composable
internal fun BackGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val s = w * 0.13f
        drawLine(color, Offset(w * 0.68f, w * 0.12f), Offset(w * 0.30f, w * 0.5f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.30f, w * 0.5f), Offset(w * 0.68f, w * 0.88f), s, StrokeCap.Round)
    }
}

/** YouTube Music's red, for this page's accents. */
internal val YT_RED = Color(0xFFFF4E45)

/** Cover art by URL, kept in memory for the session so scrolling back doesn't refetch it. */
private val imageCache = LruCache<String, ImageBitmap>(80)

@Composable
private fun rememberRemoteImage(url: String?): ImageBitmap? {
    val image by produceState(url?.let { imageCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { download(url) }.getOrNull() }?.also { imageCache.put(url, it) }
    }
    return image
}

private fun download(url: String): ImageBitmap? {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 10_000
    try {
        if (conn.responseCode != 200) return null
        val bytes = conn.inputStream.use { it.readBytes() }
        // Cards are 144 dp: about 400 px is plenty.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 400) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    } finally {
        conn.disconnect()
    }
}
