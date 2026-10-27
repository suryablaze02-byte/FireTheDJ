package com.autodj.ui

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    visual: () -> VisualFrame? = { null },
    onFullscreen: () -> Unit = {},
    onCast: () -> Unit = {},
) {
    val deck = state.current
    val track = state.set.getOrNull(state.index)
    if (deck == null || track == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Pick a crate on the Mix tab to start a set.",
                color = Glass.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
            // The set label is "Source · Genre" for a folder or playlist set, else just the genre.
            val label = state.crate.orEmpty()
            val source = label.substringBeforeLast(" · ", "")
            val genre = label.substringAfterLast(" · ")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.height(32.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).background(Glass.Ember, CircleShape))
                    Text(listOf(genre, state.mode.label).filter { it.isNotBlank() }.joinToString(" · "), color = Glass.Text, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton("Open the full-screen visualizer", onClick = onFullscreen) { ExpandGlyph(Glass.Text) }
                CastButton(rememberCasting(), diameter = 44.dp, onClick = onCast)
            }
            if (source.isNotBlank()) {
                Text("Playing from $source", color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp))
            }

            // The glass record with the live spectrum round it; it takes whatever height is left.
            DjVisualizer(visual, artwork[deck.uri], deck.uri, deck.title, Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), glass = true)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(deck.title, color = Glass.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (deck.artist.isNotBlank()) Text(deck.artist, color = Glass.Muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val key = if (deck.pitchSemis != 0) "${deck.key} (${if (deck.pitchSemis > 0) "+" else ""}${deck.pitchSemis})" else deck.key
                Text("${"%.0f".format(deck.bpm)} BPM · $key", color = Glass.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
            }

            // Where the set is in this track.
            val progress = if (deck.durationSec > 0) (deck.positionSec / deck.durationSec).toFloat().coerceIn(0f, 1f) else 0f
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Glass.FillSelected)) {
                Box(Modifier.fillMaxWidth(progress).height(6.dp).background(Color.White, CircleShape))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(formatTime(deck.positionSec), color = Glass.Muted, fontSize = 12.sp)
                val trick = when {
                    state.trickNow != null -> "Now: ${state.trickNow}"
                    state.trickNext != null -> "${state.trickNext} in ${formatTime(state.secondsToTrick ?: 0.0)}"
                    else -> ""
                }
                Text(trick, color = if (state.trickNow != null) Glass.Ember else Glass.EmberText, fontSize = 12.sp,
                    fontWeight = if (state.trickNow != null) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                Text(formatTime(deck.durationSec), color = Glass.Muted, fontSize = 12.sp)
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onStop).semantics { contentDescription = "Stop the set" },
                    contentAlignment = Alignment.Center,
                ) { StopIcon(Glass.Text, Modifier.size(26.dp)) }
                Box(
                    Modifier.size(76.dp).background(Color.White, CircleShape).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onToggle)
                        .semantics { contentDescription = if (state.paused) "Resume the set" else "Pause the set" },
                    contentAlignment = Alignment.Center,
                ) { if (state.paused) PlayIcon(Color(0xFF120A10), Modifier.size(32.dp)) else PauseIcon(Color(0xFF120A10), Modifier.size(32.dp)) }
                Text("AI in control", color = Glass.Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
            }

            UpNext(state, artwork, onOpenMix, Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * The playing track's cover, blown up and blurred, lighting the glass behind the whole screen,
 * tab bar included (Android 12+; older phones keep the ambient light).
 */
@Composable
fun NowPlayingBackdrop(state: EngineState, artwork: Map<String, String>) {
    val deck = state.current ?: return
    if (Build.VERSION.SDK_INT < 31) return
    Artwork(artwork[deck.uri], deck.uri, deck.title, Modifier.fillMaxSize().blur(80.dp).alpha(0.55f), RectangleShape)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.45f)))))
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
            Text(line, color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(next.title, next.artist).filter { it.isNotBlank() }.joinToString(" · "),
                color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.Muted)
    }
}
