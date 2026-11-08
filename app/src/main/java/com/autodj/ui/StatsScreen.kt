package com.autodj.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.autodj.dj.ListeningStats
import com.autodj.dj.PlayHistory
import com.autodj.dj.TopItem
import com.autodj.dj.formatHeard
import com.autodj.dj.listeningStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StatsRange(val label: String) { MONTH("30 days"), ALL("All time") }

/** The start of [range], in the clock [com.autodj.dj.Play.at] uses. */
fun StatsRange.sinceMs(): Long = if (this == StatsRange.MONTH) System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 else 0

/** Three bars: the mark for Your stats. */
@Composable
fun StatsGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width / 5
        listOf(0.45f, 1f, 0.7f).forEachIndexed { i, h ->
            val bar = this.size.height * h
            drawRoundRect(tint, Offset(i * w * 2, this.size.height - bar), Size(w, bar), CornerRadius(w / 2))
        }
    }
}

/** A song's cover, or a plain tile while it has none. */
@Composable
fun StatsCover(path: String?, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val image = rememberArtwork(path)
    Box(modifier.size(size).clip(shape).background(Glass.Ember.copy(alpha = 0.45f))) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** A vinyl record with its grooves, centred at [c]. */
fun DrawScope.drawRecord(c: Offset, r: Float, disc: Color, label: Color, groove: Color) {
    drawCircle(disc, r, c)
    listOf(0.86f, 0.72f, 0.58f).forEach { drawCircle(groove, r * it, c, style = Stroke(1.dp.toPx())) }
    drawCircle(label, r * 0.36f, c)
    drawCircle(disc, r * 0.05f, c)
}

@Composable
fun StatsScreen(artwork: Map<String, String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { PlayHistory(context.applicationContext) }
    var range by rememberSaveable { mutableStateOf(StatsRange.MONTH) }
    var cleared by remember { mutableIntStateOf(0) }
    var wrapped by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val stats by produceState<ListeningStats?>(null, range, cleared) {
        value = withContext(Dispatchers.IO) { listeningStats(history.all(), range.sinceMs()) }
    }
    val s = stats
    if (wrapped && s != null && s.sets > 0) {
        WrappedScreen(s, range, artwork, onBack = { wrapped = false })
        return
    }
    BackHandler(onBack = onBack)
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear your listening history?") },
            text = { Text("Removes every set and song from your stats. Your music stays.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { history.clear() }
                        cleared++
                    }
                }) { Text("Clear history", color = Dj.Warn) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton("Back", onClick = onBack) { Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Glass.Text) }
                Text("Your stats", color = Glass.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                GlassSegmented(StatsRange.entries, range, { it.label }, { range = it }, Modifier.width(176.dp), height = 44.dp)
            }
            when {
                s == null -> Unit
                s.sets == 0 -> Text(
                    if (range == StatsRange.MONTH) "Nothing in the last 30 days. Keep mixing and your stats build up here."
                    else "Nothing yet. Start a mix and your stats build up here as you play.",
                    color = Glass.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp),
                )
                else -> StatsBody(s, artwork, onWrapped = { wrapped = true })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your history stays on this phone.", color = Glass.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }) { Text("Clear history", color = Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun StatsBody(s: ListeningStats, artwork: Map<String, String>, onWrapped: () -> Unit) {
    val heroShape = RoundedCornerShape(28.dp)
    val dark = LocalGlassDark.current
    Box(
        Modifier.fillMaxWidth().height(238.dp).clip(heroShape).glass(heroShape)
            .drawBehind {
                val corner = Offset(size.width, 0f)
                drawCircle(Brush.radialGradient(listOf(Glass.Ember.copy(alpha = 0.7f), Color.Transparent), corner, size.height * 0.95f), size.height * 0.95f, corner)
                drawRecord(Offset(size.width - 40.dp.toPx(), 40.dp.toPx()), 104.dp.toPx(), Color(0xFF1B1B1F), Glass.Ember, Color.White.copy(alpha = 0.14f))
            }
            .padding(20.dp),
    ) {
        Text("HOURS MIXED", color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(formatHeard(s.heardSec), color = Glass.Text, fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, maxLines = 1)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                StatsFigure(s.sets.toString(), if (s.sets == 1) "set played" else "sets played")
                StatsFigure(formatHeard(s.longestSec), "longest set")
            }
        }
    }

    StatsHeading("Favourites")
    val tile = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().height(92.dp).glass(tile).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Crate", color = Glass.Muted, fontSize = 14.sp)
            Text(s.crate.orEmpty(), color = Glass.Text, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(100.dp).height(52.dp)) {
            val turns = listOf(-8f, 0f, 8f)
            s.crateUris.forEachIndexed { i, uri ->
                StatsCover(artwork[uri], 44.dp, RoundedCornerShape(10.dp), Modifier.align(Alignment.CenterStart).offset(x = 28.dp * i).rotate(turns[i]))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Genre" to s.genre, "Language" to s.language).forEach { (label, value) ->
            Column(Modifier.weight(1f).glass(tile).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(label, color = Glass.Muted, fontSize = 14.sp)
                Text(value ?: "Not known yet", color = Glass.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (s.artists.isNotEmpty()) {
        StatsHeading("Top artists")
        val top = s.artists.first().plays.toFloat()
        s.artists.forEachIndexed { i, a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}", color = Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(14.dp))
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Glass.Fill)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(a.plays / top).clip(RoundedCornerShape(12.dp)).background(Glass.Ember.copy(alpha = if (i == 0) (if (dark) 0.6f else 0.75f) else 0.4f)))
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${a.plays}", color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (s.songs.isNotEmpty()) {
        StatsHeading("Most-played songs")
        s.songs.forEach { song ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsCover(artwork[song.uri], 48.dp, RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.name, color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Glass.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${song.plays} ${if (song.plays == 1) "play" else "plays"}", color = Glass.Muted, fontSize = 14.sp)
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(24.dp)).background(Glass.Ember)
            .clickable(role = Role.Button, onClick = onWrapped).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("See your Wrapped", color = Glass.OnAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("Cards to share", color = Glass.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.OnAccent)
    }
}

@Composable
private fun StatsHeading(text: String) = Text(text, color = Glass.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)

@Composable
private fun StatsFigure(value: String, label: String) {
    Column {
        Text(value, color = Glass.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Glass.Muted, fontSize = 14.sp)
    }
}

private enum class CardKind { HOURS, SETS, SOUND, ARTIST, SONG, SUMMARY }

private val CARD_W = 300.dp
private val CARD_H = 533.dp
private val Ink = Color(0xFF0C0C10)
private val Amber = Color(0xFFFFB84D)

/** The share cards, swiped through; what's shared is the card alone, not the buttons around it. */
@Composable
private fun WrappedScreen(s: ListeningStats, range: StatsRange, artwork: Map<String, String>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kinds = CardKind.entries.filter { (it != CardKind.ARTIST || s.artists.isNotEmpty()) && (it != CardKind.SONG || s.songs.isNotEmpty()) }
    val pager = rememberPagerState { kinds.size }
    val layers = List(CardKind.entries.size) { rememberGraphicsLayer() }
    var sharing by remember { mutableStateOf(false) }
    val last = pager.currentPage == kinds.lastIndex

    Column(Modifier.fillMaxSize().background(Color(0xFF16161B)), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DarkGlass { GlassIconButton("Back", onClick = onBack) { Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Color.White) } }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.indices.forEach { i ->
                    Box(Modifier.size(8.dp).background(if (i == pager.currentPage) Glass.Ember else Color.White.copy(alpha = 0.3f), CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            val scale = minOf(1f, maxHeight / CARD_H, maxWidth / CARD_W)
            // Every card stays laid out, so Share all can capture the ones off screen.
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = kinds.size) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                        CaptureCard(layers[page]) { WrappedCard(kinds[page], s, range, artwork) }
                    }
                }
            }
        }
        Box(
            Modifier.padding(bottom = 20.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (last) Glass.Ember else Color.White)
                .clickable(enabled = !sharing, role = Role.Button) {
                    sharing = true
                    scope.launch {
                        val pages = if (last) kinds.indices.toList() else listOf(pager.currentPage)
                        runCatching { shareCards(context, pages.map { layers[it] }) }
                        sharing = false
                    }
                }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (last) "Share all" else "Share", color = Color(0xFF1B1B1F), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

/** A card at one fixed size, whatever the phone's text size, drawn into [layer] so it can be saved. */
@Composable
private fun CaptureCard(layer: GraphicsLayer, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Box(
            Modifier.size(CARD_W, CARD_H).drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) { content() }
    }
}

private suspend fun shareCards(context: Context, layers: List<GraphicsLayer>) {
    val bitmaps = layers.map { it.toImageBitmap().asAndroidBitmap() }
    val uris = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        bitmaps.mapIndexed { i, b ->
            val file = File(dir, "firethedj-wrapped-${i + 1}.png")
            file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        }
    }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.setType("image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share your Wrapped"))
}

@Composable
private fun WrappedCard(kind: CardKind, s: ListeningStats, range: StatsRange, artwork: Map<String, String>) {
    val month = range == StatsRange.MONTH
    when (kind) {
        CardKind.HOURS -> {
            val hours = s.heardSec / 3600
            val number = if (hours > 0) hours.toString() else (s.heardSec / 60).coerceAtLeast(1).toString()
            val unit = if (hours > 0) (if (hours == 1) "hour mixed" else "hours mixed") else if (s.heardSec < 120) "minute mixed" else "minutes mixed"
            CardFrame(Glass.Ember, Color(0xFF1B1B1F), Modifier.drawBehind {
                drawRecord(Offset(size.width + 40.dp.toPx(), 60.dp.toPx()), 170.dp.toPx(), Color(0xFF1B1B1F), Color.White, Color.White.copy(alpha = 0.14f))
            }) {
                Text(if (month) "Your month on the decks" else "Your time on the decks", fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(130.dp))
                Column {
                    Text(number, fontSize = when (number.length) { 1, 2 -> 176.sp; 3 -> 116.sp; else -> 88.sp }, lineHeight = when (number.length) { 1, 2 -> 150.sp; 3 -> 104.sp; else -> 82.sp },
                        fontWeight = FontWeight.Black, letterSpacing = (-0.04).em, maxLines = 1)
                    Text(unit, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.02).em)
                }
            }
        }
        CardKind.SETS -> CardFrame(Ink, Color.White) {
            Column {
                BigLine(s.sets.toString(), 104.sp, Amber)
                Text(if (s.sets == 1) "set played" else "sets played", fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
            }
            Column {
                val bars = s.setLengths.takeLast(36)
                val top = (bars.maxOrNull() ?: 1).toFloat()
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    val gap = 4.dp.toPx()
                    val w = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtMost(28.dp.toPx())
                    bars.forEachIndexed { i, b ->
                        val h = size.height * (b / top).coerceAtLeast(0.03f)
                        drawRoundRect(if (b.toFloat() == top) Amber else Color(0xFF55555F), Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
                    }
                }
                Text("Longest set", fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
                Text(formatHeard(s.longestSec), color = Amber, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
            }
        }
        CardKind.SOUND -> CardFrame(Color.White, Color(0xFF1B1B1F)) {
            Text("Your sound", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SoundLine("Crate", s.crate.orEmpty(), 38.sp)
                SoundLine("Genre", s.genre.orEmpty(), 50.sp)
                s.language?.let { SoundLine("Language", it, 50.sp) }
            }
        }
        CardKind.ARTIST -> CardFrame(Ink, Color.White) {
            Text("Your number one artist", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Column {
                val a = s.artists.first()
                Text(a.name, color = Amber, fontSize = if (a.name.length > 14) 50.sp else 66.sp, lineHeight = if (a.name.length > 14) 52.sp else 66.sp,
                    fontWeight = FontWeight.Black, letterSpacing = (-0.03).em, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${a.plays} ${if (a.plays == 1) "play" else "plays"}", fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            }
            RankList(s.artists.drop(1), Amber, 20.sp)
        }
        CardKind.SONG -> CardFrame(Glass.Ember, Color(0xFF1B1B1F)) {
            Text("Your most-played song", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val top = s.songs.first()
                Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawRecord(center, size.width / 2, Color(0xFF1B1B1F), Color(0xFF2A2A30), Color.White.copy(alpha = 0.14f)) }
                    StatsCover(artwork[top.uri], 58.dp, CircleShape)
                }
                Text(top.name, fontSize = if (top.name.length > 10) 34.sp else 60.sp, lineHeight = if (top.name.length > 10) 36.sp else 62.sp,
                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                Text("${top.artist} · ${top.plays} ${if (top.plays == 1) "play" else "plays"}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RankList(s.songs.drop(1), Color(0xFF1B1B1F), 17.sp)
        }
        CardKind.SUMMARY -> CardFrame(Color.White, Color(0xFF1B1B1F), brandBar = true) {
            Text(if (month) "My month in one card" else "My FireTheDJ in one card", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    SummaryFigure(formatHeard(s.heardSec).substringBefore(" m").let { if (" h " in it) it.substringBefore(" h ") + " h" else "$it m" }, "mixed")
                    SummaryFigure(s.sets.toString(), if (s.sets == 1) "set" else "sets")
                }
                SummaryFigure(formatHeard(s.longestSec), "longest set", 30.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Genre" to s.genre, "Language" to s.language, "Artist" to s.artists.firstOrNull()?.name, "Song" to s.songs.firstOrNull()?.name).forEach { (label, value) ->
                    if (value != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label, color = Color(0xFF5F5F69), fontSize = 16.sp)
                        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CardFrame(ground: Color, ink: Color, modifier: Modifier = Modifier, brandBar: Boolean = false, content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(color = ink)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(ground).then(modifier)) {
            Column(Modifier.fillMaxSize().padding(24.dp).padding(bottom = if (brandBar) 0.dp else 26.dp), verticalArrangement = Arrangement.SpaceBetween) { content() }
            if (brandBar) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(54.dp).background(Glass.Ember).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                Text("FireTheDJ", color = Color(0xFF1B1B1F), fontSize = 16.sp, fontWeight = FontWeight.Black)
            } else Text("FireTheDJ", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
        }
    }
}

@Composable
private fun BigLine(text: String, size: TextUnit, color: Color) =
    Text(text, color = color, fontSize = size, lineHeight = size * 0.95f, fontWeight = FontWeight.Black, letterSpacing = (-0.03).em, maxLines = 1)

@Composable
private fun SoundLine(label: String, value: String, size: TextUnit) {
    Column {
        Text(label, color = Color(0xFFA85A00), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val fit = if (value.length > 12) minOf(size.value, 34f).sp else size
        Text(value, fontSize = fit, lineHeight = fit * 1.02f, fontWeight = FontWeight.Black, letterSpacing = (-0.02).em, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RankList(items: List<TopItem>, rank: Color, size: TextUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, a ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 2}", color = rank, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                Text(a.name, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SummaryFigure(value: String, label: String, size: TextUnit = 44.sp) {
    Column {
        Text(value, fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, letterSpacing = (-0.03).em)
        Text(label, color = Color(0xFF5F5F69), fontSize = 14.sp)
    }
}
