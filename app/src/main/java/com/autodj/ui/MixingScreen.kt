package com.autodj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.analysis.Camelot
import com.autodj.dj.EngineState
import com.autodj.dj.VisualFrame
import com.autodj.dj.FxStatus
import kotlin.math.roundToInt

/** The live transition: both decks, the crossfade, sync readouts, the effects rack and the timeline. */
@Composable
fun MixingScreen(state: EngineState, artwork: Map<String, String>, onBack: () -> Unit, visual: () -> VisualFrame? = { null }) {
    val a = state.current ?: return
    val next = state.next
    val b = state.incoming
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mixing", color = Dj.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "AI recipe: ${state.recipe ?: "–"} · ${state.mixBars} bar${if (state.mixBars == 1) "" else "s"} · ${state.mode.label}",
                    color = Dj.BlueText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (state.mixing) "bar ${state.mixBar}/${state.mixBars}" else "in ${formatTime(state.secondsToMix ?: 0.0)}",
                color = Dj.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.background(Dj.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            TextButton(onClick = onBack) { Text("Back", color = Dj.Muted) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeckCard(
                Modifier.weight(1f), "OUT · deck A", a.uri, a.title, artwork[a.uri],
                bpmText = "%.1f BPM".format(a.bpm), gain = a.gain, accent = true, visual = visual,
            )
            DeckCard(
                Modifier.weight(1f), "IN · deck B", next?.uri ?: "", next?.title ?: "–", next?.let { artwork[it.uri] },
                bpmText = next?.let { "%.1f → %.1f".format(it.bpm, it.bpm * state.rate) } ?: "",
                gain = b?.gain ?: 0f, accent = false, visual = visual,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val gA = a.gain
            val gB = b?.gain ?: 0f
            Row(Modifier.fillMaxWidth()) {
                Text("A ${(gA * 100).roundToInt()}%", color = Dj.Muted, fontSize = 12.sp)
                Text("Crossfade", color = Dj.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("B ${(gB * 100).roundToInt()}%", color = Dj.Muted, fontSize = 12.sp)
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(28.dp).background(Dj.Surface, RoundedCornerShape(14.dp)).border(1.dp, Dj.Border, RoundedCornerShape(14.dp)),
            ) {
                val pos = if (gA + gB > 0.001f) gB / (gA + gB) else 0f
                Box(
                    Modifier.offset(x = 3.dp + (maxWidth - 46.dp) * pos, y = 3.dp).size(40.dp, 20.dp)
                        .background(Dj.Text, RoundedCornerShape(10.dp)),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Readout(Modifier.weight(1f), "Rate r", "%.3f".format(state.rate))
            Readout(Modifier.weight(1f), "12·log₂ r (key-locked)", "%+.2f st".format(state.pitchSemitones))
            Readout(Modifier.weight(1f), "Phase err (mod T_b)", "${state.phaseErrMs.roundToInt()} ms")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shift = state.nextPitchSemis
            val nextKey = next?.let { Camelot.code(it.keyPc + shift, it.keyMinor) } ?: "–"
            Readout(
                Modifier.weight(2f), "Key (Camelot)",
                "${a.key} → $nextKey" + if (shift != 0) "  (${if (shift > 0) "+" else ""}$shift st)" else "",
            )
            Readout(Modifier.weight(1f), "Set tempo", "%.1f".format(state.masterBpm))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.fx.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { FxCard(it, Modifier.weight(1f)) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.phases.forEach { ph ->
                        val active = state.mixing && state.mixProgress >= ph.start && state.mixProgress < ph.end
                        Box(
                            Modifier.weight((ph.end - ph.start).coerceAtLeast(0.01f)).fillMaxHeight()
                                .background(if (active) Dj.Accent else Dj.PhaseIdle, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ph.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (active) Dj.Bg else Dj.BlueText,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Box(Modifier.offset(x = maxWidth * state.mixProgress).width(2.dp).fillMaxHeight().background(Dj.Text))
            }
            Text("FireTheDJ picks the effects, key and tempo for each mix. There's no manual Mix now.", color = Dj.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DeckCard(
    modifier: Modifier,
    label: String,
    seed: String,
    title: String,
    art: String?,
    bpmText: String,
    gain: Float,
    accent: Boolean,
    visual: () -> VisualFrame?,
) {
    Column(
        modifier.background(Dj.Surface, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Each deck's own visualizer: deck A is the set's deck, deck B the one coming in.
        DjVisualizer(visual, art, seed, title, Modifier.size(140.dp), only = !accent, showLabels = false)
        Text(label, color = Dj.Muted, fontSize = 11.sp)
        Text(title, color = Dj.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(bpmText, color = Dj.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(6.dp).background(Dj.Border, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(gain.coerceIn(0f, 1f)).fillMaxHeight().background(if (accent) Dj.Accent else Dj.Blue, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Readout(modifier: Modifier, label: String, value: String) {
    Column(modifier.background(Dj.Surface, RoundedCornerShape(10.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Dj.Muted, fontSize = 11.sp, maxLines = 2)
        Text(value, color = Dj.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FxCard(fx: FxStatus, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.background(if (fx.active) Dj.AccentBg else Dj.Surface, shape)
            .border(1.dp, if (fx.active) Dj.Accent else Dj.Border, shape)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(9.dp).background(if (fx.active) Dj.Accent else Dj.LightIdle, CircleShape))
        Column {
            Text(fx.name, color = Dj.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(fx.detail, color = Dj.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The same mix view for a phone held sideways, opened from the effects panel: the decks are
 * already on screen, so it gives the room to the mix itself. The crossfade, sync readouts and
 * timeline sit in a column on the left; every effect fills a four-column rack on the right.
 */
@Composable
fun MixingScreenLandscape(state: EngineState, onBack: () -> Unit) {
    val a = state.current ?: return
    val next = state.next
    val b = state.incoming
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Effects · ${if (state.mixing) "mixing" else "next mix"}", color = Dj.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "AI recipe: ${state.recipe ?: "–"} · ${state.mixBars} bar${if (state.mixBars == 1) "" else "s"} · ${state.mode.label}",
                    color = Dj.BlueText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (state.mixing) "bar ${state.mixBar}/${state.mixBars}" else "in ${formatTime(state.secondsToMix ?: 0.0)}",
                color = Dj.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.background(Dj.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            TextButton(onClick = onBack) { Text("Back", color = Dj.Muted) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val gA = a.gain
                val gB = b?.gain ?: 0f
                Row(Modifier.fillMaxWidth()) {
                    Text("A ${(gA * 100).roundToInt()}%", color = Dj.Muted, fontSize = 12.sp)
                    Text("Crossfade", color = Dj.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("B ${(gB * 100).roundToInt()}%", color = Dj.Muted, fontSize = 12.sp)
                }
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(24.dp).background(Dj.Surface, RoundedCornerShape(12.dp)).border(1.dp, Dj.Border, RoundedCornerShape(12.dp)),
                ) {
                    val pos = if (gA + gB > 0.001f) gB / (gA + gB) else 0f
                    Box(Modifier.offset(x = 3.dp + (maxWidth - 40.dp) * pos, y = 3.dp).size(34.dp, 18.dp).background(Dj.Text, RoundedCornerShape(9.dp)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Readout(Modifier.weight(1f), "Rate r", "%.3f".format(state.rate))
                    Readout(Modifier.weight(1f), "Phase err", "${state.phaseErrMs.roundToInt()} ms")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shift = state.nextPitchSemis
                    val nextKey = next?.let { Camelot.code(it.keyPc + shift, it.keyMinor) } ?: "–"
                    Readout(Modifier.weight(1f), "Key (Camelot)", "${a.key} → $nextKey" + if (shift != 0) " (${if (shift > 0) "+" else ""}$shift)" else "")
                    Readout(Modifier.weight(1f), "Set tempo", "%.1f".format(state.masterBpm))
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.phases.forEach { ph ->
                            val active = state.mixing && state.mixProgress >= ph.start && state.mixProgress < ph.end
                            Box(
                                Modifier.weight((ph.end - ph.start).coerceAtLeast(0.01f)).fillMaxHeight()
                                    .background(if (active) Dj.Accent else Dj.PhaseIdle, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(ph.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Dj.Bg else Dj.BlueText,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Box(Modifier.offset(x = maxWidth * state.mixProgress).width(2.dp).fillMaxHeight().background(Dj.Text))
                }
            }
            Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fx.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { FxCard(it, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
