package com.autodj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.dj.EngineState

/** Room the mini player takes above the tab bar: its 64dp and an 8dp gap. */
val MINI_PLAYER_SPACE = 72.dp

/**
 * The playing set on Mix and Music: the song, what the AI does next, and pause, in a glass pill
 * above the tab bar. Tapping it opens Now Playing.
 */
@Composable
fun MiniPlayer(state: EngineState, artwork: Map<String, String>, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val deck = state.current ?: return
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.fillMaxWidth().height(64.dp).floatingGlass(shape, GlassOverContent)
            .clickable(role = Role.Button, onClickLabel = "Open Now Playing", onClick = onOpen),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(artwork[deck.uri], deck.uri, deck.title, Modifier.size(48.dp), RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(displayTitle(deck.title), deck.artist).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val next = state.next
                val line = when {
                    state.paused -> "Paused"
                    state.mixing -> "Mixing into ${next?.title?.let(::displayTitle) ?: "the next song"}"
                    state.trickNow != null -> "Now: ${state.trickNow}"
                    state.recipe != null && state.secondsToMix != null -> "Next: ${state.recipe} in ${formatTime(state.secondsToMix)}"
                    else -> "AI in control"
                }
                Text(line, color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Glass.Text)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = if (state.paused) "Resume the set" else "Pause the set" },
                contentAlignment = Alignment.Center,
            ) { if (state.paused) PlayIcon(Glass.Ground, Modifier.size(18.dp)) else PauseIcon(Glass.Ground, Modifier.size(18.dp)) }
        }
        // Where the set is in this song, along the bottom edge.
        val progress = if (deck.durationSec > 0) (deck.positionSec / deck.durationSec).toFloat().coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp).fillMaxWidth(progress).height(3.dp).background(Glass.Ember, CircleShape))
    }
}
