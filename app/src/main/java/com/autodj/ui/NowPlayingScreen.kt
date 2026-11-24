package com.autodj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.dj.DoubleStart
import com.autodj.dj.EngineState
import com.autodj.dj.VisualFrame
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

@Composable
fun NowPlayingScreen(
    state: EngineState,
    artwork: Map<String, String>,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit = {},
    onFullSong: () -> Unit = {},
    onVibe: () -> Unit = {},
    visual: () -> VisualFrame? = { null },
    onFullscreen: () -> Unit = {},
    onCast: () -> Unit = {},
    /** The language the set is sung in, shown with its genre and mode; null for a mixed set. */
    setLanguage: String? = null,
    /** A song's language by its uri. */
    languageOf: (String) -> String? = { null },
    /** With nothing playing: over to the Mix tab to pick a crate. */
    onGoToMix: () -> Unit = {},
    /** Whether the playing song is liked; like takes it back, dislike hides it from every set. */
    liked: Boolean = false,
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
) {
    val deck = state.current
    val track = state.set.getOrNull(state.index)
    if (deck == null || track == null) {
        Column(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = LocalTabBarInset.current),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassPlatter(Modifier.size(168.dp)) {
                Box(Modifier.size(64.dp).glass(CircleShape), contentAlignment = Alignment.Center) { NoteGlyph(Glass.Muted) }
            }
            Text("Nothing playing yet", color = Glass.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp))
            Text("Pick a crate and FireTheDJ takes it from there.", color = Glass.Muted, fontSize = 15.sp, lineHeight = 21.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.padding(top = 24.dp).height(52.dp).clip(CircleShape).background(Glass.Ember)
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    .clickable(role = Role.Button, onClick = onGoToMix).padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Go to Mix", color = Glass.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.OnAccent)
            }
        }
        return
    }
    // A tablet upright: the record beside the song, and the next mix and what's coming up below.
    val tablet = LocalConfiguration.current.screenWidthDp >= 600
    // The set label is "Source · Genre" for a folder or playlist set, else just the genre.
    val label = state.crate.orEmpty()
    val source = label.substringBeforeLast(" · ", "")
    val genre = label.substringAfterLast(" · ")
    // A Double start: the second song plays under this one from the first beat, so both are shown.
    val partner = state.next?.takeIf { state.doubleCue != null }
    val dropped = state.doubleDropSec?.let { deck.positionSec >= it } ?: false
    val dark = LocalGlassDark.current
    val colorA = if (dark) Color(0xFFFF8A3D) else Color(0xFFC4560A)
    val colorB = if (dark) Color(0xFF6FA8E8) else Color(0xFF2F6FB0)
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The label names the vibe, so tapping it changes the vibe.
            Box(Modifier.weight(1f)) { Row(
                Modifier.height(if (tablet) 40.dp else 32.dp).glass(RoundedCornerShape(20.dp))
                    .then(if (state.canChangeVibe) Modifier.clickable(role = Role.Button, onClickLabel = "Change the vibe", onClick = onVibe) else Modifier)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(Glass.Ember, CircleShape))
                Text(listOfNotNull(genre, state.mode.label, setLanguage).filter { it.isNotBlank() }.joinToString(" · "), color = Glass.Text,
                    fontSize = if (tablet) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (state.canChangeVibe) Icon(Icons.Filled.KeyboardArrowDown, null, tint = Glass.Text, modifier = Modifier.size(16.dp))
            } }
            GlassIconButton("Open the full-screen visualizer", size = if (tablet) 48.dp else 44.dp, onClick = onFullscreen) { ExpandGlyph(Glass.Text) }
            CastButton(rememberCasting(), diameter = if (tablet) 48.dp else 44.dp, onClick = onCast)
        }
        val chosen = state.energy.takeIf { it != com.autodj.dj.SetEnergy.PLANNED }?.label
        if (source.isNotBlank() || chosen != null) {
            Row(Modifier.padding(top = 6.dp)) {
                if (source.isNotBlank()) Text("Playing from $source" + if (chosen != null) " · " else "", color = Glass.Muted, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (chosen != null) Text(chosen, color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
    // The title (up to two lines), the artist and the song's details as chips.
    val info: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A Double start: A on the left, B on the right, mirrored, under the two waveforms.
            // Like and dislike wait until it is over.
            if (partner != null) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DeckRow("A", colorA, deck.title, if (dropped) "vocal out · filtering away" else deck.artist, tablet, Alignment.Start, Modifier.weight(1f))
                DeckRow("B", colorB, partner.title, if (dropped) "bass and vocal now" else "waiting for the drop", tablet, Alignment.End, Modifier.weight(1f))
            } else Column(Modifier.weight(1f)) {
                Text(displayTitle(deck.title), color = Glass.Text, fontSize = if (tablet) 38.sp else 24.sp, lineHeight = if (tablet) 43.sp else 29.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (deck.artist.isNotBlank()) Text(deck.artist, color = Glass.Text.copy(alpha = 0.9f), fontSize = if (tablet) 20.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Like: into the Liked songs crate. Dislike: never again, and out of this song now.
            if (partner == null) Row(Modifier.padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val size = if (tablet) 52.dp else 44.dp
                GlassIconButton(
                    if (liked) "Liked. Tap to take the like back" else "Like this song", size = size,
                    fill = if (liked) Glass.Ember else Glass.Fill, onClick = onLike,
                ) {
                    Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null,
                        tint = if (liked) Glass.OnAccent else Glass.Text, modifier = Modifier.size(22.dp))
                }
                GlassIconButton("Dislike: never play this song again", size = size, onClick = onDislike) {
                    Icon(Icons.Outlined.ThumbUp, null, tint = Glass.Text, modifier = Modifier.size(21.dp).rotate(180f))
                }
            }
        }
        val ownKey = if (deck.pitchSemis != 0) "${deck.key} (${if (deck.pitchSemis > 0) "+" else ""}${deck.pitchSemis})" else deck.key
        val key = state.incoming?.key?.takeIf { partner != null }?.let { "$ownKey + $it" } ?: ownKey
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val bpm = "${"%.0f".format(deck.bpm)} BPM" + if (partner != null) " · both" else ""
            listOfNotNull(bpm, "Key $key", languageOf(deck.uri)).forEach {
                Text(it, color = Glass.Text, fontSize = if (tablet) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(Glass.FillStrong).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    // Where the set is in this song, with the point it mixes out marked in orange.
    val progress: @Composable () -> Unit = progress@{
        if (partner != null) {
            DoubleLanes(state, deck.positionSec, dropped, colorA, colorB)
            return@progress
        }
        val at = if (deck.durationSec > 0) (deck.positionSec / deck.durationSec).toFloat().coerceIn(0f, 1f) else 0f
        val out = state.exitSec?.takeIf { deck.durationSec > 0 }?.let { (it / deck.durationSec).toFloat().coerceIn(0f, 1f) }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(top = 14.dp).fillMaxWidth().height(16.dp)) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).clip(CircleShape).background(Glass.FillSelected)) {
                Box(Modifier.fillMaxWidth(at).height(6.dp).background(Glass.Text, CircleShape))
            }
            if (out != null) Box(Modifier.offset(x = maxWidth * out - 1.5.dp).width(3.dp).fillMaxHeight().background(Glass.Ember, RoundedCornerShape(2.dp))
                .semantics { contentDescription = "Mixes out at ${formatTime(state.exitSec)}" })
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(formatTime(deck.positionSec), color = Glass.Muted, fontSize = 12.sp)
            val trick = when {
                state.skipping > 0 && state.secondsToMix != null -> "Skipping at the next ${if (state.skipping >= 2) "bar" else "phrase"}"
                state.doubleCue != null -> state.doubleCue
                state.trickNow != null -> "Now: ${state.trickNow}"
                state.trickNext != null -> "${state.trickNext} in ${formatTime(state.secondsToTrick ?: 0.0)}"
                state.exitSec != null && !state.mixing -> "Mix out at ${formatTime(state.exitSec)}"
                else -> ""
            }
            Text(trick, color = if (state.trickNow != null) Glass.Ember else Glass.EmberText, fontSize = 12.sp,
                fontWeight = if (state.trickNow != null) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            Text(formatTime(deck.durationSec), color = Glass.Muted, fontSize = 12.sp)
        }
    }
    val controls: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onStop).semantics { contentDescription = "Stop the set" },
                contentAlignment = Alignment.Center,
            ) { StopIcon(Glass.Text, Modifier.size(26.dp)) }
            Box(
                Modifier.size(if (tablet) 86.dp else 76.dp).background(PlayFill, CircleShape).clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = if (state.paused) "Resume the set" else "Pause the set" },
                contentAlignment = Alignment.Center,
            ) { if (state.paused) PlayIcon(PlayInk, Modifier.size(32.dp)) else PauseIcon(PlayInk, Modifier.size(32.dp)) }
            // Skip: out at the next phrase, tapped again at the next bar; ringed while skipping.
            val skipping = state.skipping > 0
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .then(if (skipping) Modifier.border(2.dp, Glass.EmberText, CircleShape) else Modifier)
                    .clickable(enabled = state.canSkip, role = Role.Button, onClick = onSkip)
                    .semantics {
                        contentDescription = when {
                            state.skipping == 1 -> "Skip sooner, at the next bar"
                            skipping -> "Skipping at the next bar"
                            else -> "Skip to the next song"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { SkipIcon(if (state.canSkip || skipping) Glass.Text else Glass.Muted, Modifier.size(26.dp)) }
        }
        // Under play/pause, as on the sideways screen.
        if (state.showsFullSong) {
            Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) { PlayFullSongButton(state, onFullSong) }
        } else {
            Text("AI in control", color = Glass.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
    }

    if (tablet) {
        Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 16.dp, bottom = 12.dp + LocalTabBarInset.current)) {
            header()
            // The record big across the top; the song and its controls beside what's next underneath.
            Records(visual, artwork, track, deck.title, partner, state.doubleDropSec, state.mode == com.autodj.dj.MixMode.TRICKSTER, colorA, colorB, Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    info()
                    progress()
                    controls()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpNext(state, artwork, Modifier)
                    ComingUp(state, artwork, 3)
                    SetProgress(state, compact = true)
                }
            }
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp + LocalTabBarInset.current)) {
            header()
            // The glass record with the live spectrum round it; it takes whatever height is left.
            Records(visual, artwork, track, deck.title, partner, state.doubleDropSec, state.mode == com.autodj.dj.MixMode.TRICKSTER, colorA, colorB, Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp))
            info()
            progress()
            controls()
            UpNext(state, artwork, Modifier.padding(top = 10.dp))
        }
    }
}

/** The playing song's record; during a Double start, both songs as waveforms instead. */
@Composable
private fun Records(
    visual: () -> VisualFrame?, artwork: Map<String, String>, track: com.autodj.model.TrackInfo, title: String,
    partner: com.autodj.model.TrackInfo?, dropSec: Double?, weave: Boolean, colorA: Color, colorB: Color, modifier: Modifier,
) {
    if (partner == null || dropSec == null) {
        DjVisualizer(visual, artwork[track.uri], track.uri, title, modifier, glass = true)
        return
    }
    DoubleWaves(visual, artwork, track, partner, dropSec, weave, colorA, colorB, modifier)
}

/** Beats shown behind and ahead of the playhead in a Double start's waveforms: mostly ahead, to see the drop coming. */
private const val WAVE_BEHIND = 16
private const val WAVE_AHEAD = 32

/**
 * A Double start as two waveforms, A above B, scrolling past a playhead a third of the way in. Both run at
 * one tempo, so the drop (the orange dashed line) comes in on both at once and reaches the playhead
 * together. B is faint until its drop (it is only a bed); after the drop A fades. Each beat's bar is
 * its bar's energy, accented on the downbeat.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DoubleWaves(
    visual: () -> VisualFrame?, artwork: Map<String, String>, a: com.autodj.model.TrackInfo, b: com.autodj.model.TrackInfo,
    dropSec: Double, weave: Boolean, colorA: Color, colorB: Color, modifier: Modifier,
) {
    var tick by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var tags by remember { mutableStateOf<List<Pair<String, Color>>>(emptyList()) }
    // Where each song is, in beats from its first downbeat; read every frame so the waves glide.
    val beats = remember { DoubleArray(2) }
    androidx.compose.runtime.LaunchedEffect(a.uri, b.uri) {
        while (true) androidx.compose.runtime.withFrameNanos { now ->
            val decks = visual()?.decks.orEmpty()
            decks.firstOrNull { !it.incoming && it.uri == a.uri }?.let { beats[0] = (it.positionSec - a.downbeatSec) / a.beatSec }
            decks.firstOrNull { it.incoming && it.uri == b.uri }?.let { beats[1] = (it.positionSec - b.downbeatSec) / b.beatSec }
            val now2 = decks.flatMap { d -> d.fx.map { it.type.label to (if (d.incoming) colorB else colorA) } }.distinct().take(4)
            if (now2 != tags) tags = now2
            tick = now
        }
    }
    val dropBeatA = (dropSec - a.downbeatSec) / a.beatSec
    val ink = Glass.Text
    val ember = Glass.Ember
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            listOf(Triple(a, colorA, "A"), Triple(b, colorB, "B")).forEachIndexed { lane, (t, color, tag) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(44.dp)) {
                        Artwork(artwork[t.uri], t.uri, t.title, Modifier.fillMaxSize(), RoundedCornerShape(9.dp))
                        Text(tag, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = (-4).dp, y = (-6).dp).clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 5.dp))
                    }
                    Canvas(
                        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(12.dp)).background(Glass.Fill)
                            .semantics { contentDescription = "Waveform of ${t.title}; the double drop is marked" },
                    ) {
                        tick // redraw every frame
                        val beatW = size.width / (WAVE_BEHIND + WAVE_AHEAD)
                        val mid = beatW * WAVE_BEHIND
                        val at = beats[lane]
                        // A's drop is this far ahead; B's is the same distance, since both run at one tempo.
                        val toDrop = dropBeatA - beats[0]
                        val dropHere = at + toDrop
                        val first = kotlin.math.floor(at - WAVE_BEHIND).toInt()
                        for (k in first..first + WAVE_BEHIND + WAVE_AHEAD + 1) {
                            val bar = k.floorDiv(4)
                            val e = t.barEnergy.getOrNull(bar)?.coerceIn(0f, 1f) ?: continue
                            // A little texture per beat, the same every time it is drawn; the downbeat stands out.
                            val grain = 0.72f + 0.28f * kotlin.math.abs(kotlin.math.sin(k * 2.3f))
                            val amp = (0.12f + 0.88f * e) * grain * (if (k.mod(4) == 0) 1f else 0.82f)
                            val h = amp * size.height * 0.9f
                            val x = mid + ((k - at) * beatW).toFloat()
                            val beforeDrop = k < dropHere
                            // Bars from the drop (negative before it). B plays at full level all along;
                            // in Trickster it cuts out one bar in four, up to the riser (see DoubleStart).
                            val fromDrop = kotlin.math.floor((k - dropHere + 0.5) / 4).toInt()
                            val weavedOut = weave && fromDrop.mod(DoubleStart.WEAVE_BARS) == DoubleStart.WEAVE_BARS - 1 &&
                                fromDrop < -DoubleStart.TRICK_RISE_BARS
                            val alpha = when {
                                lane == 1 && weavedOut -> 0.3f
                                lane == 0 && !beforeDrop -> 0.45f
                                else -> 1f
                            }
                            drawRoundRect(color.copy(alpha = alpha), Offset(x + 1f, (size.height - h) / 2),
                                Size(beatW - 2f, h), androidx.compose.ui.geometry.CornerRadius(2f))
                        }
                        val dx = mid + (toDrop * beatW).toFloat()
                        if (dx in 0f..size.width) drawLine(ember, Offset(dx, 0f), Offset(dx, size.height), 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        drawLine(ink, Offset(mid, 0f), Offset(mid, size.height), 2.dp.toPx())
                    }
                }
            }
        }
        // Both songs' effects, centred under the waveforms.
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().height(28.dp).padding(top = 4.dp), maxLines = 1,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) { tags.forEach { (label, color) -> FxTag(label, color) } }
    }
}

/** One song of a Double start: its A or B tag, its title and what it is doing now, lined up to [align]. */
@Composable
private fun DeckRow(tag: String, color: Color, title: String, role: String, tablet: Boolean, align: Alignment.Horizontal, modifier: Modifier) {
    val textAlign = if (align == Alignment.End) TextAlign.End else TextAlign.Start
    Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tag, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 1.dp))
        Text(displayTitle(title), color = Glass.Text, fontSize = if (tablet) 22.sp else 18.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (role.isNotBlank()) Text(role, color = Glass.Muted, fontSize = 13.sp, textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A Double start's progress: one lane per song. Before the drop both fill towards it, B faint
 * (it is only a bed); from the drop both are full, B bright and A fading.
 */
@Composable
private fun DoubleLanes(state: EngineState, positionSec: Double, dropped: Boolean, colorA: Color, colorB: Color) {
    val drop = state.phases.getOrNull(1)?.start ?: 0f
    val fill = state.mixProgress.coerceIn(0f, 1f)
    Column(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(colorA to (if (dropped) 0.4f else 1f), colorB to (if (dropped) 1f else 0.45f)).forEach { (color, alpha) ->
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Glass.FillSelected)) {
                        Box(Modifier.fillMaxWidth(fill).height(8.dp).background(color.copy(alpha = alpha), CircleShape))
                    }
                }
            }
            // The drop, where both lanes meet.
            Box(Modifier.offset(x = maxWidth * drop - 1.5.dp).width(3.dp).height(21.dp).background(Glass.Ember, RoundedCornerShape(2.dp))
                .semantics { contentDescription = "Double drop at ${state.doubleDropSec?.let { formatTime(it) }.orEmpty()}" })
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(positionSec), color = Glass.Muted, fontSize = 12.sp)
            // The drop is the only countdown; after it, A just fades on its lane.
            Text(if (dropped) "" else state.doubleCue.orEmpty(), color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            Text(state.doubleEndSec?.let { formatTime(it) }.orEmpty(), color = Glass.Muted, fontSize = 12.sp)
        }
    }
}

/** The next track and the transition FireTheDJ has planned; opens the mixing view. */
@Composable
private fun UpNext(state: EngineState, artwork: Map<String, String>, modifier: Modifier) {
    val next = state.next
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (next == null) {
            // The next track is chosen once this track's own tricks (a hook replay) are done.
            val more = state.set.size > state.index + 1
            Text(
                if (more) "FireTheDJ picks the next track after the ${(state.trickNow ?: state.trickNext)?.lowercase() ?: "current trick"}" else "Last track of the set",
                color = Glass.Muted,
                fontSize = 14.sp,
            )
            return@Row
        }
        Artwork(artwork[next.uri], next.uri, next.title, Modifier.size(44.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val tempo = state.tempoChange?.let { " · $it" }.orEmpty()
            val line = when {
                state.secondsToMix != null -> "Next · ${state.recipe} in ${formatTime(state.secondsToMix)}$tempo"
                state.mixing || state.recipe != null -> "Mixing now · ${state.recipe}$tempo"
                // Chosen, but the mix itself is planned once this track's hook replay is done.
                else -> "Next · mix planned after the hook replay"
            }
            // The mix type as an orange chip, then when.
            val recipe = state.recipe
            if (recipe != null && (state.secondsToMix != null || state.mixing)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(recipe, color = Glass.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(CircleShape).background(Glass.Ember).padding(horizontal = 8.dp, vertical = 2.dp))
                    Text(
                        if (state.mixing) (if (state.doubleCue != null) "playing together" else "mixing now") else "in ${formatTime(state.secondsToMix ?: 0.0)}" + state.tempoChange?.let { " · $it" }.orEmpty(),
                        color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
            } else {
                Text(line, color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOf(displayTitle(next.title), next.artist).filter { it.isNotBlank() }.joinToString(" · "),
                color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The last song has no next one to wait for, so there's nothing to hold back. */
val EngineState.showsFullSong get() = next != null || fullSong

/** Lets this song run to its end before the DJ mixes on; orange while it's on, dimmed once the mix has begun. */
@Composable
fun PlayFullSongButton(state: EngineState, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp, fontSize: TextUnit = 15.sp) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val on = state.fullSong
    val ink = if (on) Glass.OnAccent else Glass.Text
    Row(
        modifier.height(height).alpha(if (state.canFullSong) 1f else 0.5f).clip(CircleShape)
            .background(if (on) Glass.Ember else Glass.Fill)
            .border(if (focused) 3.dp else 1.dp, if (focused) Glass.Text else Glass.Text.copy(alpha = 0.3f), CircleShape)
            .clickable(interaction, indication = null, enabled = state.canFullSong, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = ink, modifier = Modifier.size(18.dp)) else FullSongIcon(ink, Modifier.size(18.dp))
        Text(if (on) "Playing full song" else "Play full song", color = ink, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** An arrow reaching a stop line: on to the end of the song. */
@Composable
fun FullSongIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(w * x1, w * y1), Offset(w * x2, w * y2), stroke, StrokeCap.Round)
        line(0.12f, 0.5f, 0.74f, 0.5f)
        line(0.54f, 0.25f, 0.78f, 0.5f)
        line(0.54f, 0.75f, 0.78f, 0.5f)
        line(0.92f, 0.2f, 0.92f, 0.8f)
    }
}
