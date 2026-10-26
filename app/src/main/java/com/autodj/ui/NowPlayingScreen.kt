package com.autodj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.dj.EngineState
import com.autodj.dj.VisualFrame
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    state: EngineState,
    artwork: Map<String, String>,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onOpenMix: () -> Unit,
    visual: () -> VisualFrame? = { null },
    onFullscreen: () -> Unit = {},
    onCast: () -> Unit = {},
) {
    val deck = state.current
    val track = state.set.getOrNull(state.index)
    if (deck == null || track == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Pick a genre crate in the Library to start a mix.",
                color = Dj.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The set label is "Source · Genre" for a folder or playlist set, else just the genre.
        val label = state.crate.orEmpty()
        val source = label.substringBeforeLast(" · ", "")
        val genre = label.substringAfterLast(" · ")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FireTheDjWordmark(24.sp, Modifier.weight(1f))
                if (genre.isNotBlank()) HeaderChip(genre, dot = false)
                HeaderChip(state.mode.label, dot = true)
                CastButton(rememberCasting(), onClick = onCast)
            }
            if (source.isNotBlank()) {
                Text("Playing from $source", color = Dj.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // The visualizer takes whatever height is left, so the controls below always fit on screen.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            DjVisualizer(visual, artwork[deck.uri], deck.uri, deck.title, Modifier.fillMaxSize())
            // Top-right of the visualizer: the control sits on the thing it enlarges.
            Row(
                Modifier.align(Alignment.TopEnd).background(Dj.Surface.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                    .border(1.dp, Dj.Border, RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button, onClickLabel = "Open the full-screen visualizer", onClick = onFullscreen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EqualizerGlyph(Dj.Accent)
                Text("Visualizer", color = Dj.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(deck.title, color = Dj.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (deck.artist.isNotBlank()) Text(deck.artist, color = Dj.Muted, fontSize = 15.sp, maxLines = 1)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Chip("%.1f BPM".format(deck.bpm), mono = true)
            Chip(
                if (deck.pitchSemis != 0) "${deck.key} (${if (deck.pitchSemis > 0) "+" else ""}${deck.pitchSemis})" else deck.key,
                mono = true,
            )
            Chip("${track.genre} · ${(track.genreConfidence * 100).roundToInt()}%")
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EnergyMap(track, deck.positionSec, state.exitSec)
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${formatTime(deck.positionSec)} / ${formatTime(deck.durationSec)} · set ${"%.1f".format(state.masterBpm)} BPM",
                    color = Dj.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                state.exitSec?.let { exit ->
                    Text("AI mix-out · bar ${track.barAt(exit) + 1}", color = Dj.BlueText, fontSize = 12.sp)
                }
            }
            val trick = when {
                state.trickNow != null -> "Now: ${state.trickNow}"
                state.trickNext != null -> "Next trick: ${state.trickNext} in ${formatTime(state.secondsToTrick ?: 0.0)}"
                else -> null
            }
            if (trick != null) {
                Text(
                    trick,
                    color = if (state.trickNow != null) Dj.Accent else Dj.BlueText,
                    fontSize = 13.sp,
                    fontWeight = if (state.trickNow != null) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        UpNext(state, artwork, onOpenMix)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(Dj.Surface, CircleShape).border(1.dp, Dj.Border, CircleShape)
                    .clickable(role = Role.Button, onClick = onStop).semantics { contentDescription = "Stop the set" },
                contentAlignment = Alignment.Center,
            ) { StopIcon(Dj.Text) }
            Box(
                Modifier.size(72.dp).background(Dj.Accent, CircleShape)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = if (state.paused) "Resume the set" else "Pause the set" },
                contentAlignment = Alignment.Center,
            ) { if (state.paused) PlayIcon(Dj.Bg) else PauseIcon(Dj.Bg) }
            Text("AI in control", color = Dj.Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
        }
    }
}

@Composable
private fun UpNext(state: EngineState, artwork: Map<String, String>, onOpenMix: () -> Unit) {
    val next = state.next
    Row(
        Modifier.fillMaxWidth().background(Dj.Surface, RoundedCornerShape(14.dp)).border(1.dp, Dj.Border, RoundedCornerShape(14.dp))
            .clickable(enabled = next != null, onClickLabel = "Show the planned mix", onClick = onOpenMix)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (next == null) {
            // The next track is chosen once this track's own tricks (a hook replay) are done.
            val more = state.set.size > state.index + 1
            Text(
                if (more) "FireTheDJ picks the next track after the ${(state.trickNow ?: state.trickNext)?.lowercase() ?: "current trick"}" else "Last track of the set",
                color = Dj.Muted,
                fontSize = 14.sp,
            )
            return@Row
        }
        Vinyl(artwork[next.uri], next.uri, next.title, 52.dp, spinning = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Up next · chosen by FireTheDJ", color = Dj.Muted, fontSize = 12.sp)
            Text(
                listOf(next.title, next.artist).filter { it.isNotBlank() }.joinToString(" — "),
                color = Dj.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val tempo = state.tempoChange ?: ""
            val line = when {
                state.secondsToMix != null -> "AI mixes in ${formatTime(state.secondsToMix)} · ${state.recipe} · $tempo"
                state.mixing || state.recipe != null -> "Mixing now · ${state.recipe} · $tempo"
                // Chosen, but the mix itself is planned once this track's hook replay is done.
                else -> "Mix planned after the hook replay"
            }
            Text(line, color = Dj.BlueText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Chip(text: String, mono: Boolean = false) {
    Text(
        text,
        color = Dj.Text,
        fontSize = 13.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        maxLines = 1,
        modifier = Modifier.background(Dj.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A short status chip in the header: the set's genre crate, or its mode (with a live dot). */
@Composable
private fun HeaderChip(label: String, dot: Boolean) {
    Row(
        Modifier.background(Dj.Surface, RoundedCornerShape(999.dp)).border(1.dp, Dj.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(8.dp).background(Dj.Accent, CircleShape))
        Text(label, color = if (dot) Dj.Text else Dj.Muted, fontSize = 13.sp, maxLines = 1)
    }
}
