package com.autodj.ui.vis

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chill Drive: a red convertible, top down, cruising a synthwave highway at night, seen from
 * behind. Fireworks burst on every beat (more on the downbeat, a volley on a drop), the sun
 * swells with the kick, and the tail lights are two little spectrum meters.
 */
class DriveScene : Scene {
    private var dist = 0f
    private var lastHalf = Int.MIN_VALUE
    private var flash = 0f
    private var drop = 0f
    private var dropCool = 0f

    /** Rockets climb for half a beat so they burst on the next one. Positions: x as a fraction of the width, y of the height. */
    private class Rocket(val u0: Float, val u1: Float, val y1: Float, val time: Float, val big: Boolean, val col: Int, val col2: Int) {
        var age = 0f
    }

    private val rockets = ArrayList<Rocket>()

    // Sparks: burst origin (u of the width, y of the height), then offset and velocity in heights.
    private val su = FloatArray(MAX_SPARKS)
    private val sy = FloatArray(MAX_SPARKS)
    private val sdx = FloatArray(MAX_SPARKS)
    private val sdy = FloatArray(MAX_SPARKS)
    private val svx = FloatArray(MAX_SPARKS)
    private val svy = FloatArray(MAX_SPARKS)
    private val slife = FloatArray(MAX_SPARKS)
    private val sdec = FloatArray(MAX_SPARKS)
    private val scol = IntArray(MAX_SPARKS)
    private val sglit = BooleanArray(MAX_SPARKS)
    private var sparks = 0

    /** The white-hot flash at the heart of each burst. */
    private val cores = ArrayList<FloatArray>() // u, y, radius (heights), life, colour

    private val stars = Random(11).let { r -> Array(60) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.3f, 1f + r.nextFloat() * 1.6f, r.nextFloat() * 6f) } }

    // Scratch for drawing; rebuilt every draw.
    private val px = FloatArray(N + 1)
    private val py = FloatArray(N + 1)
    private val ps = FloatArray(N + 1)
    private val pz = FloatArray(N + 1)
    private val road = Path()
    private val edges = Path()
    private val dashes = Path()
    private val grid = Path()
    private val ridge = Path()
    private val palm = Path()

    override fun step(s: VisualState) {
        val dt = s.dt
        if (!s.paused) dist += dt * SPEED * (0.85f + 0.3f * s.energy)
        val beatSec = s.songBeatSec.toFloat().coerceIn(0.25f, 1.5f)
        // Half a beat early, launch: they burst on the beat. Two a beat, five ahead of the downbeat.
        val half = floor(s.beat * 2f).toInt()
        if (!s.paused && half != lastHalf) {
            if (half % 2 == 1 && lastHalf != Int.MIN_VALUE) {
                val down = ((half + 1) / 2).mod(4) == 0
                repeat(if (down) 5 else 2) { q -> launch(down && q < 3, 0.5f * beatSec * (1 + q * 0.06f)) }
            }
            lastHalf = half
        }
        // A drop: the bass comes back well above where it's been. Light the sky up.
        dropCool -= dt
        if (!s.paused && dropCool <= 0f && s.bass > 0.45f && s.bass - s.slowBass > 0.2f) {
            dropCool = 12f
            drop = 1f
            repeat(10) { k -> launch(true, 0.2f + k * 0.06f) }
        }
        drop *= 0.25f.pow(dt)
        flash *= 0.05f.pow(dt)

        val it = rockets.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.age += dt
            if (r.age >= r.time) {
                burst(r)
                it.remove()
            }
        }
        val drag = 0.35f.pow(dt)
        var i = 0
        while (i < sparks) {
            svx[i] *= drag
            svy[i] = svy[i] * drag + 0.09f * dt
            sdx[i] += svx[i] * dt
            sdy[i] += svy[i] * dt
            slife[i] -= sdec[i] * dt
            if (slife[i] <= 0f) {
                sparks--
                copySpark(sparks, i)
            } else {
                i++
            }
        }
        cores.removeAll { c -> c[3] -= dt * 3f; c[3] <= 0f }
    }

    private fun launch(big: Boolean, time: Float) {
        val u = 0.08f + Random.nextFloat() * 0.84f
        rockets += Rocket(u, u + (Random.nextFloat() - 0.5f) * 0.08f, 0.05f + Random.nextFloat() * 0.26f, time, big, Random.nextInt(FW.size), Random.nextInt(FW.size))
    }

    private fun burst(r: Rocket) {
        val n = if (r.big) 120 else 72
        val sp = if (r.big) 0.46f else 0.34f
        flash = min(1f, flash + if (r.big) 0.6f else 0.35f)
        cores += floatArrayOf(r.u1, r.y1, if (r.big) 0.09f else 0.06f, 1f, r.col.toFloat())
        for (k in 0 until n) {
            val a = k / n.toFloat() * 2 * PI.toFloat() + Random.nextFloat() * 0.2f
            val v = sp * (0.55f + Random.nextFloat() * 0.45f)
            addSpark(r.u1, r.y1, cos(a) * v, sin(a) * v, 0.35f + Random.nextFloat() * 0.3f, if (k % 3 == 0) r.col2 else r.col, Random.nextFloat() < 0.3f)
        }
        val inner = n / 3
        for (k in 0 until inner) {
            val a = k / inner.toFloat() * 2 * PI.toFloat()
            addSpark(r.u1, r.y1, cos(a) * sp * 0.45f, sin(a) * sp * 0.45f, 0.6f, r.col2, true)
        }
    }

    private fun addSpark(u: Float, y: Float, vx: Float, vy: Float, dec: Float, col: Int, glitter: Boolean) {
        if (sparks >= MAX_SPARKS) return
        val i = sparks++
        su[i] = u; sy[i] = y; sdx[i] = 0f; sdy[i] = 0f; svx[i] = vx; svy[i] = vy
        slife[i] = 1f; sdec[i] = dec; scol[i] = col; sglit[i] = glitter
    }

    private fun copySpark(from: Int, to: Int) {
        su[to] = su[from]; sy[to] = sy[from]; sdx[to] = sdx[from]; sdy[to] = sdy[from]; svx[to] = svx[from]; svy[to] = svy[from]
        slife[to] = slife[from]; sdec[to] = sdec[from]; scol[to] = scol[from]; sglit[to] = sglit[from]
    }

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val t = s.time
        val hy = h * HORIZON
        val base = floor(dist / SEG).toInt()
        val fr = dist - base * SEG
        val bend = curve(base)

        // Sky: violet to hot pink at the horizon, brighter while fireworks burst.
        val lift = flash * 0.9f + drop * 0.4f
        val sky = Brush.verticalGradient(
            0f to lit(Color(0xFF08021A), lift * 0.5f), 0.45f to lit(Color(0xFF220640), lift),
            0.8f to lit(Color(0xFF5C126E), lift), 1f to lit(Color(0xFFFF3C8C), lift), startY = 0f, endY = hy,
        )
        drawRect(sky, size = Size(w, hy + 1))
        for (st in stars) {
            val a = (0.45f + 0.4f * sin(t * 1.4f + st[3])).coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = a), Offset(st[0] * w, st[1] * h), Size(st[2], st[2]))
        }

        // The striped sun on the horizon, swelling a little with the kick.
        val sr = h * 0.17f * (1 + s.kick * 0.04f)
        val sun = Offset(w / 2 - bend * w * 0.05f, hy - h * 0.04f)
        clipRect(0f, 0f, w, hy) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3D8A).copy(alpha = 0.45f + 0.3f * s.kick), Color.Transparent), sun, sr * 1.8f), sr * 1.8f, sun)
            drawCircle(Brush.verticalGradient(0f to Color(0xFFFFE14F), 0.55f to Color(0xFFFF7A3D), 1f to Color(0xFFFF2D95), startY = sun.y - sr, endY = sun.y + sr), sr, sun)
            val scroll = (t * 12f) % (sr * 0.14f)
            for (k in 0 until 7) {
                val yy = sun.y + sr * (0.05f + k * 0.14f) + scroll
                drawRect(sky, Offset(sun.x - sr, yy), Size(sr * 2, sr * (0.025f + k * 0.012f)))
            }
        }

        drawFireworks(w, h, t)

        // Two ranges of mountains with neon edges.
        for (layer in 0..1) {
            ridge.reset()
            ridge.moveTo(0f, hy)
            val far = layer == 0
            for (k in 0..40) {
                val x = k / 40f * w
                val u = (x + bend * w * (if (far) 0.4f else 0.8f)) / w
                val hgt = h * (if (far) 0.06f else 0.035f) * (0.5f + 0.5f * sin(u * (if (far) 5f else 9f) + layer * 2) * sin(u * (if (far) 2.1f else 3.3f) + 1))
                ridge.lineTo(x, hy - hgt)
            }
            ridge.lineTo(w, hy)
            ridge.close()
            drawPath(ridge, if (far) Color(0xFF1C0A3A) else Color(0xFF12062A))
            val edge = if (far) Color(0xFF7A3DFF) else Color(0xFFFF4FD8)
            drawPath(ridge, edge.copy(alpha = 0.3f), style = Stroke(6f))
            drawPath(ridge, edge, style = Stroke(2f))
        }

        // The highway, projected segment by segment toward the horizon.
        drawRect(Color(0xFF0A0418), Offset(0f, hy), Size(w, h - hy))
        val k = max(w, h) * 0.5f * D
        var x = 0f
        var dx = -bend * fr / SEG
        for (n in 0..N) {
            val z = n * SEG - fr + 130f
            val p = k / z
            px[n] = w / 2 + (x - LANE) * p
            py[n] = hy + CAM_H * p
            ps[n] = p
            pz[n] = z
            dx += curve(base + n)
            x += dx
        }
        // The neon grid: lines across every third segment, lines along that follow the road.
        for (n in N downTo 2) {
            if ((base + n) % 3 != 0) continue
            drawLine(Color(0xFFFF4FD8).copy(alpha = 0.15f + 0.65f * (1 - n / N.toFloat())), Offset(0f, py[n]), Offset(w, py[n]), 1.5f)
        }
        grid.reset()
        for (g in -16..16) {
            val gx = g * 1400f
            for (n in 1..N) {
                val gxp = px[n] + gx * ps[n]
                if (n == 1) grid.moveTo(gxp, py[n]) else grid.lineTo(gxp, py[n])
            }
        }
        drawPath(grid, Color(0xFFFF4FD8).copy(alpha = 0.55f), style = Stroke(1.5f))

        strip(road, 0f, R)
        drawPath(road, Color(0xFF150A28))
        edges.reset()
        strip(edges, -R, 120f, reset = false)
        strip(edges, R, 120f, reset = false)
        drawPath(edges, Color(0xFF3FF2FF).copy(alpha = 0.25f))
        edges.reset()
        strip(edges, -R, 40f, reset = false)
        strip(edges, R, 40f, reset = false)
        drawPath(edges, Color(0xFF3FF2FF))
        dashes.reset()
        for (n in N downTo 1) {
            if (((base + n) shr 2) % 2 == 0) continue
            for (lx in LANES) quad(dashes, n - 1, n, lx, 22f)
        }
        drawPath(dashes, Color(0xFFFF4FD8).copy(alpha = 0.85f))

        // A few palms well back from the road.
        for (n in N downTo 2) {
            val i = base + n
            if (i % 9 == 0) drawPalm(n, R * (1.7f + hash(i) * 0.8f), i, t)
            if (i % 9 == 4) drawPalm(n, -R * (1.7f + hash(i) * 0.8f), i + 3, t)
        }

        val cs = min(w / 1300f, h / 620f)
        val tilt = (-bend * 0.02f).coerceIn(-0.05f, 0.05f) * 180f / PI.toFloat()
        val bob = sin(t * 11f) * 0.6f + s.kick * 0.6f
        withTransform({
            translate(w / 2, h * 0.975f + bob * cs)
            rotate(tilt, Offset.Zero)
            scale(cs, cs, Offset.Zero)
        }) { drawCar(s, t) }

        if (drop > 0.01f) drawRect(Color(0xFFFF4FA3).copy(alpha = drop * 0.12f))
    }

    private fun DrawScope.drawFireworks(w: Float, h: Float, t: Float) {
        for (r in rockets) {
            val kk = min(1f, r.age / r.time)
            val e = 1 - (1 - kk) * (1 - kk)
            val rx = (r.u0 + (r.u1 - r.u0) * e) * w
            val ry = (HORIZON + (r.y1 - HORIZON) * e) * h
            drawLine(Color(0xCCFFE6C8), Offset(rx, ry), Offset(rx, ry + h * 0.04f), 2f, StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        for (c in cores) {
            val o = Offset(c[0] * w, c[1] * h)
            val rad = c[2] * h * 1.4f
            val life = c[3]
            drawCircle(
                Brush.radialGradient(0f to Color.White.copy(alpha = life), 0.3f to FW[c[4].toInt()].copy(alpha = life), 1f to Color.Transparent, center = o, radius = rad),
                rad, o, blendMode = BlendMode.Plus,
            )
        }
        val thick = h / 560f
        for (i in 0 until sparks) {
            val life = slife[i]
            val twinkle = if (sglit[i] && life < 0.6f) (if (sin(t * 40f + su[i] * 97f + i) > 0f) 1f else 0.2f) else 1f
            val col = if (life > 0.85f) Color.White else FW[scol[i]]
            val x = su[i] * w + sdx[i] * h
            val y = (sy[i] + sdy[i]) * h
            drawLine(
                col.copy(alpha = (life * twinkle).coerceIn(0f, 1f)), Offset(x, y), Offset(x - svx[i] * h * 0.05f, y - svy[i] * h * 0.05f),
                (if (life > 0.5f) 3f else 1.8f) * thick, StrokeCap.Round, blendMode = BlendMode.Plus,
            )
        }
    }

    /** A strip of road [half] wide either side of [off] (world units from the centre line), near to far and back. */
    private fun strip(path: Path, off: Float, half: Float, reset: Boolean = true) {
        if (reset) path.reset()
        for (n in 1..N) {
            val xx = px[n] + (off - half) * ps[n]
            if (n == 1) path.moveTo(xx, py[n]) else path.lineTo(xx, py[n])
        }
        for (n in N downTo 1) path.lineTo(px[n] + (off + half) * ps[n], py[n])
        path.close()
    }

    private fun quad(path: Path, a: Int, b: Int, off: Float, half: Float) {
        path.moveTo(px[a] + (off - half) * ps[a], py[a])
        path.lineTo(px[b] + (off - half) * ps[b], py[b])
        path.lineTo(px[b] + (off + half) * ps[b], py[b])
        path.lineTo(px[a] + (off + half) * ps[a], py[a])
        path.close()
    }

    private fun DrawScope.drawPalm(n: Int, wx: Float, i: Int, t: Float) {
        val p = ps[n]
        val x = px[n] + wx * p
        val y = py[n]
        if (x < -200 || x > size.width + 200) return
        val hgt = (2800 + hash(i) * 900) * p
        val lean = (hash(i + 5) - 0.5f) * 600 * p
        val tx = x + lean + sin(t * 0.9f + i) * 60 * p
        val ty = y - hgt
        palm.reset()
        palm.moveTo(x, y)
        palm.quadraticTo(x + lean * 0.2f, y - hgt * 0.6f, tx, ty)
        drawPath(palm, Color(0xFF16061F), style = Stroke(max(1f, 95 * p), cap = StrokeCap.Round))
        palm.reset()
        for (f in 0 until 8) {
            val a = f / 8f * 2 * PI.toFloat() + 0.3f
            val len = (1100 + hash(i + f) * 300) * p
            palm.moveTo(tx, ty)
            palm.quadraticTo(tx + cos(a) * len * 0.55f, ty - len * 0.3f, tx + cos(a) * len, ty + abs(sin(a)) * len * 0.25f + len * 0.35f + sin(t * 1.3f + f) * 40 * p)
        }
        drawPath(palm, Color(0xFF1A0826), style = Stroke(max(1f, 55 * p), cap = StrokeCap.Round))
    }

    /** The car, from behind, in its own units: about 540 wide, the road at y = 0. */
    private fun DrawScope.drawCar(s: VisualState, t: Float) {
        drawOval(Color.Black.copy(alpha = 0.6f), Offset(-270f, -28f), Size(540f, 52f))
        for (wx in floatArrayOf(-238f, 150f)) {
            drawRoundRect(Color(0xFF08080A), Offset(wx, -78f), Size(88f, 78f), CornerRadius(16f))
            for (k in 0 until 4) drawRect(Color(0xFF1C1C20), Offset(wx + 8, -70f + k * 16), Size(72f, 3f))
        }
        drawPath(CAR_BODY, Brush.verticalGradient(0f to Color(0xFFFF3B3B), 0.4f to Color(0xFFE00012), 1f to Color(0xFF4A0008), startY = -190f, endY = -40f))
        drawPath(CAR_HAUNCHES, Color.Black.copy(alpha = 0.25f))
        drawPath(CAR_DECK, Brush.verticalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFE3101E)), startY = -206f, endY = -182f))
        drawPath(CAR_LIP, Color(0xFFC00010))
        for ((sx, sy2, sh) in STRIPES) drawRect(Color(0xFFF4F1EA), Offset(sx, sy2), Size(30f, sh))
        drawPath(CAR_CABIN, Color(0xFF120810))
        drawPath(CAR_SCREEN, Color(0x22AAD7FF))
        drawPath(CAR_SCREEN, Color(0xFFDFE3EA), style = Stroke(6f))
        drawRect(Color(0xFF2A2A30), Offset(-14f, -300f), Size(28f, 10f))
        for (hx in floatArrayOf(-86f, 86f)) drawRoundRect(Color(0xFF2A1A1E), Offset(hx - 34, -258f), Size(68f, 50f), CornerRadius(14f))
        drawCircle(Color(0xFF17100C), 31f, Offset(-86f, -274f))
        drawCircle(Color(0xFF2B1A14), 28f, Offset(86f, -276f))
        for (k in 0 until 6) {
            val wv = sin(t * 6 + k) * 10
            drawLine(Color(0xFF2B1A14), Offset(74f + k * 5, -262f), Offset(116f + k * 7 + wv * 1.6f, -218f + k * 3), 7f, StrokeCap.Round)
        }
        drawRoundRect(Color(0xFF0C0A0E), Offset(-214f, -162f), Size(428f, 52f), CornerRadius(8f))
        tailMeter(s, -206f, 1)
        tailMeter(s, 206f, -1)
        drawCircle(Color(0xFFCFD3DC), 15f, Offset(0f, -136f))
        drawCircle(Color(0xFF0C0A0E), 9f, Offset(0f, -136f))
        drawRoundRect(
            Brush.verticalGradient(0f to Color.White, 0.5f to Color(0xFFA9ADB6), 1f to Color(0xFFE8EAEE), startY = -104f, endY = -80f),
            Offset(-250f, -104f), Size(500f, 24f), CornerRadius(12f),
        )
        drawRoundRect(Color(0xFFF3EFE4), Offset(-52f, -76f), Size(104f, 30f), CornerRadius(4f))
        for (k in 0 until 5) drawRect(Color(0xFF1D3A7A), Offset(-34f + k * 14, -68f), Size(9f, 14f))
        drawRect(Color(0xFF0D0B10), Offset(-200f, -46f), Size(400f, 8f))
        for (ex in floatArrayOf(-135f, -105f, 105f, 135f)) {
            drawCircle(Color(0xFFA4A6AE), 10f, Offset(ex, -44f))
            drawCircle(Color(0xFF141216), 6f, Offset(ex, -44f))
        }
        for (mx in floatArrayOf(-1f, 1f)) drawOval(Color(0xFFE00012), Offset(mx * 250 - 16, -224f), Size(32f, 20f))
    }

    /** Seven red bars in each tail light: bass nearest the middle of the car. */
    private fun DrawScope.tailMeter(s: VisualState, x0: Float, dir: Int) {
        val left = if (dir > 0) x0 else x0 - 138
        drawRoundRect(Color(0xFF1A0408), Offset(left, -158f), Size(138f, 44f), CornerRadius(6f))
        var sum = 0f
        for (k in 0 until 7) {
            val band = 6 - k // outer edge high, inner edge low: dir > 0 is the left light, its inner edge on the right
            var v = 0f
            for (j in 0 until 4) v += s.mix[(band * 4 + j).coerceAtMost(s.n - 1)]
            v = min(1f, v / 4 * 1.4f)
            sum += v
            val bx = if (dir > 0) x0 + 6 + k * 19 else x0 - 6 - (k + 1) * 19
            val hgt = 6 + v * 32
            drawRect(Color(0x40FF2840), Offset(bx + 2, -152f), Size(14f, 32f))
            drawRect(Color(1f, (40 + v * 90) / 255f, (64 + v * 70) / 255f), Offset(bx + 2, -120 - hgt), Size(14f, hgt))
            drawRect(Color(0xFFFFD6DC), Offset(bx + 2, -120 - hgt), Size(14f, 2f))
        }
        drawRoundRect(Color(0xFFFF2A48).copy(alpha = 0.12f + sum / 7 * 0.3f), Offset(left - 8, -166f), Size(154f, 60f), CornerRadius(14f), blendMode = BlendMode.Plus)
        drawRoundRect(Color(0xFFC4C8D0), Offset(left, -158f), Size(138f, 44f), CornerRadius(6f), style = Stroke(2.5f))
    }

    private fun lit(c: Color, l: Float) = Color(min(1f, c.red * (1 + l)), min(1f, c.green * (1 + l)), min(1f, c.blue * (1 + l)))

    companion object {
        private const val N = 130
        private const val SEG = 240f
        private const val R = 2600f
        private const val LANE = R * 0.25f
        private const val CAM_H = 1200f
        private const val D = 0.9f
        private const val SPEED = 3800f
        private const val HORIZON = 0.42f
        private const val MAX_SPARKS = 1400
        private val LANES = floatArrayOf(-R * 0.5f, 0f, R * 0.5f)

        private val FW = listOf(
            Color(0xFFFF4FD8), Color(0xFF3FF2FF), Color(0xFFB06BFF), Color(0xFFFFD23F), Color(0xFFFF7A3D), Color.White, Color(0xFFFF5F9E),
        )

        /** How hard the road bends at segment [i]: slow, gentle curves. */
        private fun curve(i: Int) = sin(i * 0.0045f) + 0.7f * sin(i * 0.0017f + 2)

        private fun hash(i: Int): Float {
            val x = sin(i * 127.1f + 311.7f) * 43758.547f
            return x - floor(x)
        }

        private val STRIPES = listOf(Triple(-46f, -206f, 24f), Triple(16f, -206f, 24f), Triple(-46f, -182f, 22f), Triple(16f, -182f, 22f))

        private val CAR_BODY = Path().apply {
            moveTo(-200f, -40f); lineTo(-240f, -50f)
            quadraticTo(-252f, -110f, -238f, -150f); quadraticTo(-225f, -178f, -195f, -184f)
            lineTo(195f, -184f)
            quadraticTo(225f, -178f, 238f, -150f); quadraticTo(252f, -110f, 240f, -50f)
            lineTo(200f, -40f); close()
        }
        private val CAR_HAUNCHES = Path().apply {
            moveTo(-240f, -50f); quadraticTo(-246f, -90f, -236f, -120f); lineTo(-205f, -120f); lineTo(-205f, -50f); close()
            moveTo(240f, -50f); quadraticTo(246f, -90f, 236f, -120f); lineTo(205f, -120f); lineTo(205f, -50f); close()
        }
        private val CAR_DECK = Path().apply {
            moveTo(-200f, -184f); quadraticTo(-195f, -204f, -170f, -206f); lineTo(170f, -206f); quadraticTo(195f, -204f, 200f, -184f); close()
        }
        private val CAR_LIP = Path().apply { moveTo(-205f, -184f); lineTo(205f, -184f); lineTo(215f, -192f); lineTo(-215f, -192f); close() }
        private val CAR_CABIN = Path().apply { moveTo(-170f, -206f); lineTo(170f, -206f); lineTo(155f, -218f); lineTo(-155f, -218f); close() }
        private val CAR_SCREEN = Path().apply { moveTo(-170f, -224f); lineTo(170f, -224f); lineTo(150f, -300f); lineTo(-150f, -300f); close() }
    }
}
