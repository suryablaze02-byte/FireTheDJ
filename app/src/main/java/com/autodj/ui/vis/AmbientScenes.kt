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
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A: neon rings racing toward the viewer, each lit by a slice of the spectrum. */
class TunnelScene : Scene {
    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val (c1, c2) = s.palette
        val t = s.time
        drawRect(Color(0xFF020104))
        val c = Offset(w / 2 + sin(t * 0.4f) * w * 0.04f, h / 2 + cos(t * 0.3f) * h * 0.03f)
        val glowR = max(w, h) * 0.5f
        drawCircle(Brush.radialGradient(listOf(c2.copy(alpha = 0.25f + 0.3f * s.bass), Color.Transparent), c, glowR), glowR, c)
        val rings = 22
        val speed = s.beat * 0.5f
        for (i in rings - 1 downTo 0) {
            val z = ((((i - speed) % rings) + rings) % rings) / rings + 0.02f
            val sc = 0.08f / z
            val rw = w * sc
            val rh = h * sc
            if (rw > w * 3) continue
            val band = s.mix[((1 - z) * (s.n - 1)).toInt().coerceIn(0, s.n - 1)]
            val a = min(1f, (1 - z) * (0.25f + band)) * (0.5f + s.energy)
            drawRoundRect(
                (if (i % 2 == 1) c1 else c2).copy(alpha = a.coerceIn(0f, 1f)), Offset(c.x - rw / 2, c.y - rh / 2), Size(rw, rh),
                CornerRadius(min(rw, rh) * 0.18f), style = Stroke(max(1.5f, 4f * sc * (0.6f + band * 2f))), blendMode = BlendMode.Plus,
            )
        }
        val big = max(w, h)
        for (i in 0 until 40) {
            val ang = i / 40f * 2 * PI.toFloat() + t * 0.2f
            val r0 = big * (0.08f + s.frac(t * (0.6f + s.energy) + i * 0.37f) * 0.7f)
            val r1 = r0 * (1.08f + 0.25f * s.kick)
            drawLine(
                Color.White.copy(alpha = 0.08f + 0.2f * s.kick * s.energy),
                c + Offset(cos(ang), sin(ang)) * r0, c + Offset(cos(ang), sin(ang)) * r1, 2f, blendMode = BlendMode.Plus,
            )
        }
    }
}

/** B: soft clouds of colour that swell with the bass, a thin ribbon of the music, film grain. */
class LiquidScene : Scene {
    private val grain: ShaderBrush = run {
        val rnd = Random(3)
        val px = IntArray(160 * 160) { val v = rnd.nextInt(256); (22 shl 24) or (v shl 16) or (v shl 8) or v }
        val bmp = Bitmap.createBitmap(px, 160, 160, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    private val ribbon = Path()

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val (c1, c2) = s.palette
        val t = s.time
        drawRect(Color(0xFF06040A))
        val blobs = listOf(Triple(c1, 0.31f, 0.23f), Triple(c2, 0.17f, 0.41f), Triple(c1, 0.23f, 0.13f), Triple(Color(0xFF5AA8FF), 0.11f, 0.29f), Triple(c2, 0.27f, 0.19f))
        blobs.forEachIndexed { i, (col, fx, fy) ->
            val p = Offset(w * (0.5f + 0.35f * sin(t * fx + i * 2)), h * (0.5f + 0.35f * cos(t * fy + i * 1.3f)))
            val r = min(w, h) * (0.35f + 0.2f * s.bass + 0.06f * sin(t + i))
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * (0.5f + s.energy)), col.copy(alpha = 0.12f), Color.Transparent), p, r), r, p, blendMode = BlendMode.Plus)
        }
        ribbon.reset()
        var x = 0f
        while (x <= w) {
            val u = x / w
            var y = 0f
            for (k in 0 until 6) y += s.mix[k * 7] * sin(u * PI.toFloat() * (2 + k * 3) + t * (1 + k * 0.4f)) / (k + 1)
            val yy = h / 2 + y * h * 0.12f * sin(PI.toFloat() * u)
            if (x == 0f) ribbon.moveTo(x, yy) else ribbon.lineTo(x, yy)
            x += 8f
        }
        drawPath(ribbon, Color.White.copy(alpha = 0.35f + 0.3f * s.energy), style = Stroke(3f))
        drawRect(grain)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)), center, max(w, h) * 0.75f))
    }
}

/** C: the screen as a wall of LED pixels; every kick pushes a ring of light outward. */
class FieldScene : Scene {
    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val (c1, c2) = s.palette
        drawRect(Color(0xFF030206))
        val step = max(18f, min(w, h) / 26f)
        val maxR = hypot(w, h) / 2
        val wave = s.frac(s.beat) * maxR * 1.2f
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) {
                val d = hypot(x - w / 2, y - h / 2)
                val band = s.mix[((d / maxR) * s.n).toInt().coerceIn(0, s.n - 1)]
                val ring = exp(-abs(d - wave) / (step * 2)) * s.kick
                val v = min(1f, band * 0.9f + ring * 0.9f)
                if (v >= 0.05f) {
                    drawCircle(lerp(c1, c2, d / maxR).copy(alpha = v), step * 0.36f * (0.5f + 0.5f * v), Offset(x, y))
                }
                x += step
            }
            y += step
        }
    }
}

/** A cone of light from [from], [aim] radians off straight down. */
internal fun DrawScope.beam(from: Offset, aim: Float, spread: Float, len: Float, color: Color, alpha: Float) {
    val p1 = from + Offset(sin(aim - spread), cos(aim - spread)) * len
    val p2 = from + Offset(sin(aim + spread), cos(aim + spread)) * len
    val path = Path().apply { moveTo(from.x, from.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
    val end = from + Offset(sin(aim), cos(aim)) * len
    drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), from, end), blendMode = BlendMode.Plus)
}
