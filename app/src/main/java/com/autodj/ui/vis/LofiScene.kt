package com.autodj.ui.vis

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lo-fi Vibe: a city rooftop at dusk in sunset pinks and violets. The skyline is an equaliser
 * (each building's lit floors climb with its slice of the spectrum), string lights glow with
 * the music, the boombox pumps with the bass, and a cat watches it all from the ledge.
 */
class LofiScene : Scene {
    private var lastBeat = Int.MIN_VALUE
    private var ear = 0f
    private var reel = 0f
    private var drop = 0f
    private var dropCool = 0f

    /** A shooting star every other bar: where it starts (fractions of the screen) and how far along it is. */
    private var shootX = 0f
    private var shootY = 0f
    private var shoot = -1f

    private val grain: ShaderBrush = run {
        val rnd = Random(5)
        val pixels = IntArray(160 * 160) { val v = rnd.nextInt(256); (if (rnd.nextBoolean()) 36 shl 24 else 0) or (v shl 16) or (v shl 8) or v }
        val bmp = Bitmap.createBitmap(pixels, 160, 160, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    private val stars = Random(9).let { r -> Array(50) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.3f, 1.2f + r.nextFloat() * 1.4f, r.nextFloat() * 6f) } }
    private val clouds = Random(4).let { r -> Array(6) { floatArrayOf(r.nextFloat(), 0.18f + r.nextFloat() * 0.25f, 0.12f + r.nextFloat() * 0.14f, 0.004f + r.nextFloat() * 0.006f) } }

    /** Width and height (fractions of the screen) of each building in the skyline, one per slice of the spectrum. */
    private val buildings = Random(8).let { r -> Array(TOWERS) { floatArrayOf(0.045f + r.nextFloat() * 0.03f, 0.16f + r.nextFloat() * 0.3f) } }
    private val windowSeed = Random(2).let { r -> FloatArray(TOWERS * 64) { r.nextFloat() } }
    private val wire = Path()
    private val leaf = Path()

    override fun step(s: VisualState) {
        val dt = s.dt
        val b = floor(s.beat).toInt()
        if (!s.paused && b != lastBeat) {
            if (lastBeat != Int.MIN_VALUE && b.mod(4) == 0) {
                ear = 1f
                if (b.mod(8) == 0 && shoot < 0f) {
                    shootX = 0.05f + Random.nextFloat() * 0.5f
                    shootY = 0.04f + Random.nextFloat() * 0.12f
                    shoot = 0f
                }
            }
            lastBeat = b
        }
        if (shoot >= 0f) {
            shoot += dt * 1.5f
            if (shoot >= 1f) shoot = -1f
        }
        dropCool -= dt
        if (!s.paused && dropCool <= 0f && s.bass > 0.45f && s.bass - s.slowBass > 0.2f) {
            dropCool = 12f
            drop = 1f
        }
        drop *= 0.3f.pow(dt)
        ear *= 0.02f.pow(dt)
        if (!s.paused) reel += dt * 3f
    }

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val t = s.time
        val hz = h * 0.62f
        val base = h * 0.74f
        val u = min(h / 560f, w / 700f)
        val bass = min(1f, s.bass * 0.7f + s.kick * 0.5f)

        // Dusk: violet overhead to peach at the horizon; the top brightens with the bass.
        val l = bass * 0.08f + drop * 0.2f
        drawRect(
            Brush.verticalGradient(
                0f to Color(min(1f, (40 + l * 200) / 255f), min(1f, (36 + l * 120) / 255f), min(1f, (86 + l * 160) / 255f)),
                0.45f to Color(0xFF7A4A96), 0.78f to Color(0xFFFF8FA3), 1f to Color(0xFFFFCF9E), startY = 0f, endY = hz,
            ),
            size = Size(w, hz),
        )
        for (st in stars) {
            val a = (0.25f + 0.35f * sin(t * 1.3f + st[3])).coerceIn(0f, 1f) * (1 - st[1] * 2)
            drawRect(Color.White.copy(alpha = a.coerceIn(0f, 1f)), Offset(st[0] * w, st[1] * h), Size(st[2], st[2]))
        }
        val sr = h * 0.12f * (1 + bass * 0.06f)
        val sun = Offset(w * 0.62f, hz - sr * 0.35f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB27A).copy(alpha = 0.4f + bass * 0.3f), Color.Transparent), sun, sr * 2f), sr * 2f, sun)
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE0A0), Color(0xFFFF7A8A)), startY = sun.y - sr, endY = sun.y + sr), sr, sun)
        for (c in clouds) {
            val cx = ((c[0] + t * c[3]) % 1.3f - 0.15f) * w
            val cy = c[1] * h
            val cw = c[2] * w
            drawOval(Color(0x59FFAABE), Offset(cx - cw * 0.5f, cy - h * 0.022f), Size(cw, h * 0.044f))
            drawOval(Color(0x59FFAABE), Offset(cx + cw * 0.15f - cw * 0.28f, cy - h * 0.035f), Size(cw * 0.56f, h * 0.04f))
        }
        if (shoot >= 0f) {
            val x = (shootX + shoot * 0.25f) * w
            val y = (shootY + shoot * 0.12f) * h
            drawLine(Color.White.copy(alpha = 1 - shoot), Offset(x, y), Offset(x - w * 0.06f, y - h * 0.03f), 2f * u, StrokeCap.Round)
        }

        drawRect(Color(0xFF3A2F60), Offset(0f, hz), Size(w, base - hz))
        drawSkyline(s, w, h, base, t)
        drawRect(Color(0x14FF96AA), Offset(0f, hz - h * 0.05f), Size(w, h * 0.12f))

        drawTower(w * 0.12f, base + h * 0.02f, u)
        drawRect(Color(0xFF3A2F4E), Offset(0f, base), Size(w, h - base))
        drawRect(Color(0xFF4A3C60), Offset(0f, base), Size(w, h * 0.035f))
        for (k in 0 until 20) drawLine(Color(0x2E000000), Offset(k * w / 20, base + h * 0.035f), Offset(k * w / 20, h), 1f)
        drawLine(Color(0xFF2A1D36), Offset(w * 0.93f, base), Offset(w * 0.93f, h * 0.28f), 5f * u)
        drawLights(s, w * 0.12f, w * 0.93f, h * 0.3f, h * 0.1f, t)

        drawCat(w * 0.33f, base, u, sin(t * 1.4f + bass))
        drawBoombox(s, w * 0.63f, base, u * 0.95f, bass)
        drawPlant(w * 0.46f, base, u, 0, t, bass)
        drawPlant(w * 0.82f, base, u * 1.15f, 1, t, bass)

        drawRect(grain)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x80140620)), center, max(w, h) * 0.75f))
    }

    /** Each building is one slice of the spectrum: its lit floors fill from the street up. */
    private fun DrawScope.drawSkyline(s: VisualState, w: Float, h: Float, base: Float, t: Float) {
        val per = s.n / TOWERS
        var x = -w * 0.02f
        val floorH = h * 0.028f
        for (i in 0 until TOWERS) {
            val bw = buildings[i][0] * w
            val bh = buildings[i][1] * h
            var v = 0f
            for (j in 0 until per) v += s.mix[i * per + j]
            v = min(1f, v / per * 0.85f + drop * 0.6f)
            drawRect(TOWER_COLORS[i % TOWER_COLORS.size], Offset(x, base - bh), Size(bw, bh))
            if (i % 5 == 2) {
                drawRect(TOWER_COLORS[i % TOWER_COLORS.size], Offset(x + bw * 0.45f, base - bh - h * 0.04f), Size(2f, h * 0.04f))
                drawRect(if (sin(t * 3 + i) > 0) Color(0xFFFF6A7A) else Color(0xFF4A2030), Offset(x + bw * 0.45f - 1, base - bh - h * 0.045f), Size(4f, 4f))
            }
            val rows = (bh / floorH).toInt()
            val cols = max(2, (bw / (w * 0.012f)).toInt())
            val lit = (v * rows).roundToInt()
            val cw = bw * 0.76f / cols
            for (r in 0 until rows) {
                val on = r < lit
                for (k in 0 until cols) {
                    val sd = windowSeed[(i * 64 + r * 7 + k) % windowSeed.size]
                    if (!on && sd < 0.85f) continue
                    val a = if (on) 0.55f + 0.45f * sd else 0.25f
                    val col = if (on && r == lit - 1) Color(1f, 0.94f, 0.78f, a) else Color(1f, (190 + sd * 50) / 255f, (130 + sd * 60) / 255f, a)
                    drawRect(col, Offset(x + bw * 0.12f + k * cw, base - (r + 1) * floorH + h * 0.008f), Size(cw * 0.6f, h * 0.014f))
                }
            }
            x += bw + w * 0.006f
        }
    }

    private fun DrawScope.drawTower(x: Float, base: Float, u: Float) {
        withTransform({ translate(x, base); scale(u, u, Offset.Zero) }) {
            val dark = Color(0xFF2A1D36)
            for (lx in floatArrayOf(-40f, 40f)) drawLine(dark, Offset(lx, 0f), Offset(lx * 0.7f, -110f), 6f)
            drawLine(dark, Offset(-36f, -30f), Offset(34f, -80f), 3f)
            drawLine(dark, Offset(36f, -30f), Offset(-34f, -80f), 3f)
            drawRoundRect(Color(0xFF3A2A48), Offset(-50f, -230f), Size(100f, 124f), CornerRadius(8f))
            for (k in 1 until 4) drawLine(dark, Offset(-50f, -230f + k * 31), Offset(50f, -230f + k * 31), 3f)
            drawPath(ROOF, dark)
        }
    }

    /** The string of bulbs between the water tower and the pole: each glows with its own band. */
    private fun DrawScope.drawLights(s: VisualState, x0: Float, x1: Float, y: Float, sag: Float, t: Float) {
        val n = 20
        wire.reset()
        for (k in 0..n) {
            val f = k / n.toFloat()
            val xx = x0 + (x1 - x0) * f
            val yy = y + sin(f * PI.toFloat()) * sag + sin(t * 1.5f + f * 6) * 2
            if (k == 0) wire.moveTo(xx, yy) else wire.lineTo(xx, yy)
        }
        drawPath(wire, Color(0xFF1E1428), style = Stroke(1.5f))
        val glow = size.height / 560f
        for (k in 1 until n) {
            val f = k / n.toFloat()
            val v = min(1f, s.mix[(k * 2).coerceAtMost(s.n - 1)] * 1.3f)
            val col = BULBS[k % BULBS.size]
            val p = Offset(x0 + (x1 - x0) * f, y + sin(f * PI.toFloat()) * sag + sin(t * 1.5f + f * 6) * 2 + (5 + v * 5) * glow)
            val r = (10 + v * 16 + drop * 10) * glow
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = min(1f, 0.3f + v * 0.7f)), Color.Transparent), p, r), r, p, blendMode = BlendMode.Plus)
            drawOval(col, Offset(p.x - 3 * glow, p.y - 4 * glow), Size(6 * glow, 8 * glow))
        }
    }

    private fun DrawScope.drawCat(x: Float, y: Float, u: Float, tail: Float) {
        withTransform({ translate(x, y); scale(u, u, Offset.Zero) }) {
            val fur = Color(0xFF1E1428)
            drawOval(fur, Offset(-20f, -54f), Size(40f, 56f))
            drawCircle(fur, 15f, Offset(0f, -62f))
            drawPath(Path().apply { moveTo(-13f, -68f); lineTo(-10f, -86f - ear * 5); lineTo(-2f, -74f); close() }, fur)
            drawPath(CAT_EAR, fur)
            drawPath(Path().apply { moveTo(16f, -4f); quadraticTo(40f, 6f, 36f + tail * 6, 26f + tail * 4) }, fur, style = Stroke(7f, cap = StrokeCap.Round))
        }
    }

    /** Squashes on the bass, cones pumping, the tape turning, a little equaliser on the front. */
    private fun DrawScope.drawBoombox(s: VisualState, x: Float, y: Float, u: Float, bass: Float) {
        val sq = 1 + bass * 0.04f
        withTransform({ translate(x, y); scale(u * sq, u / sq, Offset.Zero) }) {
            val dark = Color(0xFF2A1D36)
            drawRoundRect(Color(0xFFE86A7A), Offset(-110f, -90f), Size(220f, 90f), CornerRadius(12f))
            drawRect(Color(0xFFC84A62), Offset(-110f, -20f), Size(220f, 20f))
            drawPath(HANDLE, dark, style = Stroke(6f))
            for (sx in floatArrayOf(-70f, 70f)) {
                val r = 28 * (1 + bass * 0.22f + drop * 0.15f)
                drawCircle(dark, 32f, Offset(sx, -48f))
                drawCircle(Color(0xFF4A3458), r * 0.85f, Offset(sx, -48f))
                drawCircle(Color(0xFFFFD59A), r * 0.25f, Offset(sx, -48f))
            }
            drawRoundRect(dark, Offset(-30f, -78f), Size(60f, 34f), CornerRadius(4f))
            for (rx in floatArrayOf(-14f, 14f)) {
                val c = Offset(rx, -61f)
                drawCircle(Color(0xFFFFD59A), 8f, c, style = Stroke(2f))
                for (k in 0 until 3) {
                    val a = reel + k * 2.09f
                    drawLine(Color(0xFFFFD59A), c, c + Offset(sin(a), cos(a)) * 7f, 2f)
                }
            }
            for (k in 0 until 8) {
                val v = min(1f, s.mix[(k * 5).coerceAtMost(s.n - 1)] * 1.3f)
                drawRect(BULBS[k % BULBS.size], Offset(-28f + k * 7, -38f - v * 14), Size(5f, 3 + v * 14))
            }
        }
    }

    private fun DrawScope.drawPlant(x: Float, y: Float, u: Float, k: Int, t: Float, bass: Float) {
        withTransform({ translate(x, y); scale(u, u, Offset.Zero) }) {
            drawPath(POT, Color(0xFFC86A5A))
            val col = if (k == 1) Color(0xFF3A6A6A) else Color(0xFF4A6A5A)
            for (j in 0 until 6) {
                val a = (j - 2.5f) * 0.4f + sin(t * 0.9f + j + k) * 0.05f + bass * 0.04f * (if (j % 2 == 1) 1 else -1)
                withTransform({ translate(0f, -32f); rotate(a * 180f / PI.toFloat(), Offset.Zero) }) {
                    drawOval(col, Offset(-8f, -68f), Size(16f, 68f))
                }
            }
        }
    }

    companion object {
        private const val TOWERS = 16
        private val TOWER_COLORS = listOf(Color(0xFF2A2F5E), Color(0xFF24406A), Color(0xFF33295C), Color(0xFF1F3A5A))
        private val BULBS = listOf(Color(0xFFFFD59A), Color(0xFFFF9EC4), Color(0xFF9EF0E0), Color(0xFFC4B0FF), Color(0xFFFFF3B0))
        private val ROOF = Path().apply { moveTo(-56f, -228f); lineTo(0f, -270f); lineTo(56f, -228f); close() }
        private val CAT_EAR = Path().apply { moveTo(13f, -68f); lineTo(10f, -86f); lineTo(2f, -74f); close() }
        private val HANDLE = Path().apply { moveTo(-70f, -90f); lineTo(-60f, -118f); lineTo(60f, -118f); lineTo(70f, -90f) }
        private val POT = Path().apply { moveTo(-20f, 0f); lineTo(20f, 0f); lineTo(25f, -32f); lineTo(-25f, -32f); close() }
    }
}
