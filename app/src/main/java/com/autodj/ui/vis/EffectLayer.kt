package com.autodj.ui.vis

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.autodj.dj.fx.FxType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Effects and tricks, drawn the same way over every [Scene]:
 *
 * - **Callouts:** the effect's name fills the screen as it starts, moving like the effect
 *   (DROP slams in, ECHO repeats, SIREN flashes red and blue, SPINBACK spins…); a small tag
 *   stays up while a long effect runs.
 * - **Fire cannons:** flame columns from the bottom edge on bass drops (impacts, sub drops, and
 *   the bass returning after a breakdown), and a left-to-right chase on crashes and horns.
 * - **Full-screen animation** per effect: shake, flashes, shockwaves, siren beacons, laser
 *   fans, build-up glow and strobe, filter tints, blackout gates, rewind spins and more.
 */
class EffectLayer {
    private val rnd = Random(29)
    private val seen = HashSet<String>()

    private var calloutType: FxType? = null
    private var calloutColor = Color.White
    private var calloutAge = 9f
    private var calloutLayout: TextLayoutResult? = null
    private var calloutFor: String? = null
    private var tagLayout: TextLayoutResult? = null
    private var tagFor: String? = null

    private var flash = 0f
    private var shake = 0f
    private var lastShotBeat = -1
    private var lastDropAt = -99f

    private class Wave(val color: Color, val born: Float, val life: Float, val width: Float)
    private class Cannon(val x: Float, val start: Float, val dur: Float)
    private class Flame(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float, val life: Float, val size: Float)

    private val waves = ArrayList<Wave>()
    private val cannons = ArrayList<Cannon>()
    private val flames = ArrayList<Flame>()

    /** Screen size from the last draw, for spawning flames in pixels. */
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

        // A drop the audio makes on its own: the low end slamming back after a quiet stretch. The
        // slow average needs some music behind it first, or the opening kick would count.
        if (!s.paused && t > 10f && s.bass > 0.55f && s.slowBass < 0.3f && t - lastDropAt > 20f) {
            lastDropAt = t
            fireCannons(t, chase = false)
        }

        calloutAge += s.dt
        flash = max(0f, flash - s.dt * 2.5f)
        shake = max(0f, shake - s.dt * 2f)
        waves.removeAll { t - it.born > it.life }

        // Cannons pour flame while they burn; flames rise, slow and fade.
        for (c in cannons) {
            val age = t - c.start
            if (age < 0f || age > c.dur) continue
            val count = (14 * (1 - age / c.dur)).toInt() + 4
            repeat(count) {
                flames += Flame(
                    c.x * w + (rnd.nextFloat() - 0.5f) * w * 0.03f, h + 6f, (rnd.nextFloat() - 0.5f) * w * 0.15f,
                    -(h * (1.5f + rnd.nextFloat() * 0.7f)), 0f, 0.55f + rnd.nextFloat() * 0.35f, min(w, h) * (0.035f + rnd.nextFloat() * 0.03f),
                )
            }
        }
        cannons.removeAll { t - it.start > it.dur + 0.5f }
        val dt = if (s.paused) 0f else s.dt
        val it = flames.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.age += dt
            if (f.age >= f.life) { it.remove(); continue }
            f.x += f.vx * dt
            f.y += f.vy * dt
            f.vy += h * 1.2f * dt
            f.vx *= 0.98f
        }
    }

    private fun onStart(type: FxType, slot: Int, s: VisualState) {
        val t = s.time
        calloutType = type
        calloutColor = CALLOUT_COLOR[type] ?: lerp(s.deckColor(slot), Color.White, 0.5f)
        calloutAge = 0f
        when (type) {
            FxType.IMPACT, FxType.SUB_DROP -> {
                flash = 1f; shake = 1f
                waves += Wave(Color.White, t, 0.9f, 12f)
                fireCannons(t, chase = false)
                lastDropAt = t
            }
            FxType.CRASH -> {
                flash = max(flash, 0.8f)
                waves += Wave(Color.White, t, 0.7f, 6f)
                if (s.energy > 0.45f) fireCannons(t, chase = true)
            }
            FxType.HORN -> {
                shake = max(shake, 0.5f)
                waves += Wave(Color(0xFFFFC23D), t, 0.8f, 8f)
                if (s.energy > 0.45f) fireCannons(t, chase = true)
            }
            FxType.BACKSPIN, FxType.BRAKE -> shake = max(shake, 0.3f)
            // The regional drums: a marigold shockwave and a shake on every fill.
            FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL -> {
                shake = max(shake, 0.6f)
                waves += Wave(Color(0xFFFFB020), t, 0.8f, 9f)
                if (s.energy > 0.45f) fireCannons(t, chase = true)
            }
            // The gap before a drop: everything blacks out for a moment.
            FxType.MUTE -> flash = 0f
            else -> Unit
        }
    }

    private fun fireCannons(t: Float, chase: Boolean) {
        val n = if (w > h) 6 else 4
        for (i in 0 until n) {
            cannons += Cannon((i + 0.5f) / n, t + if (chase) i * 0.12f else (i % 2) * 0.05f, 1.1f)
        }
    }

    /** Shake, scratch jitter and spinback, applied to the scene before it's drawn. */
    fun motion(s: VisualState): Triple<Float, Float, Float> {
        val t = s.time
        var x = shake * 16f * sin(t * 90f)
        val y = shake * 12f * cos(t * 70f)
        if (s.active(FxType.SCRATCH) != null) x += w * 0.03f * sin(t * 40f)
        val spin = s.active(FxType.BACKSPIN)?.first
        val rot = if (spin != null) -400f * (1 - (1 - spin.progress).pow(3f)) * (1 - spin.progress) else 0f
        return Triple(x, y, rot)
    }

    /** Echo: afterimages of the scene, scale and alpha, drawn behind the overlays. */
    fun echoCopies(s: VisualState): List<Pair<Float, Float>> =
        if (s.active(FxType.ECHO) != null) listOf(1.06f to 0.35f, 1.12f to 0.18f) else emptyList()

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
        // Fire cannons.
        if (flames.isNotEmpty()) {
            var heat = 0f
            for (c in cannons) {
                val age = t - c.start
                if (age in 0f..c.dur) heat = max(heat, 1 - age / c.dur)
            }
            for (f in flames) {
                val u = f.age / f.life
                val r = f.size * (0.6f + u * 1.6f)
                val col = when {
                    u < 0.25f -> Color(0xFFFFF5C8)
                    u < 0.55f -> Color(0xFFFFAA3C)
                    else -> Color(0xFFFF501E)
                }
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * (1 - u)), Color.Transparent), Offset(f.x, f.y), r), r, Offset(f.x, f.y), blendMode = BlendMode.Plus)
            }
            if (heat > 0f) drawRect(Brush.verticalGradient(listOf(Color(0xFFFF7828).copy(alpha = 0.45f * heat), Color.Transparent), h, h * 0.4f))
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
        if (s.paused) drawRect(Color.Black.copy(alpha = 0.45f))
        if (flash > 0f) drawRect(Color.White.copy(alpha = 0.65f * flash.pow(1.5f)))
    }

    /** The big full-screen name of the effect that just started, or the small tag of a long one still running. */
    fun drawCallout(ds: DrawScope, s: VisualState, measurer: TextMeasurer) = with(ds) {
        val type = calloutType
        if (type == null || calloutAge > CALLOUT_SEC) {
            val long = s.fx.filter { it.first.progress < 0.95f }.maxByOrNull { it.first.amount }?.first ?: return
            val text = label(long.type)
            if (tagFor != text) {
                tagLayout = measurer.measure(text, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White))
                tagFor = text
            }
            val l = tagLayout!!
            val pw = l.size.width + 36f
            val ph = l.size.height + 16f
            val top = h * 0.06f
            drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset((w - pw) / 2, top), Size(pw, ph), CornerRadius(ph / 2))
            drawText(l, topLeft = Offset((w - l.size.width) / 2, top + 8f))
            return
        }
        val text = label(type)
        if (calloutFor != text) {
            calloutLayout = measurer.measure(text, TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
            calloutFor = text
        }
        val layout = calloutLayout!!
        val age = calloutAge
        val inA = min(1f, age / 0.12f)
        val outA = if (age > 1.2f) 1 - (age - 1.2f) / (CALLOUT_SEC - 1.2f) else 1f
        val a = min(inA, outA).coerceIn(0f, 1f)
        // Strobed effects flash the word on and off with the loop.
        if ((type == FxType.LOOP_ROLL || type == FxType.GATE) && s.frac(s.beat * 4f) > 0.55f) return
        drawRect(Color.Black.copy(alpha = 0.35f * a))
        val fit = min(w * 0.88f / layout.size.width, h * (if (w > h) 0.45f else 0.3f) / layout.size.height)
        var scale = fit
        var rot = 0f
        var dx = 0f
        when (type) {
            FxType.IMPACT, FxType.SUB_DROP -> scale *= 1f + 2.5f * (1 - inA).pow(3f)
            FxType.RISER, FxType.SNARE_ROLL -> scale *= 0.85f + 0.2f * min(1f, age)
            FxType.BACKSPIN -> rot = -360f * min(1f, age * 1.2f)
            FxType.SCRATCH -> dx = sin(age * 60f) * w * 0.04f
            FxType.HORN, FxType.CRASH, FxType.GUNSHOT, FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL -> scale *= 1f + 0.08f * abs(sin(age * 12f))
            else -> Unit
        }
        val color = if (type == FxType.SIREN) (if ((age * 8).toInt() % 2 == 0) SIREN_RED else SIREN_BLUE) else calloutColor
        val topLeft = Offset((w - layout.size.width) / 2f + dx, (h - layout.size.height) / 2f)
        withTransform({
            rotate(rot, center)
            scale(scale, scale, center)
        }) {
            if (type == FxType.ECHO) {
                for (i in 4 downTo 1) {
                    drawText(layout, color = color, topLeft = topLeft - Offset(0f, i * layout.size.height * 0.42f * min(1f, age * 2f)), alpha = a * 0.18f * (5 - i) / 4f)
                }
            }
            drawText(
                layout, color = color, topLeft = topLeft, alpha = a,
                shadow = Shadow(if (type in HOT) Color(0xFFFF6414) else Color.White.copy(alpha = 0.5f), blurRadius = 40f),
            )
        }
    }

    private fun DrawScope.vignette(alpha: Float) {
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = alpha)), center, max(size.width, size.height) * 0.75f))
    }

    companion object {
        private const val CALLOUT_SEC = 1.7f
        private val BUILDS = setOf(FxType.RISER, FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL)
        private val HOT = setOf(FxType.IMPACT, FxType.SUB_DROP, FxType.HORN, FxType.CRASH, FxType.GUNSHOT, FxType.DAPPU, FxType.MARFA, FxType.DHOL, FxType.THAVIL)
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
        )

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
        }
    }
}
