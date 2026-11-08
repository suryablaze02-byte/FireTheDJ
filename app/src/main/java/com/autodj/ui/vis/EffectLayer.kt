package com.autodj.ui.vis

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.autodj.dj.fx.FxType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Effects and tricks, in Club only:
 *
 * - **Animation** per effect, on the LED wall behind the DJ ([Scene.effectArea]): flashes,
 *   shockwaves, siren beacons, laser fans, build-up glow and strobe, filter tints, blackout
 *   gates and more. No other look shows them.
 */
class EffectLayer {
    private val seen = HashSet<String>()

    private var flash = 0f
    private var shake = 0f
    private var lastShotBeat = -1

    private class Wave(val color: Color, val born: Float, val life: Float, val width: Float)

    private val waves = ArrayList<Wave>()

    /** Screen size from the last draw, for motion in pixels. */
    private var w = 1080f
    private var h = 2000f

    fun step(s: VisualState) {
        val t = s.time
        val keys = HashSet<String>()
        for ((a, slot) in s.fx) {
            val key = "$slot:${a.type}"
            keys += key
            if (key !in seen) onStart(a.type, slot, s)
            if (a.type == FxType.GUNSHOT) {
                val shot = kotlin.math.floor(s.beat).toInt()
                if (shot != lastShotBeat) {
                    lastShotBeat = shot
                    flash = max(flash, 0.7f)
                    shake = max(shake, 0.5f)
                }
            }
        }
        seen.clear()
        seen += keys

        flash = max(0f, flash - s.dt * 2.5f)
        shake = max(0f, shake - s.dt * 2f)
        waves.removeAll { t - it.born > it.life }
    }

    private fun onStart(type: FxType, slot: Int, s: VisualState) {
        val t = s.time
        when (type) {
            FxType.IMPACT, FxType.SUB_DROP -> {
                flash = 1f; shake = 1f
                waves += Wave(Color.White, t, 0.9f, 12f)
            }
            FxType.CRASH -> {
                flash = max(flash, 0.8f)
                waves += Wave(Color.White, t, 0.7f, 6f)
            }
            FxType.HORN -> {
                shake = max(shake, 0.5f)
                waves += Wave(Color(0xFFFFC23D), t, 0.8f, 8f)
            }
            FxType.BACKSPIN, FxType.BRAKE -> shake = max(shake, 0.3f)
            // The regional drums: a marigold shockwave and a shake on every fill.
            FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL, FxType.TIMBALES, FxType.DARBUKA -> {
                shake = max(shake, 0.6f)
                waves += Wave(Color(0xFFFFB020), t, 0.8f, 9f)
            }
            // The gap before a drop: everything blacks out for a moment.
            FxType.MUTE -> flash = 0f
            else -> Unit
        }
    }

    /** The effects' light animations, filling the area they're given (Club's LED wall). */
    fun drawOverlays(ds: DrawScope, s: VisualState) = with(ds) {
        w = size.width
        h = size.height
        val t = s.time
        val pi = PI.toFloat()
        val diag = hypot(w, h)

        // Filters: high-pass goes cold and thin, low-pass dark and underwater.
        s.active(FxType.HIGH_PASS)?.let { (a, _) -> drawRect(Color(0xFF7FB8FF).copy(alpha = 0.22f * a.amount), blendMode = BlendMode.Plus) }
        s.active(FxType.LOW_PASS)?.let { (a, _) ->
            drawRect(Color(0xFF06232A).copy(alpha = 0.55f * a.amount))
            vignette(0.6f * a.amount)
        }
        // Flanger and phaser: the colours cycle.
        (s.active(FxType.FLANGER) ?: s.active(FxType.PHASER))?.let { (a, _) ->
            drawRect(Color.hsv((t * 140f) % 360f, 0.8f, 1f).copy(alpha = 0.22f * a.amount), blendMode = BlendMode.Plus)
        }
        // Vocal tools: a spotlight on the centre (acapella) or a darkened centre (vocals out).
        s.active(FxType.VOCAL_ISOLATE)?.let { (a, _) ->
            drawRect(Color.Black.copy(alpha = 0.55f * a.amount))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1D6).copy(alpha = 0.45f * a.amount), Color.Transparent), center, min(w, h) * 0.5f), min(w, h) * 0.5f, center, blendMode = BlendMode.Plus)
        }
        s.active(FxType.VOCAL_CUT)?.let { (a, _) ->
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f * a.amount), Color.Transparent), center, min(w, h) * 0.55f), min(w, h) * 0.55f, center)
        }
        // Lo-fi: scanlines.
        s.active(FxType.LOFI)?.let { (a, _) ->
            var y = 0f
            while (y < h) { drawRect(Color.Black.copy(alpha = 0.4f * a.amount), Offset(0f, y), Size(w, 3f)); y += 7f }
        }
        // Siren: red and blue washing in from either side, a beacon sweeping round.
        s.active(FxType.SIREN)?.let { (a, _) ->
            val k = 0.5f + 0.5f * sin(t * pi * 4f)
            drawRect(Brush.horizontalGradient(listOf(SIREN_RED.copy(alpha = 0.5f * a.amount * k), Color.Transparent), 0f, w * 0.6f), blendMode = BlendMode.Plus)
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, SIREN_BLUE.copy(alpha = 0.5f * a.amount * (1 - k))), w * 0.4f, w), blendMode = BlendMode.Plus)
            drawArc(
                (if (k > 0.5f) SIREN_RED else SIREN_BLUE).copy(alpha = 0.2f * a.amount), startAngle = t * 340f, sweepAngle = 30f, useCenter = true,
                topLeft = center - Offset(diag, diag), size = Size(diag * 2, diag * 2), blendMode = BlendMode.Plus,
            )
        }
        // Lasers: fans from the bottom corners (and centre in landscape).
        s.active(FxType.LASER)?.let { (a, _) ->
            val origins = if (w > h) listOf(Offset(0f, h), Offset(w / 2, h), Offset(w, h)) else listOf(Offset(0f, h), Offset(w, h))
            origins.forEachIndexed { j, o ->
                for (k in 0 until 12) {
                    val ang = -pi / 2 + (k - 5.5f) * 0.09f + 0.6f * sin(t * 1.7f + j * 2) * (if (j % 2 == 0) -1 else 1)
                    val col = if (k % 3 == 0) LASER_PINK else LASER_GREEN
                    drawLine(col.copy(alpha = 0.6f * a.amount), o, o + Offset(cos(ang), sin(ang)) * diag, 3f, blendMode = BlendMode.Plus)
                }
            }
        }
        // Whoosh: a streak racing across.
        s.active(FxType.WHOOSH)?.let { (a, _) ->
            val x = -w * 0.3f + w * 1.6f * a.progress
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.4f * a.amount), Color.Transparent), x - w * 0.25f, x + w * 0.25f), blendMode = BlendMode.Plus)
        }
        // Downlifter: light draining down the screen.
        s.active(FxType.DOWNLIFTER)?.let { (a, _) ->
            val y = h * a.progress
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f * a.amount * (1 - a.progress)), Color.Transparent), y - h * 0.2f, y + h * 0.05f), blendMode = BlendMode.Plus)
        }
        // Build-ups: light rising from the bottom, streaks speeding up, a strobe over the last stretch, a progress line.
        val build = s.fx.filter { it.first.type in BUILDS }.maxByOrNull { it.first.progress }?.first
        if (build != null) {
            val p = build.progress
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFDCB4).copy(alpha = 0.4f * p), Color.Transparent), h, h * (1 - p)), blendMode = BlendMode.Plus)
            for (i in 0 until 26) {
                val x = w * ((i * 0.618f) % 1f)
                val y = h * (1 - s.frac(t * (0.5f + 2f * p) + i * 0.27f))
                drawLine(Color.White.copy(alpha = 0.15f + 0.3f * p), Offset(x, y), Offset(x, y - h * (0.04f + 0.12f * p)), 2f)
            }
            val rate = if (p > 0.7f) 2f + 8f * (p - 0.7f) / 0.3f else 0f
            if (rate > 0f && s.frac(s.beat * rate) < 0.2f) drawRect(Color.White.copy(alpha = 0.3f))
            drawRect(Color.White.copy(alpha = 0.85f), Offset(0f, h - 5f), Size(w * p, 5f))
        }
        // Reverse cymbal: light rushing in toward the centre.
        s.active(FxType.REVERSE_CYMBAL)?.let { (a, _) ->
            drawCircle(Color.White.copy(alpha = 0.3f * a.progress), max(w, h) * (1.2f - a.progress), center, style = Stroke(30f * a.progress + 2f))
        }
        // Loop roll: strobe on the loop.
        s.active(FxType.LOOP_ROLL)?.let { (a, _) ->
            if (s.frac(s.beat / max(a.p1, 0.0625f)) < 0.18f) drawRect(Color.White.copy(alpha = 0.3f))
        }
        // Spinback: rewind lines rolling up.
        s.active(FxType.BACKSPIN)?.let { (a, _) ->
            var y = h * (1 - s.frac(t * 3f))
            repeat(6) {
                drawRect(Color.White.copy(alpha = 0.1f * (1 - a.progress)), Offset(0f, y), Size(w, 20f))
                y -= h / 6
                if (y < 0) y += h
            }
        }
        // Shockwaves.
        for (wv in waves) {
            val age = (t - wv.born) / wv.life
            drawCircle(wv.color.copy(alpha = 0.7f * (1 - age)), diag * 0.6f * age, center, style = Stroke(wv.width * (1 - age) * 3f + 1f))
        }
        // Darkness: brakes sink the room, gates chop it, pause dims it.
        s.active(FxType.BRAKE)?.let { (a, _) -> drawRect(Color.Black.copy(alpha = 0.65f * a.progress)) }
        s.active(FxType.GATE)?.let { (a, _) -> if (s.frac(s.beat / max(a.p1, 0.0625f)) >= 0.5f) drawRect(Color.Black.copy(alpha = 0.85f * a.amount)) }
        s.active(FxType.TREMOLO)?.let { (a, _) -> drawRect(Color.Black.copy(alpha = 0.5f * a.amount * (0.5f - 0.5f * cos(2 * pi * s.beat / max(a.p1, 0.0625f))))) }
        if (flash > 0f) drawRect(Color.White.copy(alpha = 0.65f * flash.pow(1.5f)))
    }

    /** The big full-screen name of the effect that just started, or the small tag of a long one still running. */
    private fun DrawScope.vignette(alpha: Float) {
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = alpha)), center, max(size.width, size.height) * 0.75f))
    }

    companion object {
        private val BUILDS = setOf(FxType.RISER, FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL)
        private val HOT = setOf(FxType.IMPACT, FxType.SUB_DROP, FxType.HORN, FxType.CRASH, FxType.GUNSHOT, FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL, FxType.TIMBALES, FxType.DARBUKA)
        private val SIREN_RED = Color(0xFFFF2D4A)
        private val SIREN_BLUE = Color(0xFF2D6BFF)
        private val LASER_GREEN = Color(0xFF3DFF6E)
        private val LASER_PINK = Color(0xFFFF3CC8)

        private val CALLOUT_COLOR = mapOf(
            FxType.IMPACT to Color.White, FxType.SUB_DROP to Color.White, FxType.HORN to Color(0xFFFFC23D),
            FxType.CRASH to Color.White, FxType.GUNSHOT to Color(0xFFFFE6A0), FxType.LASER to Color(0xFF3DFF6E),
            FxType.HIGH_PASS to Color(0xFF8FD0FF), FxType.LOW_PASS to Color(0xFF6FE0D0), FxType.RISER to Color(0xFFFFE1C2),
            FxType.SNARE_ROLL to Color(0xFFFFE1C2), FxType.REVERSE_CYMBAL to Color.White,
            // The regional drums burn marigold; the reed glows warm.
            FxType.DAPPU to Color(0xFFFFB020), FxType.MARFA to Color(0xFFFFB020), FxType.DHOL to Color(0xFFFFB020),
            FxType.THAVIL to Color(0xFFFFD36B), FxType.NADASWARAM to Color(0xFFFFE6A0),
            // Latin sounds in carnival green and gold, Arabic ones in turquoise.
            FxType.TIMBALES to Color(0xFFFFD23D), FxType.COWBELL to Color(0xFFFFD23D), FxType.WHISTLE to Color(0xFF3DFF9E),
            FxType.GUIRA to Color(0xFF3DFF9E), FxType.SHAKER to Color(0xFF3DFF9E),
            FxType.DARBUKA to Color(0xFF3DE0D0), FxType.CLAPS to Color(0xFF3DE0D0), FxType.MIZMAR to Color(0xFF8FF5EA),
        )

        /** The colour the screen shouts [type] in; one without its own takes a warm white. */
        fun calloutColor(type: FxType): Color = CALLOUT_COLOR[type] ?: Color(0xFFFFE1C2)

        /** The loud hits (drums, horns, drops): their words burn with an orange glow. */
        fun isHot(type: FxType) = type in HOT

        /** What the screen shouts when an effect kicks in. */
        fun label(type: FxType) = when (type) {
            FxType.HIGH_PASS -> "FILTER UP"
            FxType.LOW_PASS -> "FILTER DOWN"
            FxType.ECHO -> "ECHO"
            FxType.REVERB -> "REVERB"
            FxType.LOOP_ROLL -> "LOOP ROLL"
            FxType.SCRATCH -> "SCRATCH"
            FxType.BACKSPIN -> "SPINBACK"
            FxType.BRAKE -> "BRAKE"
            FxType.GATE -> "STUTTER"
            FxType.TREMOLO -> "TREMOLO"
            FxType.FLANGER -> "FLANGER"
            FxType.PHASER -> "PHASER"
            FxType.LOFI -> "LO-FI"
            FxType.VOCAL_CUT -> "VOCALS OUT"
            FxType.VOCAL_ISOLATE -> "ACAPELLA"
            FxType.BASS_BOOST -> "BASS BOOST"
            FxType.BASS_KILL -> "BASS OUT"
            FxType.MID_CUT -> "MIDS OUT"
            FxType.TREBLE_CUT -> "MUFFLED"
            // Never reaches the visualizers (MixEngine leaves it out of their frames).
            FxType.STEM_ACAPELLA -> "ACAPELLA"
            FxType.SIREN -> "SIREN"
            FxType.HORN -> "AIR HORN"
            FxType.LASER -> "LASERS"
            FxType.RISER -> "BUILD UP"
            FxType.IMPACT -> "DROP"
            FxType.SNARE_ROLL -> "SNARE ROLL"
            FxType.REVERSE_CYMBAL -> "REVERSE"
            FxType.CRASH -> "CRASH"
            FxType.SUB_DROP -> "SUB DROP"
            FxType.DOWNLIFTER -> "DOWN"
            FxType.WHOOSH -> "WHOOSH"
            FxType.GUNSHOT -> "PULL UP"
            FxType.MUTE -> "SILENCE"
            FxType.DAPPU -> "DAPPU"
            FxType.MARFA -> "MARFA"
            FxType.DHOL -> "DHOL"
            FxType.THAVIL -> "THAVIL"
            FxType.NADASWARAM -> "NADASWARAM"
            FxType.WHISTLE -> "WHISTLE"
            FxType.TIMBALES -> "TIMBALES"
            FxType.COWBELL -> "COWBELL"
            FxType.GUIRA -> "GÜIRA"
            FxType.SHAKER -> "SHAKER"
            FxType.CLAPS -> "CLAPS"
            FxType.DARBUKA -> "DARBUKA"
            FxType.MIZMAR -> "MIZMAR"
        }
    }
}
