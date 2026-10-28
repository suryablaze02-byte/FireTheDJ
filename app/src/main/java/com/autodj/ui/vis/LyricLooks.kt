package com.autodj.ui.vis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.lyrics.LyricWord
import com.autodj.lyrics.LyricsStore
import com.autodj.lyrics.TimedLyrics
import com.autodj.lyrics.WordKey
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The flame lettering for the song's key word and the word being sung. */
private val FIRE = Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFFB020), Color(0xFFFF5A1F), Color(0xFFD7261E)))
private val TEXT = Color(0xFFF2F2F5)
private val MUTED = Color(0xFF8E8A99)
private val UNSUNG = Color(0xFF5A5664)

/** One sung line: its words, in order. */
private class SungLine(val words: List<LyricWord>) {
    val start = words.first().startSec
    val end = words.last().endSec
    val text = words.joinToString(" ") { it.text }
}

private fun linesOf(l: TimedLyrics): List<SungLine> =
    l.words.groupBy { it.line }.values.filter { it.isNotEmpty() }.map { SungLine(it) }.sortedBy { it.start }

/** The song's hook word: the longest word it sings at least three times. */
private fun hookOf(l: TimedLyrics): String? =
    l.counts.filter { (k, n) -> n >= 3 && WordKey.linkable(k) }.maxByOrNull { (k, n) -> n * k.length }?.key

/**
 * The words over a lyric look, read from the song the room hears: Beat Pop builds the sung
 * line up a word per beat, Karaoke lights each word as it's sung. Every word is shown in
 * English letters (lyrics in Telugu and other scripts were converted when they were fetched).
 */
@Composable
internal fun LyricOverlay(style: VisualizerStyle, s: VisualState, tick: Long, modifier: Modifier = Modifier, clearBottom: Dp = 0.dp) {
    tick // redraw every frame
    // Lyrics can arrive while the song plays: look again every few seconds until they do.
    val lyrics = remember(s.songUri, (s.time / 4f).toInt()) { if (s.songUri.isEmpty()) null else LyricsStore.get(s.songUri) }
    val lines = remember(lyrics) { lyrics?.let(::linesOf).orEmpty() }
    val hook = remember(lyrics) { lyrics?.let(::hookOf) }
    // Clear of whatever panel is open at the bottom (the controls), so the words never sit behind it.
    BoxWithConstraints(modifier.padding(bottom = clearBottom)) {
        val w = maxWidth.value
        val h = (maxHeight + clearBottom).value
        // Sized to the screen: a phone held upright is the 1.0 reference; landscape and TV scale up, gently.
        val k = if (w > h) min(w / 900f, h / 560f) else min(w / 390f, h / 700f)
        val pos = s.songPos
        val idx = lines.indexOfLast { it.start <= pos + 0.15 }
        val line = lines.getOrNull(idx)?.takeIf { pos <= it.end + 3.0 }
        Column(
            Modifier.fillMaxSize().padding(horizontal = (24 * k).dp, vertical = (28 * k).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height((48 * k).dp), contentAlignment = Alignment.Center) {
                s.cue?.let { CueChip(it, s.kick, k) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    lyrics == null -> Note("No timed lyrics for this song", k)
                    line == null -> BeatDots(s, k)
                    style == VisualizerStyle.KARAOKE -> Karaoke(lines, idx, pos, s, k)
                    else -> BeatPop(lines, idx, line, pos, s, hook, k)
                }
            }
        }
    }
}

/** The line building up a word per beat, each word punching in as it lands; the hook word in flames. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeatPop(lines: List<SungLine>, idx: Int, line: SungLine, pos: Double, s: VisualState, hook: String?, k: Float) {
    val base = when {
        line.words.size <= 4 -> 58f
        line.words.size <= 7 -> 44f
        else -> 34f
    } * k
    val half = s.songBeatSec / 2
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((26 * k).dp)) {
        lines.getOrNull(idx - 1)?.let {
            Text(it.text, color = MUTED, fontSize = (16 * k).sp, textAlign = TextAlign.Center, lineHeight = (22 * k).sp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy((12 * k).dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy((4 * k).dp),
        ) {
            for (word in line.words) {
                // Each word lands on the nearest half beat of the song's own grid.
                val landsAt = s.songDownbeat + ((word.startSec - s.songDownbeat) / half).roundToInt() * half
                val age = (pos - landsAt).toFloat()
                val shown = age >= 0f
                val key = hook != null && word.key == hook
                val pop = if (shown) exp(-age * 5f) else 0f
                val size = if (key) base * 1.4f else base
                Text(
                    word.text.uppercase(),
                    style = if (key) TextStyle(brush = FIRE) else TextStyle(color = TEXT),
                    fontSize = size.sp, fontWeight = FontWeight.Black, lineHeight = (size * 1.08f).sp,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (shown) 1f else 0f
                        val sc = if (shown) 1f + (if (key) 0.45f else 0.25f) * pop else 0.8f
                        scaleX = sc; scaleY = sc
                        translationY = if (shown) -10f * k * pop else 24f * k
                    },
                )
            }
        }
    }
}

/** The previous, current and next lines; the current one lit word by word, with a progress bar and the bar's beats. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Karaoke(lines: List<SungLine>, idx: Int, pos: Double, s: VisualState, k: Float) {
    val line = lines[idx]
    // Long lines get smaller type, so a line stays a line or two.
    val size = when {
        line.words.size <= 7 -> 34f
        line.words.size <= 11 -> 28f
        else -> 24f
    } * k
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((24 * k).dp)) {
        Text(lines.getOrNull(idx - 1)?.text ?: "", color = UNSUNG, fontSize = (18 * k).sp, lineHeight = (24 * k).sp)
        Column(verticalArrangement = Arrangement.spacedBy((12 * k).dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy((10 * k).dp), verticalArrangement = Arrangement.spacedBy((4 * k).dp)) {
                for (word in line.words) {
                    val now = pos >= word.startSec && pos < word.endSec
                    val sung = pos >= word.endSec
                    val pulse = if (now) 1f + 0.12f * exp(-(pos - word.startSec).toFloat() * 6f) else 1f
                    Text(
                        word.text,
                        style = when {
                            now -> TextStyle(brush = FIRE)
                            sung -> TextStyle(color = TEXT)
                            else -> TextStyle(color = UNSUNG)
                        },
                        fontSize = size.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (size * 1.18f).sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pulse; scaleY = pulse
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                        },
                    )
                }
            }
            val progress = ((pos - line.start) / max(0.5, line.end - line.start)).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height((4 * k).dp).background(Color(0xFF2A2633), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(progress).height((4 * k).dp).background(Brush.horizontalGradient(listOf(Color(0xFFFFB020), Color(0xFFFF5A1F))), RoundedCornerShape(50)))
            }
        }
        Text(lines.getOrNull(idx + 1)?.text ?: "", color = UNSUNG, fontSize = (18 * k).sp, lineHeight = (24 * k).sp)
        BeatDots(s, k)
    }
}

/** The four beats of the bar, the current one lit: shown between sung lines too. */
@Composable
private fun BeatDots(s: VisualState, k: Float) {
    val beatNo = floor((s.songPos - s.songDownbeat) / s.songBeatSec).toInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * k).dp, Alignment.CenterHorizontally)) {
        for (i in 0 until 4) {
            Box(
                Modifier.width((44 * k).dp).height((6 * k).dp)
                    .background(if (Math.floorMod(beatNo, 4) == i) Color(0xFFFF7A1A) else Color(0xFF2A2633), RoundedCornerShape(50)),
            )
        }
    }
}

/** "Word play “party”" pulsing on the kick before a word-play mix. */
@Composable
private fun CueChip(text: String, kick: Float, k: Float) {
    Text(
        text,
        color = Color(0xFFFFD9B8), fontSize = (15 * k).sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .graphicsLayer { val sc = 1f + 0.06f * kick; scaleX = sc; scaleY = sc }
            .background(Color(0xFF2A1810), RoundedCornerShape(50))
            .padding(horizontal = (16 * k).dp, vertical = (8 * k).dp),
    )
}

@Composable
private fun Note(text: String, k: Float) {
    Text(text, color = MUTED, fontSize = (15 * k).sp, textAlign = TextAlign.Center)
}

/**
 * The quiet room behind the lyric looks: near-black with one glow that breathes with the kick,
 * low and violet for Karaoke, central and ember for Beat Pop, so the words stay readable.
 */
class GlowScene(private val low: Boolean) : Scene {
    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF0B0810))
        val (c1, c2) = s.palette
        val centre = if (low) Offset(w / 2, h * 1.05f) else Offset(w / 2, h * 0.5f)
        val r = max(w, h) * (if (low) 0.6f else 0.55f)
        val a = (0.18f + 0.45f * s.kick).coerceAtMost(0.75f)
        val inner = if (low) Color(0xFF7C5CFF) else Color(0xFFFF6A1A)
        drawCircle(Brush.radialGradient(listOf(inner.copy(alpha = a), c2.copy(alpha = a * 0.4f), Color.Transparent), centre, r), r, centre)
        drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.06f + 0.1f * s.energy), Color.Transparent), Offset(w * 0.2f, h * 0.2f), r), r, Offset(w * 0.2f, h * 0.2f))
    }
}
