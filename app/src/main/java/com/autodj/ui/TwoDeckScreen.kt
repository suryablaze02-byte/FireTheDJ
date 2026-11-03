package com.autodj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.analysis.Camelot
import com.autodj.dj.EngineState
import com.autodj.dj.VisualFrame
import com.autodj.model.TrackInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.autodj.dj.fx.FxType
import com.autodj.ui.vis.EffectLayer
import kotlin.math.roundToInt

/** Deck colours: the playing deck orange, the incoming one blue (told apart by lightness too). */
private val DeckA = Color(0xFFFF9F0A)
private val DeckB = Color(0xFF64B5FF)
private val DeckBText = Color(0xFF8FC2FF)
private val AText = Color(0xFFFFD9B8)
private val BText = Color(0xFFCFE4FF)

/**
 * Now Playing as two decks, for the phone held sideways: the playing song on the left, the
 * next one on the right, and between them the mix, the crossfader, the effects playing and the
 * playback controls. Before a mix the right deck shows the next song cued at its entry point;
 * during one both decks play and the middle follows the mix bar by bar.
 *
 * On a TV it is Now Playing: [tv] adds Home and the visualizer to the controls, widens the
 * middle for them, and gives the visualizer button a focus requester for the remote.
 */
@Composable
fun TwoDeckScreen(
    state: EngineState,
    artwork: Map<String, String>,
    visual: () -> VisualFrame?,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
    onOpenEffects: () -> Unit,
    languageOf: (String) -> String?,
    tv: TvDeckButtons? = null,
) {
    val deck = state.current
    val track = state.set.getOrNull(state.index)
    if (deck == null || track == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Pick a crate on the Mix tab to start a set.", color = Glass.Muted, fontSize = 15.sp)
        }
        return
    }
    val next = state.next
    val incoming = state.incoming
    Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Deck A: the song carrying the set.
        DeckPanel(
            label = "DECK A", color = DeckA, labelColor = Glass.EmberText,
            status = if (state.mixing || incoming != null) "Mixing out" else "Playing",
            gain = deck.gain, live = true, only = false,
            title = displayTitle(deck.title),
            details = listOfNotNull(deck.artist.ifBlank { null }, "${"%.1f".format(deck.bpm)} BPM", deck.key, languageOf(deck.uri)).joinToString(" · "),
            track = track, positionSec = deck.positionSec,
            markSec = state.exitSec, markLabel = state.exitSec?.let { "Mix out ${formatTime(it)}" },
            chip = state.trickNow?.let { "Now: $it" } ?: state.trickNext?.let { "$it in ${formatTime(state.secondsToTrick ?: 0.0)}" },
            artworkPath = artwork[deck.uri], visual = visual,
            modifier = Modifier.weight(1f),
        )
        MixerPanel(state, visual, onToggle, onStop, onSkip, onOpenEffects, tv, Modifier.width(if (tv != null) 300.dp else 220.dp).fillMaxHeight())
        // Deck B: the next song, cued, then mixing in.
        if (next != null) {
            val bLive = incoming != null
            DeckPanel(
                label = "DECK B", color = DeckB, labelColor = DeckBText,
                status = when {
                    bLive -> "Coming in"
                    state.secondsToMix != null -> "Cued · in ${formatTime(state.secondsToMix)}"
                    else -> "Cued"
                },
                gain = incoming?.gain ?: 0f, live = bLive, only = true,
                title = displayTitle(next.title),
                details = listOfNotNull(
                    next.artist.ifBlank { null },
                    state.tempoChange ?: "${next.bpm.roundToInt()} BPM",
                    incoming?.key ?: Camelot.code(next.keyPc, next.keyMinor),
                    languageOf(next.uri),
                ).joinToString(" · "),
                track = next, positionSec = incoming?.positionSec ?: (state.entrySec ?: 0.0),
                markSec = state.entrySec, markLabel = state.entrySec?.let { "In at ${formatTime(it)}" },
                chip = null,
                artworkPath = artwork[next.uri], visual = visual,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text(if (state.set.size > state.index + 1) "Picking the next song…" else "Last song of the set", color = Glass.Muted, fontSize = 14.sp)
            }
        }
    }
}

/** One deck: its record (live while it plays), the song below it, and the whole song as a waveform. */
@Composable
private fun DeckPanel(
    label: String,
    color: Color,
    labelColor: Color,
    status: String,
    gain: Float,
    live: Boolean,
    only: Boolean,
    title: String,
    details: String,
    track: TrackInfo,
    positionSec: Double,
    markSec: Double?,
    markLabel: String?,
    chip: String?,
    artworkPath: String?,
    visual: () -> VisualFrame?,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = if (live) 0.07f else 0.04f))
            .border(1.dp, color.copy(alpha = if (live) 0.55f else 0.3f), RoundedCornerShape(20.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Color(0xFF120A10), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp))
            Text(status, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("Gain ${(gain * 100).roundToInt()}%", color = Glass.Muted, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
            // The record: live spectrum while the deck plays, the cover waiting while it's cued.
            Box(Modifier.weight(1f, fill = false).size(160.dp), contentAlignment = Alignment.Center) {
                if (live) {
                    DjVisualizer(visual, artworkPath, track.uri, title, Modifier.fillMaxSize(), only = only, showLabels = false, glass = true)
                } else {
                    // Cued: the glass record waits, still, its ring of bars low until the deck comes in.
                    Canvas(Modifier.fillMaxSize()) {
                        val c = center
                        val r0 = size.minDimension * 0.36f
                        for (i in 0 until 48) {
                            val t = i / 48f * 2f * PI.toFloat() - PI.toFloat() / 2f
                            val len = (3f + 3f * abs(sin(i * 0.61f) * cos(i * 0.23f))) * density
                            val dir = Offset(cos(t), sin(t))
                            drawLine(color.copy(alpha = 0.7f), c + dir * r0, c + dir * (r0 + len), 2.4f * density, StrokeCap.Round)
                        }
                    }
                    Box(Modifier.fillMaxSize(0.66f).border(2.dp, color.copy(alpha = 0.6f), CircleShape)) {
                        GlassRecord(artworkPath, track.uri, title)
                    }
                }
            }
            Text(title, color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(details, color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (chip != null) {
                Text(chip, color = AText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x2EFF7A1A))
                        .border(1.dp, Color(0xFFFF7A1A), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        Overview(track, positionSec, markSec, color, live)
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(positionSec), color = Glass.Muted, fontSize = 12.sp)
            Text(markLabel.orEmpty(), color = DeckBText, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                textAlign = TextAlign.Center, maxLines = 1)
            Text(formatTime(track.durationSec), color = Glass.Muted, fontSize = 12.sp)
        }
    }
}

/** The whole song as bars of its energy: played part in the deck's colour, the mix point marked in blue. */
@Composable
private fun Overview(t: TrackInfo, positionSec: Double, markSec: Double?, color: Color, live: Boolean) {
    val energy = t.barEnergy
    Canvas(Modifier.fillMaxWidth().height(50.dp).semantics { contentDescription = "Waveform of ${t.title}, ${formatTime(positionSec)} of ${formatTime(t.durationSec)}" }) {
        if (energy.isEmpty() || t.durationSec <= 0) return@Canvas
        val n = energy.size
        val step = size.width / n
        val head = (positionSec / t.durationSec).toFloat().coerceIn(0f, 1f) * size.width
        for (i in 0 until n) {
            val x = i * step
            val h = (0.15f + 0.85f * energy[i].coerceIn(0f, 1f)) * size.height
            val played = x < head && live
            drawRect(
                if (played) color else Color.White.copy(alpha = 0.3f),
                topLeft = Offset(x, (size.height - h) / 2), size = Size(maxOf(1f, step - 1f), h),
            )
        }
        markSec?.let { m ->
            val mx = (m / t.durationSec).toFloat().coerceIn(0f, 1f) * size.width
            drawRect(DeckB, topLeft = Offset(mx, 0f), size = Size(2.dp.toPx(), size.height))
        }
        drawRect(Color.White, topLeft = Offset(head - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
    }
}

/**
 * The middle: the coming mix (or the mix bar by bar), the crossfader and beat
 * lock, the effects panel and the playback controls.
 */
@Composable
private fun MixerPanel(
    state: EngineState,
    visual: () -> VisualFrame?,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
    onOpenEffects: () -> Unit,
    tv: TvDeckButtons?,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Under way from the mix's start until Deck B has fully taken over (a cut ends its "mixing" at once).
        val live = state.mixing || state.incoming != null
        Card(highlight = live) {
            // The heading carries where the mix stands: the countdown, the bar, or the cut.
            val where = when {
                live && state.mixBars <= 1 -> "On the cut"
                live -> "bar ${state.mixBar} of ${state.mixBars}"
                else -> state.secondsToMix?.let { "in ${formatTime(it)}" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (live) "MIXING NOW" else "NEXT MIX", color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f))
                if (where != null) Text(where, color = if (live) Glass.Muted else Color(0xFFF2A33A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(state.recipe ?: "Planned after this song's tricks", color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            // How the next song comes in: its tempo against the set, and where in it the mix starts.
            val how = listOfNotNull(state.tempoChange, state.entrySec?.let { "B from ${formatTime(it)}" }).joinToString(" · ")
            if (how.isNotEmpty()) Text(how, color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            if (live && state.mixBars <= 1) {
                // A cut: no bars to count, just the moment passing.
                Bar(state.mixProgress, DeckA, Modifier.padding(top = 5.dp), 6.dp)
            } else if (live) {
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (b in 1..state.mixBars.coerceAtMost(16)) {
                        val c = when {
                            b < state.mixBar -> DeckA
                            b == state.mixBar -> Color.White
                            else -> Glass.FillSelected
                        }
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    }
                }
            }
        }
        Card {
            val locked = state.incoming != null
            Crossfader(state.current?.gain ?: 1f, state.incoming?.gain ?: 0f, locked, if (locked) "Beats locked · ${abs(state.phaseErrMs).roundToInt()} ms" else "Beats lock on entry")
        }
        // The panel keeps its size; on a short screen it's clipped so the controls below always show.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { EffectsPanel(visual, onOpenEffects, if (tv != null) 30.sp else 26.sp) }
        Controls(state, onToggle, onStop, onSkip, tv)
    }
}

/** What a TV adds to the decks' controls: Home on the left, the visualizer on the right (where the remote starts). */
class TvDeckButtons(val onHome: () -> Unit, val onVisualizer: () -> Unit) {
    val visualizerFocus = FocusRequester()
}

/**
 * The effects box, in the Club look's words: the newest effect playing shouted big in its own
 * colour (the loud hits glowing orange), the others still running as small dark tags under it.
 * Like the visualizer, it shows only what is playing right now. A tap opens the full effects view.
 */
@Composable
private fun EffectsPanel(visual: () -> VisualFrame?, onOpen: () -> Unit, wordSize: TextUnit) {
    // Effects playing on either deck, newest first; it changes only when one starts or ends.
    var playing by remember { mutableStateOf<List<FxType>>(emptyList()) }
    LaunchedEffect(visual) {
        val since = LinkedHashMap<FxType, Long>()
        while (true) withFrameNanos { now ->
            val types = visual()?.decks.orEmpty().flatMap { d -> d.fx.filter { it.progress < 0.97f }.map { it.type } }.toSet()
            since.keys.retainAll(types)
            types.forEach { since.getOrPut(it) { now } }
            val order = since.entries.sortedByDescending { it.value }.map { it.key }
            if (order != playing) playing = order
        }
    }
    // On a TV the remote lands on it: then it's ringed in white.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().height(EFFECTS_HEIGHT).clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Show all effects", onClick = onOpen)
            .semantics { contentDescription = if (playing.isEmpty()) "Effects" else "Effects now: " + playing.joinToString { EffectLayer.label(it) } }
            .background(if (focused) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.42f))
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("EFFECTS", color = Glass.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("All ›", color = Glass.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val top = playing.firstOrNull()
            if (top == null) {
                Text("Effects light up here as they play", color = Glass.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else {
                val color = EffectLayer.calloutColor(top)
                Text(
                    EffectLayer.label(top), color = color, fontSize = wordSize, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    style = TextStyle(shadow = Shadow(if (EffectLayer.isHot(top)) Color(0xFFFF6414) else color.copy(alpha = 0.6f), Offset.Zero, 28f)),
                )
                val rest = playing.drop(1)
                if (rest.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    rest.take(2).forEach { FxPill(EffectLayer.label(it), EffectLayer.calloutColor(it)) }
                    if (rest.size > 2) FxPill("+${rest.size - 2}", Glass.Muted)
                }
            }
        }
    }
}

/** A long effect still running, as the Club look tags it: a small dark pill. */
@Composable
private fun FxPill(label: String, color: Color) {
    Text(
        label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

private val EFFECTS_HEIGHT = 100.dp


@Composable
private fun Bar(progress: Float, color: Color, modifier: Modifier, height: Dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Glass.FillSelected)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).background(color))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (highlight) Color(0x1AFF8A3D) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (highlight) DeckA.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content,
    )
}

/** Where the mix stands between the decks, from their gains: all A at the left, all B at the right. */
@Composable
private fun Crossfader(gainA: Float, gainB: Float, locked: Boolean, lock: String) {
    val total = gainA + gainB
    val pos = if (total <= 0f) 0f else (gainB / total).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth()) {
        Text("A", color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        // Between A and B: whether the beats are locked (a crossfader needs no label).
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (locked) Color(0xFF5BD69A) else Glass.FillSelected, CircleShape))
            Text(lock, color = Glass.Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
        }
        Text("B", color = DeckBText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Box(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp).semantics { contentDescription = "Crossfader ${(pos * 100).roundToInt()}% towards deck B" }) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Glass.FillSelected))
        Canvas(Modifier.fillMaxSize()) {
            val x = pos * (size.width - 14.dp.toPx())
            drawRoundRect(Color.White, topLeft = Offset(x, 0f), size = Size(14.dp.toPx(), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}

/** Stop, play/pause and skip (on a TV, Home and the visualizer too), with "AI in control" (or the skip in progress) under play. */
@Composable
private fun Controls(state: EngineState, onToggle: () -> Unit, onStop: () -> Unit, onSkip: () -> Unit, tv: TvDeckButtons?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tv != null) 10.dp else 14.dp)) {
            if (tv != null) RoundButton(44.dp, Glass.FillStrong, "Home, the music keeps playing", tv.onHome) { HomeGlyph(Glass.Text) }
            RoundButton(44.dp, if (tv != null) Glass.FillStrong else null, "Stop the set", onStop) { StopIcon(Glass.Text, Modifier.size(22.dp)) }
            RoundButton(56.dp, Color.White, if (state.paused) "Resume the set" else "Pause the set", onToggle) {
                if (state.paused) PlayIcon(Color(0xFF120A10), Modifier.size(28.dp)) else PauseIcon(Color(0xFF120A10), Modifier.size(28.dp))
            }
            val skipping = state.skipping > 0
            RoundButton(
                44.dp, null,
                when {
                    state.skipping == 1 -> "Skip sooner, at the next bar"
                    skipping -> "Skipping at the next bar"
                    else -> "Skip to the next song"
                },
                onSkip, enabled = state.canSkip, ring = skipping,
                fillTv = tv != null,
            ) { SkipIcon(if (state.canSkip || skipping) Glass.Text else Glass.Muted, Modifier.size(24.dp)) }
            if (tv != null) {
                RoundButton(44.dp, Glass.FillStrong, "Show the visualizer", tv.onVisualizer, modifier = Modifier.focusRequester(tv.visualizerFocus)) {
                    BarsGlyph(Glass.Text)
                }
            }
        }
        val skipping = state.skipping > 0
        Text(
            if (skipping) "Skipping at the next ${if (state.skipping >= 2) "bar" else "phrase"}" else "AI in control",
            color = if (skipping) Glass.EmberText else Glass.Muted, fontSize = 12.sp,
        )
    }
}

/** A round control; on a TV ([fillTv]) unfilled ones get glass, and the one the remote is on grows, ringed in white. */
@Composable
private fun RoundButton(
    size: Dp,
    fill: Color?,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    ring: Boolean = false,
    fillTv: Boolean = false,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier.size(size).scale(if (focused) 1.12f else 1f).clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else if (fillTv) Modifier.background(Glass.FillStrong) else Modifier)
            .then(
                when {
                    focused -> Modifier.border(3.dp, Color.White, CircleShape)
                    ring -> Modifier.border(2.dp, Glass.EmberText, CircleShape)
                    else -> Modifier
                },
            )
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** A house outline, for the TV's Home button. */
@Composable
private fun HomeGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val house = Path().apply {
            moveTo(w * 0.12f, w * 0.48f); lineTo(w * 0.5f, w * 0.14f); lineTo(w * 0.88f, w * 0.48f)
            moveTo(w * 0.22f, w * 0.4f); lineTo(w * 0.22f, w * 0.86f); lineTo(w * 0.42f, w * 0.86f); lineTo(w * 0.42f, w * 0.62f)
            lineTo(w * 0.58f, w * 0.62f); lineTo(w * 0.58f, w * 0.86f); lineTo(w * 0.78f, w * 0.86f); lineTo(w * 0.78f, w * 0.4f)
        }
        drawPath(house, color, style = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
