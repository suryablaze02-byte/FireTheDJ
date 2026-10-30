package com.autodj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.autodj.dj.FxStatus
import com.autodj.dj.VisualFrame
import com.autodj.model.TrackInfo
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Deck colours: the playing deck orange, the incoming one blue (told apart by lightness too). */
private val DeckA = Color(0xFFFF8A3D)
private val DeckB = Color(0xFF5AA9FF)
private val DeckBText = Color(0xFF8FC2FF)
private val AText = Color(0xFFFFD9B8)
private val BText = Color(0xFFCFE4FF)

/**
 * Now Playing as two decks, for the phone held sideways: the playing song on the left, the
 * next one on the right, and between them the mix, the crossfader, the effects playing and the
 * playback controls. Before a mix the right deck shows the next song cued at its entry point;
 * during one both decks play and the middle follows the mix bar by bar.
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
            title = deck.title,
            details = listOfNotNull(deck.artist.ifBlank { null }, "${"%.1f".format(deck.bpm)} BPM", deck.key, languageOf(deck.uri)).joinToString(" · "),
            track = track, positionSec = deck.positionSec,
            markSec = state.exitSec, markLabel = state.exitSec?.let { "Mix out ${formatTime(it)}" },
            chip = state.trickNow?.let { "Now: $it" } ?: state.trickNext?.let { "$it in ${formatTime(state.secondsToTrick ?: 0.0)}" },
            artworkPath = artwork[deck.uri], visual = visual,
            modifier = Modifier.weight(1f),
        )
        MixerPanel(state, onToggle, onStop, onSkip, onOpenEffects, Modifier.width(220.dp).fillMaxHeight())
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
                title = next.title,
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
            Text(label, color = Color(0xFF120A10), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
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
                    Box(Modifier.size(112.dp).border(1.5.dp, DeckB.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                        Artwork(artworkPath, track.uri, title, Modifier.size(92.dp).alpha(0.7f), CircleShape)
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
            Text(formatTime(positionSec), color = Glass.Muted, fontSize = 11.sp)
            Text(markLabel.orEmpty(), color = DeckBText, fontSize = 11.sp, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                textAlign = TextAlign.Center, maxLines = 1)
            Text(formatTime(track.durationSec), color = Glass.Muted, fontSize = 11.sp)
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
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
    onOpenEffects: () -> Unit,
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
                Text(if (live) "MIXING NOW" else "NEXT MIX", color = Glass.EmberText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f))
                if (where != null) Text(where, color = if (live) Glass.Muted else Color(0xFFF2A33A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(state.recipe ?: "Planned after this song's tricks", color = Glass.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            // How the next song comes in: its tempo against the set, and where in it the mix starts.
            val how = listOfNotNull(state.tempoChange, state.entrySec?.let { "B from ${formatTime(it)}" }).joinToString(" · ")
            if (how.isNotEmpty()) Text(how, color = Glass.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
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
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { EffectsPanel(state.fx, onOpenEffects) }
        Controls(state, onToggle, onStop, onSkip)
    }
}

/**
 * One panel of a fixed size for the mix's effects: a single effect gets its setting in large
 * type with beats left and a progress bar; two get a line and a bar each; three or four one
 * compact line each (a fourth line counts any more). Like the visualizer's labels, it shows only
 * what is playing right now. A tap opens the full effects view.
 */
@Composable
private fun EffectsPanel(rack: List<FxStatus>, onOpen: () -> Unit) {
    val active = rack.filter { it.active }
    Column(
        Modifier.fillMaxWidth().height(EFFECTS_HEIGHT).clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "Show all effects", onClick = onOpen).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, DeckA.copy(alpha = if (active.isEmpty()) 0.2f else 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row {
            Text("EFFECTS", color = Glass.EmberText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            if (active.isNotEmpty()) Text("${active.size} playing", color = Glass.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("  ›", color = Glass.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        when {
            active.size == 1 -> OneEffect(active[0])
            active.size == 2 -> active.forEach { TwoLineEffect(it) }
            active.size >= 3 -> {
                val shown = if (active.size > 4) active.take(3) else active
                shown.forEach { EffectLine(it) }
                if (active.size > 4) Text("+${active.size - 3} more · " + active.drop(3).joinToString { it.name }, color = Glass.EmberText, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> Text("Effects light up here as they play", color = Glass.Muted, fontSize = 11.sp)
        }
    }
}

private val EFFECTS_HEIGHT = 100.dp

private fun FxStatus.tint() = if (deck == "A") DeckA else DeckB
private fun FxStatus.tintText() = if (deck == "A") Glass.EmberText else DeckBText

/** The setting without the deck it's on ("A high-pass 900 Hz" → "High-pass 900 Hz"). */
private fun FxStatus.setting(): String {
    if (name == "Bass EQ") return "A → B swapping"
    val d = deck ?: return detail
    return detail.removePrefix("$d ").replaceFirstChar { it.uppercase() }
}

private fun FxStatus.heading() = when {
    name == "Bass EQ" -> "BASS · A → B"
    deck != null -> "${name.uppercase()} · DECK $deck"
    else -> name.uppercase()
}

@Composable
private fun OneEffect(fx: FxStatus) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(fx.tint(), CircleShape))
        Text(fx.heading(), color = fx.tintText(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1,
            modifier = Modifier.padding(start = 7.dp).weight(1f))
        val beats = ceil(fx.beatsLeft).toInt()
        if (fx.deck != null && beats > 0) Text("$beats beat${if (beats == 1) "" else "s"} left", color = if (fx.deck == "A") AText else BText, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
    }
    Text(fx.setting(), color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Bar(fx.progress, fx.tint(), Modifier, 6.dp)
}

@Composable
private fun TwoLineEffect(fx: FxStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(fx.tint(), CircleShape))
            Text(fx.heading().replace(" · DECK ", " · "), color = fx.tintText(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            Text(fx.setting().removePrefix("A → B "), color = Glass.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
        Bar(fx.progress, fx.tint(), Modifier, 4.dp)
    }
}

/** One line: a light, the name, what it's doing, and a small progress bar. */
@Composable
private fun EffectLine(fx: FxStatus) {
    val tint = fx.tint()
    Row(Modifier.height(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(tint, CircleShape))
        Text(fx.name.removeSuffix(" EQ"), color = Glass.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        Text(if (fx.name == "Bass EQ") "A → B swapping" else fx.detail, color = Glass.Muted, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
        Bar(fx.progress, tint, Modifier.width(30.dp), 4.dp)
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
        Text("A", color = Glass.EmberText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        // Between A and B: whether the beats are locked (a crossfader needs no label).
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (locked) Color(0xFF5BD69A) else Glass.FillSelected, CircleShape))
            Text(lock, color = Glass.Muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
        }
        Text("B", color = DeckBText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    Box(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp).semantics { contentDescription = "Crossfader ${(pos * 100).roundToInt()}% towards deck B" }) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Glass.FillSelected))
        Canvas(Modifier.fillMaxSize()) {
            val x = pos * (size.width - 14.dp.toPx())
            drawRoundRect(Color.White, topLeft = Offset(x, 0f), size = Size(14.dp.toPx(), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}

/** Stop, play/pause and skip, with "AI in control" (or the skip in progress) under play. */
@Composable
private fun Controls(state: EngineState, onToggle: () -> Unit, onStop: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(44.dp, null, "Stop the set", onStop) { StopIcon(Glass.Text, Modifier.size(22.dp)) }
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
            ) { SkipIcon(if (state.canSkip || skipping) Glass.Text else Glass.Muted, Modifier.size(24.dp)) }
        }
        val skipping = state.skipping > 0
        Text(
            if (skipping) "Skipping at the next ${if (state.skipping >= 2) "bar" else "phrase"}" else "AI in control",
            color = if (skipping) Glass.EmberText else Glass.Muted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun RoundButton(
    size: Dp,
    fill: Color?,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    ring: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (ring) Modifier.border(2.dp, Glass.EmberText, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}
