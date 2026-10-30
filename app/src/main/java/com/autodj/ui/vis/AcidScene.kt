package com.autodj.ui.vis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Acid Trip: a melting kaleidoscope. Four sets of shapes (petals and eyes, spirals, sacred
 * geometry, fractal tendrils) take turns every 8 bars, and straight away on a drop: each zooms
 * in twisting and dissolves as the next arrives. Behind them a rainbow swirl melts like wet
 * paint, a giant star morphs into flowers and blobs, and shapes fly out of a black hole at the
 * centre. The trip deepens with the music: faded and slow in a breakdown, fully melted on a drop.
 * No strobes: the beat moves and scales shapes, and the colour only drifts.
 *
 * Everything is drawn in a 1000-unit square centred on the screen, as coloured triangles
 * ([Mesh]): the melt bends every point each frame, and paths that change every frame would be
 * rasterised on the CPU, far too slowly. Before Android 10 the GPU canvas can't draw triangles,
 * so the scene goes through a half-size bitmap instead.
 */
class AcidScene : Scene {
    private class Shape(val pts: FloatArray, val closed: Boolean, val fill: Boolean, val hue: Int, val width: Float)

    private val sets = listOf(petals(), spirals(), geometry(), tendrils())

    /** How deep the trip is (0 = breakdown, 1 = drop), smoothed. */
    private var trip = 0.3f
    private var set = 0
    private var previous = 0
    private var changedAt = -10f
    private var lastBlock = -1
    private var lastDropAt = -100f
    private var wasLow = true

    private val mesh = Mesh()
    private val plain = Paint()
    private val add = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaded = Paint()
    /** Scratch for one slice's melted points, then mirrored round the wheel. */
    private var slice = FloatArray(4096)
    private var bitmap: Bitmap? = null

    override fun step(s: VisualState) {
        val surge = (s.bass - s.slowBass).coerceAtLeast(0f)
        val target = (s.energy * 1.5f + surge * 2.5f).coerceIn(0f, 1f)
        trip += (target - trip) * min(1f, s.dt * if (target > trip) 3f else 0.6f)
        // A new set every 8 bars; on a drop (the bass back well above its recent level after a dip), at once.
        val block = floor(s.beat / 32f).toInt()
        val drop = wasLow && s.bass > 0.35f && s.bass > s.slowBass * 1.6f && s.time - lastDropAt > 8f
        if (s.bass < s.slowBass * 0.7f) wasLow = true
        if (drop) { wasLow = false; lastDropAt = s.time }
        if (!s.paused && (drop || (lastBlock >= 0 && block != lastBlock))) {
            previous = set
            set = (set + 1) % sets.size
            changedAt = s.time
        }
        lastBlock = block
    }

    override fun draw(ds: DrawScope, s: VisualState) {
        val w = ds.size.width
        val h = ds.size.height
        if (Build.VERSION.SDK_INT >= 29) {
            scene(ds.drawContext.canvas.nativeCanvas, w, h, s)
            return
        }
        val bw = (w / 2).roundToInt().coerceAtLeast(1)
        val bh = (h / 2).roundToInt().coerceAtLeast(1)
        val bmp = bitmap?.takeIf { it.width == bw && it.height == bh } ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bitmap = it }
        scene(Canvas(bmp), bw.toFloat(), bh.toFloat(), s)
        ds.drawContext.canvas.nativeCanvas.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun scene(cv: Canvas, w: Float, h: Float, s: VisualState) {
        val t = s.time
        val melt = 0.35f + 1.9f * trip
        val drift = (t * 26f) % 360f
        val sat = 0.5f + 0.5f * trip
        val bright = 0.7f + 0.3f * trip
        val hsv = FloatArray(3)
        fun neon(i: Int, alpha: Float, shift: Float = 0f): Int {
            hsv[0] = ((HUES[i % HUES.size] + drift + shift) % 360f + 360f) % 360f
            hsv[1] = 0.92f * sat
            hsv[2] = bright
            return android.graphics.Color.HSVToColor((alpha.coerceIn(0f, 1f) * 255).roundToInt(), hsv)
        }

        cv.drawColor(0xFF12041F.toInt())
        cv.save()
        // The whole scene breathes and turns a little every two bars.
        val breathe = 0.5f - 0.5f * cos(s.beat * PI.toFloat() / 4f)
        val u = max(w, h) / 1000f * (1f + 0.12f * breathe * (0.4f + trip))
        cv.translate(w / 2, h / 2)
        cv.scale(u, u)
        cv.rotate(8f * breathe)

        swirl(t, melt, ::neon)
        mesh.flush(cv, plain)
        morph(t, melt, neon(3, 0.45f))
        tunnel(s, melt, ::neon)
        mesh.flush(cv, plain)

        // The kaleidoscope: pulses on the kick, turns slowly against the swirl.
        cv.save()
        val pulse = 1f + 0.12f * s.kick
        cv.rotate(-t * 12f)
        cv.scale(pulse, pulse)
        val p = ((t - changedAt) / TRANSITION_S).coerceIn(0f, 1f)
        // The old set grows and dissolves while the new one zooms in, twisting.
        if (p < 1f) kaleido(cv, sets[previous], 1f + 1.6f * p, 70f * p, 1f - p, t, melt, ::neon)
        kaleido(cv, sets[set], 0.3f + 0.7f * ease(p), -90f * (1f - ease(p)), ease(p), t, melt, ::neon)
        cv.restore()

        // The vanishing point: a black hole with a spiral of coloured dots spinning into it.
        shaded.shader = RadialGradient(0f, 0f, 95f, intArrayOf(0xFF000000.toInt(), 0xFF05020A.toInt(), 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(0f, 0f, 95f, shaded)
        val spin = -t * 2.1f
        for (i in 0 until 40) {
            val r = 8f + i * 3.2f
            val a = i * 0.55f + spin
            fillPaint.color = neon(i, 1f)
            cv.drawCircle(r * sin(a), -r * cos(a), 1.5f + i * 0.18f, fillPaint)
        }
        cv.restore()
        shaded.shader = RadialGradient(w / 2, h / 2, max(w, h) * 0.7f, intArrayOf(0, 0xCC05020A.toInt()), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, shaded)
    }

    /** The rainbow wheel of twisted wedges, its edges wobbling like wet paint. */
    private fun swirl(t: Float, melt: Float, neon: (Int, Float, Float) -> Int) {
        val n = 36
        val steps = 12
        val spin = t * 0.26f
        fun x(a: Float, k: Int) = edgeR(k) * cos(twist(a, k, t, melt))
        fun y(a: Float, k: Int) = edgeR(k) * sin(twist(a, k, t, melt))
        for (i in 0 until n) {
            val a0 = i / n.toFloat() * 2 * PI.toFloat() + spin
            val a1 = (i + 1) / n.toFloat() * 2 * PI.toFloat() + spin
            val c = neon(i, 0.7f, 0f)
            val base = mesh.vertex(0f, 0f, c)
            for (k in 1..steps) {
                mesh.vertex(x(a0, k), y(a0, k), c)
                mesh.vertex(x(a1, k), y(a1, k), c)
            }
            mesh.tri(base, base + 1, base + 2)
            for (k in 1 until steps) {
                val q = base + 1 + (k - 1) * 2
                mesh.tri(q, q + 2, q + 1)
                mesh.tri(q + 1, q + 2, q + 3)
            }
        }
    }

    private fun edgeR(k: Int) = k * 70f
    private fun twist(a: Float, k: Int, t: Float, melt: Float): Float {
        val r = edgeR(k)
        return a + r * 0.0018f + melt * 0.18f * sin(r * 0.011f + t * 1.3f + a * 3f)
    }

    /** A huge star behind everything, morphing into a flower, a blob and back. */
    private fun morph(t: Float, melt: Float, color: Int) {
        val phase = (t / 9f) % 1f * 4f
        val stage = floor(phase).toInt()
        val f = ease(phase - stage)
        val n = 48
        val pts = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = i / n.toFloat() * 2 * PI.toFloat() + t * 0.05f
            val r = lerp(morphR(stage, a, i), morphR(stage + 1, a, i), f) + melt * 18f * sin(a * 7f + t * 2f)
            pts[i * 2] = r * sin(a)
            pts[i * 2 + 1] = -r * cos(a)
        }
        mesh.ribbon(pts, 0, pts.size, true, 22f, color)
    }

    private fun morphR(stage: Int, a: Float, i: Int) = when (stage % 4) {
        0 -> if (i % 2 == 0) 760f else 420f
        1 -> 620f * (1 + 0.25f * sin(a * 5))
        2 -> if (i % 2 == 0) 700f else 560f
        else -> 600f * (1 + 0.35f * sin(a * 3))
    }

    /** Hexagons, stars and squares flying out of the centre, faster as the trip deepens. */
    private fun tunnel(s: VisualState, melt: Float, neon: (Int, Float, Float) -> Int) {
        val count = (4 + 12 * trip).toInt()
        val speed = s.beat / 8f * (0.6f + trip)
        for (i in 0 until count) {
            val z = s.frac(speed + i / count.toFloat())
            val sc = 0.03f + 3.4f * z * z
            val alpha = min(z / 0.15f, 1f) * min((1f - z) / 0.2f, 1f) * 0.9f
            if (alpha <= 0.01f) continue
            val (points, inner) = TUNNEL[i % TUNNEL.size]
            val pts = FloatArray(points * 4)
            for (k in 0 until points * 2) {
                val a = k / (points * 2f) * 2 * PI.toFloat() + z * 2.1f
                val r = (if (k % 2 == 0) 300f else 300f * inner) * sc + melt * 6f * sin(a * 5 + s.time * 3)
                pts[k * 2] = r * sin(a)
                pts[k * 2 + 1] = -r * cos(a)
            }
            mesh.ribbon(pts, 0, pts.size, true, (if (i % 2 == 1) 14f else 8f) * (0.5f + sc), neon(i, alpha, 0f))
        }
    }

    /** Twelve mirrored copies of [shapes], with red and cyan colour echoes on a deep trip. */
    private fun kaleido(cv: Canvas, shapes: List<Shape>, scale: Float, turn: Float, alpha: Float, t: Float, melt: Float, neon: (Int, Float, Float) -> Int) {
        if (alpha <= 0.01f) return
        cv.save()
        cv.rotate(turn)
        cv.scale(scale, scale)
        if (trip > 0.45f) {
            val echo = (trip - 0.45f) * 1.1f * alpha
            cv.save(); cv.translate(-10f, 4f)
            wheel(shapes, t, melt, echo, -70f, false, neon); mesh.flush(cv, add)
            cv.restore(); cv.save(); cv.translate(10f, -4f)
            wheel(shapes, t, melt, echo, 90f, false, neon); mesh.flush(cv, add)
            cv.restore()
        }
        // A dark edge first lifts the shapes off the melting paint.
        wheel(shapes, t, melt, alpha, 0f, true, neon)
        mesh.flush(cv, plain)
        wheel(shapes, t, melt, alpha, 0f, false, neon)
        mesh.flush(cv, plain)
        cv.restore()
    }

    /** Adds every shape, melted, twelve times round the wheel (odd slices mirrored); [halo] adds their dark edges instead. */
    private fun wheel(shapes: List<Shape>, t: Float, melt: Float, alpha: Float, shift: Float, halo: Boolean, neon: (Int, Float, Float) -> Int) {
        val edge = android.graphics.Color.argb((0.8f * alpha * 255).roundToInt(), 5, 2, 10)
        for (sh in shapes) {
            val n = sh.pts.size
            if (slice.size < n * 2) slice = FloatArray(n * 2)
            meltInto(sh.pts, t, melt)
            val color = if (halo) edge else neon(sh.hue, alpha, shift)
            for (j in 0 until 12) {
                val a = j * PI.toFloat() / 6f
                val ca = cos(a)
                val sa = sin(a)
                val m = if (j % 2 == 1) -1f else 1f
                var i = 0
                while (i < n) {
                    val x = slice[i] * m
                    val y = slice[i + 1]
                    slice[n + i] = x * ca - y * sa
                    slice[n + i + 1] = x * sa + y * ca
                    i += 2
                }
                if (sh.fill) mesh.fan(slice, n, n, color, if (halo) 6f else 0f)
                else mesh.ribbon(slice, n, n, sh.closed, sh.width + if (halo) 10f else 0f, color)
            }
        }
    }

    /** Puts [pts], each pushed by a slow wave field (the melt), at the start of [slice]. */
    private fun meltInto(pts: FloatArray, t: Float, melt: Float) {
        var i = 0
        while (i < pts.size) {
            val x = pts[i]
            val y = pts[i + 1]
            slice[i] = x + melt * 9f * (sin(y * 0.021f + t * 1.7f) + 0.6f * sin(x * 0.017f - t * 1.1f + y * 0.006f))
            slice[i + 1] = y + melt * 9f * (sin(x * 0.019f + t * 1.3f) + 0.6f * sin(y * 0.013f + t * 0.8f))
            i += 2
        }
    }

    /** Coloured triangles gathered up and drawn in one call. */
    private class Mesh {
        private var verts = FloatArray(16384)
        private var colors = IntArray(16384)
        private var indices = ShortArray(32768)
        private var nv = 0
        private var ni = 0

        fun vertex(x: Float, y: Float, color: Int): Int {
            if (nv * 2 + 2 > verts.size) {
                verts = verts.copyOf(verts.size * 2)
                colors = colors.copyOf(colors.size * 2)
            }
            verts[nv * 2] = x
            verts[nv * 2 + 1] = y
            // drawVertices reads the colour array as long as the coordinate array.
            colors[nv] = color
            return nv++
        }

        fun tri(a: Int, b: Int, c: Int) {
            if (ni + 3 > indices.size) indices = indices.copyOf(indices.size * 2)
            indices[ni++] = a.toShort()
            indices[ni++] = b.toShort()
            indices[ni++] = c.toShort()
        }

        /** A filled shape that's star-shaped round its centre, as a fan; [grow] pushes the rim outward (for a dark edge). */
        fun fan(pts: FloatArray, off: Int, n: Int, color: Int, grow: Float) {
            var cx = 0f
            var cy = 0f
            val count = n / 2
            for (i in 0 until count) { cx += pts[off + i * 2]; cy += pts[off + i * 2 + 1] }
            cx /= count; cy /= count
            val c = vertex(cx, cy, color)
            for (i in 0 until count) {
                var x = pts[off + i * 2]
                var y = pts[off + i * 2 + 1]
                if (grow > 0f) {
                    val d = hypot(x - cx, y - cy).coerceAtLeast(0.001f)
                    x += (x - cx) / d * grow
                    y += (y - cy) / d * grow
                }
                vertex(x, y, color)
            }
            for (i in 0 until count) tri(c, c + 1 + i, c + 1 + (i + 1) % count)
        }

        /** A line [width] wide along [pts] (closed back to the start if [closed]), as a strip of quads. */
        fun ribbon(pts: FloatArray, off: Int, n: Int, closed: Boolean, width: Float, color: Int) {
            val count = n / 2
            if (count < 2) return
            val half = width / 2
            val base = nv
            for (i in 0 until count) {
                val prev = if (i > 0) i - 1 else if (closed) count - 1 else 0
                val next = if (i < count - 1) i + 1 else if (closed) 0 else count - 1
                var dx = pts[off + next * 2] - pts[off + prev * 2]
                var dy = pts[off + next * 2 + 1] - pts[off + prev * 2 + 1]
                val d = hypot(dx, dy).coerceAtLeast(0.001f)
                dx /= d; dy /= d
                val x = pts[off + i * 2]
                val y = pts[off + i * 2 + 1]
                vertex(x - dy * half, y + dx * half, color)
                vertex(x + dy * half, y - dx * half, color)
            }
            val segs = if (closed) count else count - 1
            for (i in 0 until segs) {
                val a = base + i * 2
                val b = base + ((i + 1) % count) * 2
                tri(a, a + 1, b)
                tri(a + 1, b + 1, b)
            }
        }

        fun flush(cv: Canvas, paint: Paint) {
            if (ni > 0) cv.drawVertices(Canvas.VertexMode.TRIANGLES, nv * 2, verts, 0, null, 0, colors, 0, indices, 0, ni, paint)
            nv = 0
            ni = 0
        }
    }

    private companion object {
        /** Neon magenta, acid green, cyan, yellow, purple, orange: drifting round the wheel together. */
        val HUES = floatArrayOf(312f, 110f, 187f, 54f, 268f, 25f)
        const val TRANSITION_S = 1.4f
        /** Tunnel shapes: points and the inner radius of each (1 = a plain polygon). */
        val TUNNEL = listOf(3 to 1f, 5 to 0.6f, 2 to 1f, 8 to 0.8f)

        fun ease(x: Float) = x * x * (3 - 2 * x)
        fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 24) = FloatArray(n * 2) { k ->
            val a = (k / 2) / n.toFloat() * 2 * PI.toFloat()
            if (k % 2 == 0) cx + rx * cos(a) else cy + ry * sin(a)
        }

        fun star(cx: Float, cy: Float, points: Int, inner: Float, outer: Float, phase: Float = 0f) = FloatArray(points * 4) { k ->
            val i = k / 2
            val a = i / (points * 2f) * 2 * PI.toFloat() + phase
            val r = if (i % 2 == 0) outer else inner
            if (k % 2 == 0) cx + r * sin(a) else cy - r * cos(a)
        }

        /** A quadratic curve as points. */
        fun curve(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, n: Int = 12) = FloatArray((n + 1) * 2) { k ->
            val f = (k / 2) / n.toFloat()
            val g = 1 - f
            if (k % 2 == 0) g * g * x0 + 2 * g * f * cx + f * f * x1 else g * g * y0 + 2 * g * f * cy + f * f * y1
        }

        fun petals(): List<Shape> {
            // The eye's outline without its repeated corner points, so it fans cleanly from its middle.
            val top = curve(-34f, -300f, 0f, -345f, 34f, -300f)
            val bottom = curve(34f, -300f, 0f, -255f, -34f, -300f)
            val eye = top + bottom.copyOfRange(2, bottom.size - 2)
            return listOf(
                Shape(ellipse(0f, -150f, 26f, 88f), true, true, 0, 0f),
                Shape(ellipse(0f, -150f, 12f, 60f), true, true, 3, 0f),
                Shape(eye, true, true, 1, 0f),
                Shape(ellipse(0f, -300f, 13f, 13f, 12), true, true, 2, 0f),
                Shape(ellipse(38f, -215f, 9f, 9f, 10), true, true, 1, 0f),
                Shape(ellipse(58f, -400f, 16f, 16f, 12), true, true, 4, 0f),
                Shape(curve(20f, -440f, 70f, -490f, 10f, -520f), false, false, 5, 12f),
            )
        }

        fun spirals() = (0 until 5).map { i ->
            val a = 30f + i * 40f
            val start = i.toFloat()
            val steps = 88
            val pts = FloatArray((steps + 1) * 2) { k ->
                val th = start + (k / 2) / 40f * 2 * PI.toFloat()
                val r = a + 22f * (th - start)
                if (k % 2 == 0) r * sin(th) * 0.45f else -r * cos(th) - 120f - i * 60f
            }
            Shape(pts, false, false, i, 16f - 2 * i)
        }

        fun geometry() = listOf(
            Shape(star(0f, -140f, 3, 40f, 80f), true, false, 2, 11f),
            Shape(star(0f, -140f, 3, 40f, 80f, PI.toFloat()), true, false, 0, 11f),
            Shape(star(0f, -270f, 6, 30f, 30f), true, true, 4, 0f),
            Shape(star(0f, -380f, 2, 18f, 48f), true, true, 1, 0f),
            Shape(star(40f, -470f, 4, 22f, 60f), true, false, 5, 6f),
        )

        fun tendrils(): List<Shape> {
            val out = mutableListOf<Shape>()
            fun grow(x: Float, y: Float, ang: Float, len: Float, depth: Int) {
                if (depth == 0 || len < 8f) {
                    out += Shape(ellipse(x, y, 4f + depth, 4f + depth, 8), true, true, 3, 0f)
                    return
                }
                val x2 = x + len * sin(ang)
                val y2 = y - len * cos(ang)
                out += Shape(curve(x, y, (x + x2) / 2 + 14 * cos(ang), (y + y2) / 2 + 14 * sin(ang), x2, y2, 8), false, false, depth, depth * 3.2f)
                grow(x2, y2, ang + 0.45f, len * 0.7f, depth - 1)
                grow(x2, y2, ang - 0.38f, len * 0.66f, depth - 1)
            }
            grow(0f, -60f, 0f, 140f, 6)
            return out
        }
    }
}
