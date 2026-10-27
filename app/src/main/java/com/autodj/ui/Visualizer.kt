package com.autodj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.dj.DeckVisual
import com.autodj.dj.SpectrumTap
import com.autodj.dj.VisualFrame
import com.autodj.dj.fx.FxPlan
import com.autodj.dj.fx.FxType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The live deck visualizer: a radial spectrum around the cover art, with the set's beat,
 * phrase and every effect and trick drawn into it.
 *
 * - **Spectrum:** lows at the bottom, highs at the top, mirrored left and right. The deck
 *   carrying the set is orange; a deck being mixed in is blue and interleaved with it, so a
 *   crossfade is visible as one colour giving way to the other.
 * - **Beat:** the cover pulses with the kick; the outer ring marks the four beats of the bar
 *   and fills through the 8-bar phrase.
 * - **Effects:** filters dim the bands they cut, gates and loop rolls strobe the ring, echoes
 *   leave expanding ghosts, reverb hazes, flanger and phaser sweep the colours, lo-fi turns
 *   the bars blocky, vocal cuts dim the vocal band, scratches and spinbacks move the cover.
 * - **Sound effects:** sirens rotate red and blue beacons, risers and snare rolls build a
 *   glow, crashes, horns, impacts, sub drops and gunshots send shockwaves, lasers fire beams,
 *   whooshes sweep round the ring.
 *
 * [only] limits it to one deck (false = the set's deck, true = the incoming one).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DjVisualizer(
    frame: () -> VisualFrame?,
    artworkPath: String?,
    seed: String,
    title: String,
    modifier: Modifier = Modifier,
    only: Boolean? = null,
    showLabels: Boolean = true,
    glass: Boolean = false,
) {
    val scene = remember { Scene() }
    var tick by remember { mutableLongStateOf(0L) }
    var labels by remember { mutableStateOf<List<Pair<String, Color>>>(emptyList()) }
    // The glass record's turn, in degrees: it turns while the set plays and stops when paused.
    val turn = remember { FloatArray(2) } // [angle, last frame time in s]
    LaunchedEffect(only) {
        while (true) {
            withFrameNanos { now ->
                val f = frame()
                scene.update(f?.let { v -> VisualFrame(v.decks.filter { only == null || it.incoming == only }, v.paused) }, now)
                if (scene.labels != labels) labels = scene.labels
                val t = now / 1e9f
                if (f != null && !f.paused && turn[1] > 0f) turn[0] = (turn[0] + (t - turn[1]) * RECORD_DEG_PER_S) % 360f
                turn[1] = t
                tick = now
            }
        }
    }
    BoxWithConstraints(modifier) {
        // The effect tags get their own row under the ring rather than covering it.
        val tagRow = if (showLabels) 28.dp else 0.dp
        val side = min(maxWidth.value, (maxHeight - tagRow).value).coerceAtLeast(0f).dp
        // Ring and tag row centred together, so a tall screen (full screen) keeps the ring in the middle.
        val top = ((maxHeight - side - tagRow) / 2).coerceAtLeast(0.dp)
        Box(Modifier.align(Alignment.TopCenter).padding(top = top).size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                tick // redraw every frame
                scene.draw(this)
            }
            Box(
                Modifier.size(side * Scene.ART_RATIO).graphicsLayer {
                    tick
                    scaleX = scene.pulse
                    scaleY = scene.pulse
                    rotationZ = scene.artRotationDeg + if (glass) turn[0] else 0f
                    alpha = scene.artAlpha
                },
            ) {
                if (glass) GlassRecord(artworkPath, seed, title) else Artwork(artworkPath, seed, title, Modifier.fillMaxSize(), CircleShape)
            }
        }
        if (showLabels && labels.isNotEmpty()) {
            FlowRow(
                Modifier.align(Alignment.TopCenter).padding(top = top + side + 2.dp),
                maxLines = 1,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                labels.forEach { (label, color) -> FxTag(label, color) }
            }
        }
    }
}

/** One turn every six seconds: slow enough to read the label, quick enough to feel alive. */
private const val RECORD_DEG_PER_S = 60f

/**
 * A record made of frosted glass: fine grooves, light catching its upper left, a sheen that
 * shows it turning, and the cover art as its centre label.
 */
@Composable
private fun GlassRecord(artworkPath: String?, seed: String, title: String) {
    GlassPlatter(Modifier.fillMaxSize()) {
        Artwork(artworkPath, seed, title, Modifier.align(Alignment.Center).fillMaxSize(0.56f).border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape), CircleShape)
        Box(Modifier.align(Alignment.Center).fillMaxSize(0.06f).background(Color(0xFF0F0A0E), CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
    }
}

@Composable
private fun FxTag(label: String, color: Color) {
    Row(
        Modifier.background(Dj.Bg.copy(alpha = 0.8f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, color = Dj.Text, fontSize = 11.sp, maxLines = 1)
    }
}

/** Everything the visualizer remembers between frames, and how it draws. */
private class Scene {
    private val n = SpectrumTap.BANDS

    /** Smoothed bar lengths and falling peak dots, per deck slot (0 = set deck, 1 = incoming). */
    private val bars = Array(2) { FloatArray(n) }
    private val peaks = Array(2) { FloatArray(n) }
    private val gains = FloatArray(2)
    private var present = BooleanArray(2)
    private var deckFx = Array(2) { emptyList<com.autodj.dj.ActiveFx>() }
    private var beat = 0f
    private var barInPhrase = 0
    private var lastNanos = 0L
    private var time = 0f

    var pulse = 1f
        private set
    var artRotationDeg = 0f
        private set
    var artAlpha = 1f
        private set
    var labels: List<Pair<String, Color>> = emptyList()
        private set

    private class Wave(val color: Color, val born: Float, val life: Float, val width: Float)
    private class Ghost(val color: Color, val born: Float, val radius: Float)

    private val waves = ArrayList<Wave>()
    private val ghosts = ArrayList<Ghost>()
    private val seen = HashSet<String>()
    private var lastEchoSlot = -1
    private var lastShotBeat = -1
    private var spin = 0f
    private var flash = 0f

    fun update(f: VisualFrame?, now: Long) {
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now
        time += dt
        val decks = arrayOfNulls<DeckVisual>(2)
        f?.decks?.forEach { decks[if (it.incoming) 1 else 0] = it }
        // With only the incoming deck shown, draw it in slot 0's place but keep its colour.
        val paused = f?.paused ?: true
        for (s in 0..1) {
            val d = decks[s]
            present[s] = d != null
            gains[s] += ((d?.gain ?: 0f) - gains[s]) * min(1f, dt * 12)
            deckFx[s] = d?.fx.orEmpty()
            for (b in 0 until n) {
                // Bars shrink with the crossfade as well as with level, so a fade reads clearly.
                val target = if (d == null || paused) 0f else d.bands[b] * (0.25f + 0.75f * d.gain)
                val cur = bars[s][b]
                bars[s][b] = if (target > cur) cur + (target - cur) * min(1f, dt * 30) else cur + (target - cur) * min(1f, dt * 7)
                peaks[s][b] = max(bars[s][b], peaks[s][b] - dt * 0.6f)
            }
        }
        val lead = decks[0] ?: decks[1]
        if (lead != null && !paused) {
            beat = lead.beat
            barInPhrase = lead.barInPhrase
        }
        // Kick: the lowest bands of whichever deck is loudest.
        val bass = (0..1).maxOf { s -> (0 until 5).sumOf { bars[s][it].toDouble() }.toFloat() / 5 * gains[s] }
        pulse = 1f + 0.09f * bass.pow(2f) + 0.05f * flash

        val all = (0..1).flatMap { s -> deckFx[s].map { it to s } }
        // Turntable moves: scratches rock the cover, spinbacks whip it backwards, brakes sag.
        var target = 0f
        artAlpha = 1f
        for ((fx, _) in all) {
            when (fx.type) {
                FxType.SCRATCH -> target += 18f * sin(2f * PI.toFloat() * beat)
                FxType.BACKSPIN -> target -= 540f * (1 - (1 - fx.progress).pow(2f))
                FxType.BRAKE -> {
                    target += 25f * fx.progress
                    artAlpha = 1f - 0.5f * fx.progress
                }
                else -> Unit
            }
        }
        spin += (target - spin) * min(1f, dt * 14)
        artRotationDeg = spin

        // One-shot effects: a shockwave when they start.
        val keys = HashSet<String>()
        for ((fx, s) in all) {
            val key = "$s:${fx.type}"
            keys += key
            val fresh = key !in seen
            val color = deckColor(s)
            when (fx.type) {
                FxType.IMPACT, FxType.SUB_DROP -> if (fresh) {
                    waves += Wave(color, time, 0.9f, 10f)
                    flash = 1f
                }
                FxType.CRASH -> if (fresh) {
                    waves += Wave(Color.White, time, 0.7f, 5f)
                    flash = max(flash, 0.7f)
                }
                FxType.HORN -> if (fresh) waves += Wave(Dj.Warn, time, 0.8f, 7f)
                FxType.GUNSHOT -> {
                    val shot = floor(beat).toInt()
                    if (shot != lastShotBeat) {
                        lastShotBeat = shot
                        waves += Wave(Color.White, time, 0.35f, 4f)
                        flash = max(flash, 0.6f)
                    }
                }
                FxType.ECHO -> {
                    // A ghost of the ring at each repeat.
                    val period = if (fx.p1 > 0) fx.p1 else 0.75f
                    val slot = floor(beat / period).toInt()
                    if (slot != lastEchoSlot) {
                        lastEchoSlot = slot
                        ghosts += Ghost(color, time, avgLength(s))
                    }
                }
                else -> Unit
            }
        }
        seen.clear()
        seen += keys
        flash = max(0f, flash - dt * 3f)
        waves.removeAll { time - it.born > it.life }
        ghosts.removeAll { time - it.born > GHOST_LIFE }

        labels = all.map { (fx, s) -> fx.type.label to deckColor(s) }.distinct().take(5)
    }

    private fun avgLength(s: Int) = (0 until n).sumOf { bars[s][it].toDouble() }.toFloat() / n

    private fun deckColor(slot: Int) = if (slot == 0) Dj.Accent else Dj.Blue

    fun draw(ds: DrawScope) = with(ds) {
        val c = center
        val outer = size.minDimension / 2f - 6.dp.toPx()
        val art = size.minDimension / 2f * ART_RATIO
        val inner = art + 8.dp.toPx()
        val reach = (outer - 10.dp.toPx() - inner).coerceAtLeast(1f)
        val all = (0..1).flatMap { s -> deckFx[s].map { it to s } }
        fun active(type: FxType) = all.firstOrNull { it.first.type == type }

        // Glow behind everything: the music's colour, louder music brighter.
        val energy = (0..1).maxOf { s -> avgLength(s) * gains[s] }
        val glowColor = if (present[1] && gains[1] > gains[0]) Dj.Blue else Dj.Accent
        drawCircle(
            Brush.radialGradient(
                listOf(glowColor.copy(alpha = 0.10f + 0.25f * energy + 0.3f * flash), Color.Transparent),
                center = c, radius = outer * 1.05f,
            ),
            radius = outer * 1.05f, center = c,
        )
        // Reverb: a soft haze ring.
        active(FxType.REVERB)?.let { (fx, s) ->
            drawCircle(deckColor(s).copy(alpha = 0.18f * fx.amount), radius = inner + reach * 0.6f, center = c, style = Stroke(reach * 0.9f))
        }
        // Riser, snare roll, reverse cymbal, downlifter: a glow that builds (or falls) with the effect.
        val build = all.filter { it.first.type in BUILDS }.maxOfOrNull { (fx, _) ->
            if (fx.type == FxType.DOWNLIFTER) (1 - fx.progress) * fx.amount else fx.progress * fx.amount
        } ?: 0f
        if (build > 0f) {
            val flicker = if (active(FxType.SNARE_ROLL) != null) 0.75f + 0.25f * sin(time * 40f) else 1f
            drawCircle(Dj.Warn.copy(alpha = 0.55f * build * flicker), radius = outer - 2.dp.toPx(), center = c, style = Stroke(2.dp.toPx() + 10.dp.toPx() * build))
        }

        // Echo ghosts: earlier copies of the ring drifting outwards.
        for (g in ghosts) {
            val age = (time - g.born) / GHOST_LIFE
            val r = inner + reach * g.radius + reach * 0.8f * age
            drawCircle(g.color.copy(alpha = 0.45f * (1 - age)), radius = r, center = c, style = Stroke(2.dp.toPx()))
        }

        // Rhythm: gate and tremolo chop the ring's brightness; loop rolls strobe on the loop.
        var chop = 1f
        active(FxType.GATE)?.let { (fx, _) -> if (frac(beat / max(fx.p1, 0.0625f)) >= 0.5f) chop *= 1f - 0.85f * fx.amount }
        active(FxType.TREMOLO)?.let { (fx, _) -> chop *= 1f - fx.amount * (0.5f - 0.5f * cos(2f * PI.toFloat() * beat / max(fx.p1, 0.0625f))) }
        val roll = active(FxType.LOOP_ROLL)?.first
        if (roll != null) chop *= 0.55f + 0.45f * (1 - frac(beat / max(roll.p1, 0.0625f)))

        // Which bands the filters and vocal tools let through.
        val hp = active(FxType.HIGH_PASS)?.first?.let { bandOf(FxPlan.highPassHz(it.amount)) } ?: -1f
        val lp = active(FxType.LOW_PASS)?.first?.let { bandOf(FxPlan.lowPassHz(it.amount)) } ?: n.toFloat()
        val vocalCut = active(FxType.VOCAL_CUT)?.first?.amount ?: 0f
        val vocalIso = active(FxType.VOCAL_ISOLATE)?.first?.amount ?: 0f
        val lofi = active(FxType.LOFI)?.first?.amount ?: 0f
        val sweep = (active(FxType.FLANGER) ?: active(FxType.PHASER))?.first
        val vocalLo = bandOf(250.0)
        val vocalHi = bandOf(4000.0)

        // Slow drift with the music: a full turn every 8 bars.
        val rotation = beat / 32f * 2f * PI.toFloat()
        val step = PI.toFloat() / n
        val barWidth = (inner * step * 0.55f).coerceIn(1.5f, 7.dp.toPx())
        for (s in 0..1) {
            if (!present[s] && bars[s].all { it < 0.01f }) continue
            val base = deckColor(s)
            val offset = if (s == 1) step / 2 else 0f
            for (b in 0 until n) {
                var alpha = chop
                if (b < hp || b > lp) alpha *= 0.18f
                if (b in vocalLo.toInt()..vocalHi.toInt()) alpha *= 1f - 0.7f * vocalCut
                else alpha *= 1f - 0.75f * vocalIso
                var len = bars[s][b]
                if (lofi > 0f) {
                    val steps = 8f - 5f * lofi
                    len = floor(len * steps) / steps
                }
                val color = if (sweep != null) {
                    val m = 0.5f + 0.5f * sin(time * 2f * PI.toFloat() * max(sweep.p1, 0.2f) + b * 0.25f)
                    lerp(base, if (s == 0) PHASE_A else PHASE_B, m * sweep.amount)
                } else base
                val hi = lerp(color, Color.White, 0.35f * len)
                // Lows at the bottom (angle π from the top), highs at the top; mirrored.
                val theta = PI.toFloat() - (b + 0.5f) * step + offset
                for (side in intArrayOf(1, -1)) {
                    val a = rotation + side * theta
                    val dir = Offset(sin(a), -cos(a))
                    val start = c + dir * inner
                    val end = c + dir * (inner + 2.dp.toPx() + reach * len)
                    drawLine(
                        Brush.linearGradient(listOf(color.copy(alpha = 0.9f * alpha), hi.copy(alpha = alpha)), start, end),
                        start, end, strokeWidth = barWidth, cap = if (lofi > 0f) StrokeCap.Butt else StrokeCap.Round,
                    )
                    val peak = c + dir * (inner + 4.dp.toPx() + reach * peaks[s][b])
                    drawCircle(hi.copy(alpha = 0.7f * alpha), radius = barWidth * 0.45f, center = peak)
                }
            }
        }

        // Beat and phrase ring.
        val ring = outer
        drawCircle(Dj.Border, radius = ring, center = c, style = Stroke(1.5f.dp.toPx()))
        val phrase = (barInPhrase + frac(beat / 4f)) / 8f
        drawArc(
            Dj.Accent.copy(alpha = 0.75f), startAngle = -90f, sweepAngle = 360f * phrase, useCenter = false,
            topLeft = c - Offset(ring, ring), size = Size(ring * 2, ring * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
        val beatInBar = floor(beat).toInt().mod(4)
        val beatFrac = frac(beat)
        for (i in 0 until 4) {
            val a = i * PI.toFloat() / 2
            val p = c + Offset(sin(a), -cos(a)) * ring
            val on = i == beatInBar
            val r = if (on) 5.dp.toPx() * (1.3f - 0.3f * beatFrac) else 3.dp.toPx()
            drawCircle(if (on) Dj.Text else Dj.Muted.copy(alpha = 0.6f), radius = r, center = p)
        }
        // Loop roll: the looped slice of the bar lit on the ring.
        if (roll != null) {
            val beats = max(roll.p1, 0.0625f)
            val start = floor(beat / beats) * beats
            drawArc(
                Dj.Blue, startAngle = -90f + 90f * (start % 4f), sweepAngle = 90f * beats, useCenter = false,
                topLeft = c - Offset(ring, ring), size = Size(ring * 2, ring * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Siren: red and blue beacons circling.
        active(FxType.SIREN)?.let { (fx, _) ->
            val a = time * 360f * 1.2f
            for ((k, col) in listOf(SIREN_RED, SIREN_BLUE).withIndex()) {
                drawArc(
                    col.copy(alpha = 0.75f * fx.amount), startAngle = a + 180f * k, sweepAngle = 70f, useCenter = false,
                    topLeft = c - Offset(ring, ring), size = Size(ring * 2, ring * 2), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        // Laser: beams firing out from the cover on each zap.
        active(FxType.LASER)?.let { (fx, s) ->
            val z = frac(beat / max(fx.p1, 0.125f))
            val a0 = time * 5f
            for (k in 0 until 3) {
                val a = a0 + k * 2f * PI.toFloat() / 3
                val dir = Offset(sin(a), -cos(a))
                drawLine(
                    lerp(deckColor(s), Color.White, 0.6f).copy(alpha = fx.amount * (1 - z)),
                    c + dir * inner, c + dir * (inner + (ring - inner) * (0.4f + 0.6f * z) + 20.dp.toPx()),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        // Whoosh: a bright sweep travelling round the ring.
        active(FxType.WHOOSH)?.let { (fx, _) ->
            drawArc(
                Color.White.copy(alpha = 0.5f * fx.amount * sin(PI.toFloat() * fx.progress)), startAngle = -90f + 360f * fx.progress - 40f,
                sweepAngle = 80f, useCenter = false, topLeft = c - Offset(ring, ring), size = Size(ring * 2, ring * 2),
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        // Shockwaves from hits.
        for (w in waves) {
            val age = (time - w.born) / w.life
            val r = inner + (outer * 1.15f - inner) * (1 - (1 - age).pow(2f))
            drawCircle(w.color.copy(alpha = 0.8f * (1 - age)), radius = r, center = c, style = Stroke(w.width.dp.toPx() * (1 - age) + 1f))
        }
    }

    /** Band index (fractional) of a frequency, matching [SpectrumTap]'s log spacing. */
    private fun bandOf(hz: Double): Float = (ln(hz / 40.0) / ln(16000.0 / 40.0) * n).toFloat().coerceIn(-1f, n.toFloat())

    private fun frac(x: Float) = x - floor(x)

    companion object {
        /** Cover diameter as a share of the visualizer's size. */
        const val ART_RATIO = 0.42f
        private const val GHOST_LIFE = 1.4f
        private val BUILDS = setOf(FxType.RISER, FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL, FxType.DOWNLIFTER)
        private val SIREN_RED = Color(0xFFFF4D5E)
        private val SIREN_BLUE = Color(0xFF4D7CFF)
        private val PHASE_A = Color(0xFFFF4FD8)
        private val PHASE_B = Color(0xFF4FFFD2)
    }
}
