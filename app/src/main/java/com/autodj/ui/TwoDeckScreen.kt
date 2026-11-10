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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import com.autodj.dj.PhaseUi
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
import androidx.compose.runtime.ReadOnlyComposable
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
private val DeckBText: Color @Composable @ReadOnlyComposable get() = if (LocalGlassDark.current) Color(0xFF8FC2FF) else Color(0xFF1F6FC2)
private val AText: Color @Composable @ReadOnlyComposable get() = if (LocalGlassDark.current) Color(0xFFFFD9B8) else Color(0xFF8A4600)
private val BText: Color @Composable @ReadOnlyComposable get() = if (LocalGlassDark.current) Color(0xFFCFE4FF) else Color(0xFF1F4F8A)

/** A deck or card's glass: faint white on dark, frosted white on a light song tint. */
private val CardFill: Color @Composable @ReadOnlyComposable get() = if (LocalGlassDark.current) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
private val CardEdge: Color @Composable @ReadOnlyComposable get() = if (LocalGlassDark.current) Color.White.copy(alpha = 0.16f) else Color(0xFF1C1C1E).copy(alpha = 0.1f)

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
    onFullSong: () -> Unit,
    onOpenEffects: () -> Unit,
    languageOf: (String) -> String?,
    tv: TvDeckButtons? = null,
    onVibe: () -> Unit = {},
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
    // A big screen gets bigger records and type; there and on a TV the middle column also shows
    // the mix plan, the songs coming up and how far into the set it is.
    val roomy = tv == null && isRoomyLandscape()
    val coming = if (roomy) 3 else if (tv != null) 2 else 0
    Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (roomy) 14.dp else 8.dp)) {
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
            artworkPath = artwork[deck.uri], visual = visual, roomy = roomy,
            modifier = Modifier.weight(1f),
        )
        MixerPanel(
            state, visual, artwork, coming, onToggle, onStop, onSkip, onFullSong, onOpenEffects, onVibe, tv,
            Modifier.width(if (tv != null) 300.dp else if (roomy) 320.dp else 220.dp).fillMaxHeight(),
        )
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
                artworkPath = artwork[next.uri], visual = visual, roomy = roomy,
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
    roomy: Boolean = false,
) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(if (LocalGlassDark.current) Color.White.copy(alpha = if (live) 0.07f else 0.04f) else Color.White.copy(alpha = if (live) 0.6f else 0.42f))
            .border(1.dp, color.copy(alpha = if (live) 0.55f else 0.3f), RoundedCornerShape(20.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Color(0xFF120A10), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp))
            if (chip == null || roomy) Text(status, color = labelColor, fontSize = if (roomy) 15.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            // What this deck is doing right now: a trick on deck A.
            if (chip != null) Text(chip, color = AText, fontSize = if (roomy) 14.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x2EFF7A1A)).border(1.dp, Color(0xFFFF7A1A), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
            // The record: live spectrum while the deck plays, the cover waiting while it's cued.
            Box(Modifier.weight(1f, fill = false).size(if (roomy) 340.dp else 160.dp), contentAlignment = Alignment.Center) {
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
                    Box(Modifier.fillMaxSize(0.72f).border(2.dp, color.copy(alpha = 0.6f), CircleShape)) {
                        GlassRecord(artworkPath, track.uri, title)
                    }
                }
            }
            Text(title, color = Glass.Text, fontSize = if (roomy) 26.sp else 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(details, color = Glass.Muted, fontSize = if (roomy) 15.sp else 12.sp, maxLines = if (roomy) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Overview(track, positionSec, markSec, color, live, if (roomy) 64.dp else 50.dp)
        val small = if (roomy) 15.sp else 12.sp
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(positionSec), color = Glass.Muted, fontSize = small)
            Text(markLabel.orEmpty(), color = DeckBText, fontSize = small, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                textAlign = TextAlign.Center, maxLines = 1)
            Text(formatTime(track.durationSec), color = Glass.Muted, fontSize = small)
        }
    }
}

/** The whole song as bars of its energy: played part in the deck's colour, the mix point marked in blue. */
@Composable
private fun Overview(t: TrackInfo, positionSec: Double, markSec: Double?, color: Color, live: Boolean, height: Dp = 50.dp) {
    val energy = t.barEnergy
    // Read outside the drawing: the unplayed bars and the playhead in the text colour, light or dark.
    val ink = Glass.Text
    val markColour = DeckB
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = "Waveform of ${t.title}, ${formatTime(positionSec)} of ${formatTime(t.durationSec)}" }) {
        if (energy.isEmpty() || t.durationSec <= 0) return@Canvas
        val n = energy.size
        val step = size.width / n
        val head = (positionSec / t.durationSec).toFloat().coerceIn(0f, 1f) * size.width
        for (i in 0 until n) {
            val x = i * step
            val h = (0.15f + 0.85f * energy[i].coerceIn(0f, 1f)) * size.height
            val played = x < head && live
            drawRect(
                if (played) color else ink.copy(alpha = 0.28f),
                topLeft = Offset(x, (size.height - h) / 2), size = Size(maxOf(1f, step - 1f), h),
            )
        }
        markSec?.let { m ->
            val mx = (m / t.durationSec).toFloat().coerceIn(0f, 1f) * size.width
            drawRect(DeckB, topLeft = Offset(mx, 0f), size = Size(2.dp.toPx(), size.height))
        }
        drawRect(ink, topLeft = Offset(head - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
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
    artwork: Map<String, String>,
    /** How many songs to show coming up; 0 keeps the phone's plain column (no plan, no queue). */
    coming: Int,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
    onFullSong: () -> Unit,
    onOpenEffects: () -> Unit,
    onVibe: () -> Unit,
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
            BeatLock(state)
            if (coming > 0 && tv == null && state.phases.isNotEmpty()) {
                // Room for the plan: its phases along a strip, the mix's progress marked once it's under way.
                PhaseStrip(state.phases, if (live) state.mixProgress else null, Modifier.padding(top = 8.dp))
            } else if (live && state.mixBars <= 1) {
                // A cut: no bars to count, just the moment passing.
                Bar(state.mixProgress, DeckA, Modifier.padding(top = 5.dp), 6.dp)
            } else if (live) {
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (b in 1..state.mixBars.coerceAtMost(16)) {
                        val c = when {
                            b < state.mixBar -> DeckA
                            b == state.mixBar -> Glass.Text
                            else -> Glass.FillSelected
                        }
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    }
                }
            }
        }
        if (coming > 0) {
            // A big screen or a TV: the effects in this mix, then what's coming and how far in the set is.
            // On a TV the list gives way to a bigger Coming up.
            EffectsPanel(state, visual, onOpenEffects, if (tv != null) 28.sp else 32.sp, list = tv == null)
            if (state.canChangeVibe) VibeCard(state, onVibe, wide = true)
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ComingUp(state, artwork, coming)
                    SetProgress(state, compact = tv != null)
                }
            }
        } else {
            // The panel keeps its size; on a short screen it's clipped so the controls below always show.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { EffectsPanel(state, visual, onOpenEffects, 24.sp, list = false, Modifier.fillMaxHeight()) }
            if (state.canChangeVibe) VibeCard(state, onVibe, wide = false)
        }
        Controls(state, onToggle, onStop, onSkip, onFullSong, tv)
    }
}

/**
 * The mix's plan as a strip: each phase as long as its share of the mix, named under it, and a
 * white line at [progress] (0..1) once the mix is under way.
 */
@Composable
private fun PhaseStrip(phases: List<PhaseUi>, progress: Float?, modifier: Modifier) {
    val colours = listOf(DeckB, DeckA, Glass.Text.copy(alpha = 0.4f), DeckBText, Glass.EmberText)
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            phases.forEachIndexed { i, ph ->
                Column(Modifier.weight((ph.end - ph.start).coerceAtLeast(0.04f)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(colours[i % colours.size]))
                    Text(ph.label, color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (progress != null) BoxWithConstraints(Modifier.fillMaxWidth().height(7.dp)) {
            Box(Modifier.offset(x = maxWidth * progress.coerceIn(0f, 1f) - 1.dp).width(2.dp).fillMaxHeight().background(Glass.Text))
        }
    }
}

/** The next songs in the set, with their cover, BPM and key; the first is the one the next mix brings in. */
@Composable
internal fun ComingUp(state: EngineState, artwork: Map<String, String>, count: Int, onVibe: (() -> Unit)? = null) {
    val ahead = state.set.drop(state.index + 1).take(count)
    if (ahead.isEmpty()) return
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COMING UP", color = Glass.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            if (onVibe != null) VibeLinkButton(onVibe)
        }
        ahead.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Artwork(artwork[t.uri], t.uri, t.title, Modifier.size(38.dp), RoundedCornerShape(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayTitle(t.title), color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.bpm.roundToInt()} BPM · ${Camelot.code(t.keyPc, t.keyMinor)}", color = Glass.Muted, fontSize = 12.sp, maxLines = 1)
                }
                if (i == 0) Text("NEXT", color = DeckBText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * How far into the set: which song of how many, the minutes played and about how long is left.
 * [compact] fits it on one line, for the TV's short middle column.
 */
@Composable
internal fun SetProgress(state: EngineState, compact: Boolean = false) {
    val total = state.set.size
    if (total == 0) return
    val now = state.current?.positionSec ?: 0.0
    val played = state.set.take(state.index).sumOf { it.durationSec } + now
    val left = (state.set.drop(state.index).sumOf { it.durationSec } - now).coerceAtLeast(0.0)
    if (compact) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Song ${state.index + 1} of $total", color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${minutes(played)} in · ${minutes(left)} left", color = Glass.Muted, fontSize = 14.sp, maxLines = 1)
            }
        }
        return
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Song ${state.index + 1} of $total", color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${minutes(played)} in", color = Glass.Muted, fontSize = 12.sp)
        }
        Bar((played / (played + left).coerceAtLeast(1.0)).toFloat(), Glass.Text, Modifier.padding(top = 7.dp), 5.dp)
        Text("About ${minutes(left)} left", color = Glass.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

/** "14 min" or "3 h 20 min". */
private fun minutes(sec: Double): String {
    val m = (sec / 60).roundToInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** What a TV adds to the decks' controls: Home on the left, the visualizer on the right (where the remote starts). */
class TvDeckButtons(val onHome: () -> Unit, val onVisualizer: () -> Unit) {
    val visualizerFocus = FocusRequester()
}

/**
 * The effects box. The effect playing now is shouted in the Club look's words and its own colour
 * (the loud hits glowing orange), with its deck, the beats it has left and a bar; between effects
 * it says the trick playing now, or what comes next and when, so it's never empty. [list] adds
 * every effect planned for this mix, lit as it plays, for the roomier middle columns. A tap (or OK
 * on a TV) opens the full effects view.
 */
@Composable
private fun EffectsPanel(state: EngineState, visual: () -> VisualFrame?, onOpen: () -> Unit, wordSize: TextUnit, list: Boolean, modifier: Modifier = Modifier, rows: Int = 5) {
    // The newest effect playing on either deck; it changes only when one starts or ends.
    var playing by remember { mutableStateOf<FxType?>(null) }
    LaunchedEffect(visual) {
        val since = LinkedHashMap<FxType, Long>()
        while (true) withFrameNanos { now ->
            val types = visual()?.decks.orEmpty().flatMap { d -> d.fx.filter { it.progress < 0.97f }.map { it.type } }.toSet()
            since.keys.retainAll(types)
            types.forEach { since.getOrPut(it) { now } }
            val newest = since.entries.maxByOrNull { it.value }?.key
            if (newest != playing) playing = newest
        }
    }
    // This mix's effects: the ones playing and the ones planned (the rest stand by).
    val planned = state.fx.filter { it.active || it.detail == "Planned" }
    val active = state.fx.firstOrNull { it.active }
    // On a TV the remote lands on it: then it's ringed in white.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    val hot = playing != null || state.trickNow != null
    Column(
        modifier.fillMaxWidth().clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Show all effects", onClick = onOpen)
            .semantics { contentDescription = playing?.let { "Effect now: " + EffectLayer.label(it) } ?: "Effects" }
            .background(if (focused) Glass.FillSelected else if (hot) Color(0x1AFF8A3D) else CardFill)
            .border(if (focused) 3.dp else 1.dp, if (focused) Glass.Text else if (hot) DeckA.copy(alpha = 0.45f) else CardEdge, shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EFFECTS", color = Glass.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text(if (state.fx.isNotEmpty()) "All ${state.fx.size} ›" else "All ›", color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val top = playing
        val trick = state.trickNow
        when {
            top != null -> {
                val color = EffectLayer.calloutColor(top)
                Text(
                    EffectLayer.label(top), color = color, fontSize = wordSize, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    style = TextStyle(shadow = Shadow(if (EffectLayer.isHot(top)) Color(0xFFFF6414) else color.copy(alpha = 0.6f), Offset.Zero, 28f)),
                )
                if (active != null) {
                    Bar(active.progress, DeckA, Modifier.padding(top = 1.dp), 4.dp)
                    Text(
                        listOfNotNull(active.deck?.let { "Deck $it" }, "${active.beatsLeft.roundToInt()} beats left").joinToString(" · "),
                        color = Glass.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
            }
            trick != null -> Text(
                trick.uppercase(), color = Glass.EmberText, fontSize = wordSize * 0.8f, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                style = TextStyle(shadow = Shadow(Color(0xFFFF6414), Offset.Zero, 22f)),
            )
            else -> {
                // Nothing playing: what's next, so the box always says something.
                val next = when {
                    state.trickNext != null -> "${state.trickNext} in ${formatTime(state.secondsToTrick ?: 0.0)}"
                    state.recipe != null && state.secondsToMix != null -> "${state.recipe} in ${formatTime(state.secondsToMix)}"
                    else -> "Picking the next mix"
                }
                Text("Next", color = Glass.Muted, fontSize = 12.sp)
                Text(next, color = Glass.Text, fontSize = if (list) 18.sp else 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (list && planned.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                planned.take(rows).forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).background(if (f.active) DeckA else Glass.FillSelected, CircleShape))
                        Text(f.name, color = if (f.active) Glass.Text else Glass.Muted, fontSize = 14.sp, fontWeight = if (f.active) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (f.active) "now" else "planned", color = if (f.active) Glass.EmberText else Glass.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}



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
            .background(if (highlight) Color(0x1AFF8A3D) else CardFill)
            .border(1.dp, if (highlight) DeckA.copy(alpha = 0.55f) else CardEdge, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content,
    )
}

/**
 * The vibe in force and the way to change it, under Effects: a slim card, since it changes a few
 * times a night while the effect changes every few seconds. On a TV the remote rings it in white.
 */
@Composable
private fun VibeCard(state: EngineState, onVibe: () -> Unit, wide: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (focused) Glass.FillSelected else CardFill)
            .border(if (focused) 3.dp else 1.dp, if (focused) Glass.Text else CardEdge, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Change the vibe", onClick = onVibe)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // One line, so Effects above keeps room for its effect name.
        Text("VIBE", color = Glass.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(if (wide) "${state.energy.label} · ${state.mode.label}" else state.energy.label, color = Glass.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.height(28.dp).clip(CircleShape).background(Glass.FireSelected).border(1.dp, Glass.Ember, CircleShape).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Change", color = Glass.EmberText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

/**
 * Whether the beats are locked: green once B is in and its beats sit on A's, how far apart they
 * are. The decks show the blend itself, so no crossfader is drawn.
 */
@Composable
private fun BeatLock(state: EngineState) {
    val locked = state.incoming != null
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(if (locked) Color(0xFF5BD69A) else Glass.FillSelected, CircleShape))
        Text(if (locked) "Beats locked · ${abs(state.phaseErrMs).roundToInt()} ms" else "Beats lock on entry", color = Glass.Muted, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.padding(start = 5.dp))
    }
}

/** Stop, play/pause and skip (on a TV, Home and the visualizer too), with "AI in control" (or the skip in progress) under play. */
@Composable
private fun Controls(state: EngineState, onToggle: () -> Unit, onStop: () -> Unit, onSkip: () -> Unit, onFullSong: () -> Unit, tv: TvDeckButtons?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tv != null) 10.dp else 14.dp)) {
            if (tv != null) RoundButton(44.dp, Glass.FillStrong, "Home, the music keeps playing", tv.onHome) { HomeGlyph(Glass.Text) }
            RoundButton(44.dp, if (tv != null) Glass.FillStrong else null, "Stop the set", onStop) { StopIcon(Glass.Text, Modifier.size(22.dp)) }
            RoundButton(56.dp, PlayFill, if (state.paused) "Resume the set" else "Pause the set", onToggle) {
                if (state.paused) PlayIcon(PlayInk, Modifier.size(28.dp)) else PauseIcon(PlayInk, Modifier.size(28.dp))
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
        if (!skipping && state.showsFullSong) {
            PlayFullSongButton(state, onFullSong, height = 32.dp, fontSize = 13.sp)
        } else {
            Text(
                if (skipping) "Skipping at the next ${if (state.skipping >= 2) "bar" else "phrase"}" else "AI in control",
                color = if (skipping) Glass.EmberText else Glass.Muted, fontSize = 12.sp,
            )
        }
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
                    focused -> Modifier.border(3.dp, Glass.Text, CircleShape)
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
