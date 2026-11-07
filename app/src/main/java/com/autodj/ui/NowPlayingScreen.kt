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
import com.autodj.dj.EngineState
import com.autodj.dj.VisualFrame

@Composable
fun NowPlayingScreen(
    state: EngineState,
    artwork: Map<String, String>,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onOpenMix: () -> Unit,
    onSkip: () -> Unit = {},
    visual: () -> VisualFrame? = { null },
    onFullscreen: () -> Unit = {},
    onCast: () -> Unit = {},
    /** The language the set is sung in, shown with its genre and mode; null for a mixed set. */
    setLanguage: String? = null,
    /** A song's language by its uri. */
    languageOf: (String) -> String? = { null },
    /** With nothing playing: over to the Mix tab to pick a crate. */
    onGoToMix: () -> Unit = {},
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
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.height(if (tablet) 40.dp else 32.dp).glass(RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(Glass.Ember, CircleShape))
                Text(listOfNotNull(genre, state.mode.label, setLanguage).filter { it.isNotBlank() }.joinToString(" · "), color = Glass.Text,
                    fontSize = if (tablet) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            GlassIconButton("Open the full-screen visualizer", size = if (tablet) 48.dp else 44.dp, onClick = onFullscreen) { ExpandGlyph(Glass.Text) }
            CastButton(rememberCasting(), diameter = if (tablet) 48.dp else 44.dp, onClick = onCast)
        }
        if (source.isNotBlank()) {
            Text("Playing from $source", color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
    // The title (up to two lines), the artist and the song's details as chips.
    val info: @Composable () -> Unit = {
        Text(displayTitle(deck.title), color = Glass.Text, fontSize = if (tablet) 38.sp else 24.sp, lineHeight = if (tablet) 43.sp else 29.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (deck.artist.isNotBlank()) Text(deck.artist, color = Glass.Text.copy(alpha = 0.9f), fontSize = if (tablet) 20.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val key = if (deck.pitchSemis != 0) "${deck.key} (${if (deck.pitchSemis > 0) "+" else ""}${deck.pitchSemis})" else deck.key
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull("${"%.0f".format(deck.bpm)} BPM", "Key $key", languageOf(deck.uri)).forEach {
                Text(it, color = Glass.Text, fontSize = if (tablet) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(Glass.FillStrong).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    // Where the set is in this song, with the point it mixes out marked in orange.
    val progress: @Composable () -> Unit = {
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
        Text("AI in control", color = Glass.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }

    if (tablet) {
        Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 16.dp, bottom = 12.dp + LocalTabBarInset.current)) {
            header()
            // The record big across the top; the song and its controls beside what's next underneath.
            DjVisualizer(visual, artwork[deck.uri], deck.uri, deck.title, Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), glass = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    info()
                    progress()
                    controls()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpNext(state, artwork, onOpenMix, Modifier)
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
            DjVisualizer(visual, artwork[deck.uri], deck.uri, deck.title, Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), glass = true)
            info()
            progress()
            controls()
            UpNext(state, artwork, onOpenMix, Modifier.padding(top = 10.dp))
        }
    }
}

/** The next track and the transition FireTheDJ has planned; opens the mixing view. */
@Composable
private fun UpNext(state: EngineState, artwork: Map<String, String>, onOpenMix: () -> Unit, modifier: Modifier) {
    val next = state.next
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp))
            .clickable(enabled = next != null, onClickLabel = "Show the planned mix", onClick = onOpenMix)
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
                        if (state.mixing) "mixing now" else "in ${formatTime(state.secondsToMix ?: 0.0)}" + state.tempoChange?.let { " · $it" }.orEmpty(),
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
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.Muted)
    }
}
