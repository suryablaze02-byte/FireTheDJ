package com.autodj.ui.vis

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.autodj.dj.fx.FxType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chill Drive: a coast road at night under a big moon, seen from behind a dark sports coupe.
 * Hills fade into fog, the moon's path shimmers on the sea, and a street lamp passes on every
 * beat, washing the car in warm light. The tail-light strip is the spectrum meter. On a drop the
 * moon flares, an aurora unrolls across the sky and a few shooting stars fall.
 *
 * Kept light for the phone's processor: every gradient is made once (glows are unit gradients
 * moved and scaled into place, their strength set by alpha) and the car is drawn without clip masks.
 */
class DriveScene : Scene {
    /** How far the car has driven, in road units. */
    private var dist = 0f
    private var dropAge = 99f
    private var dropCool = 0f
    private var drop = 0f
    private val bands = FloatArray(12)

    private val stars = Random(7).let { r -> Array(140) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat() * 6f) } }

    // Hills, sea and road change shape every frame. As filled paths the render thread rebuilds each
    // one per frame (far too slow); as triangle strips they go straight to the GPU.
    private val strip = FloatArray(512)
    private val stripPaint = android.graphics.Paint()

    // Gradients that depend only on the screen size, made again when it changes.
    private var cachedW = -1f
    private var cachedH = -1f
    private lateinit var sky: Brush
    private lateinit var seaFill: android.graphics.Shader
    private lateinit var vignette: Brush
    private val fog = arrayOfNulls<Brush>(3)

    // The car, painted once per size and reused: its curved shapes are slow to redraw every frame.
    private var carScale = -1f
    private var carBody: ImageBitmap? = null
    private var carWash: ImageBitmap? = null
    private var carLamps: ImageBitmap? = null

    override fun step(s: VisualState) {
        val dt = s.dt
        if (!s.paused) {
            dist += dt * SPEED * (0.85f + 0.3f * s.energy)
            dropAge += dt
        }
        // A drop: the bass back well above where it's been, or an impact the mix plays.
        dropCool -= dt
        val hit = s.bass > 0.45f && s.bass - s.slowBass > 0.2f || s.active(FxType.IMPACT) != null || s.active(FxType.SUB_DROP) != null
        if (!s.paused && dropCool <= 0f && hit) {
            dropCool = 12f
            dropAge = 0f
        }
        drop = if (dropAge < 6f) min(1f, dropAge * 3f) * exp(-dropAge * 0.6f) else 0f
        val n = s.n
        for (i in 0 until 12) {
            val a = i * n / 12
            val b = max(a + 1, (i + 1) * n / 12)
            var v = 0f
            for (j in a until b) v += s.mix[j]
            bands[i] = min(1f, v / (b - a) * 1.4f)
        }
    }

    private fun prepare(w: Float, h: Float, hy: Float) {
        if (w == cachedW && h == cachedH) return
        cachedW = w
        cachedH = h
        sky = Brush.verticalGradient(0f to Color(0xFF050816), 0.55f to Color(0xFF101A3A), 0.86f to Color(0xFF3A3A66), 1f to Color(0xFFC98A7A), startY = 0f, endY = hy)
        seaFill = android.graphics.LinearGradient(0f, hy, 0f, h, 0xFF2B2D52.toInt(), 0xFF070A1C.toInt(), android.graphics.Shader.TileMode.CLAMP)
        for (li in 0..2) {
            fog[li] = Brush.verticalGradient(0f to FOG.copy(alpha = 0f), 1f to FOG.copy(alpha = 0.18f - li * 0.04f), startY = hy - h * HILL_AMP[li], endY = hy)
        }
        val vr = max(w, h) * 0.75f
        vignette = Brush.radialGradient((min(w, h) * 0.3f / vr) to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), center = Offset(w / 2, h * 0.55f), radius = vr)
    }

    /** Fills the first [points] (x, y) pairs of [strip] as a triangle strip, in [color] or [shader]. */
    private fun DrawScope.fillStrip(points: Int, color: Int, shader: android.graphics.Shader? = null) {
        stripPaint.shader = shader
        stripPaint.color = color
        // A shader is only applied with texture coordinates; the screen positions themselves serve.
        drawContext.canvas.nativeCanvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLE_STRIP, points * 2, strip, 0, if (shader != null) strip else null, 0, null, 0, null, 0, 0, stripPaint,
        )
    }

    /** Paints the car's still parts, its lamplight wash and its lit lamps into pictures at [cs] pixels per car unit. */
    private fun DrawScope.bakeCar(cs: Float) {
        if (cs == carScale) return
        carScale = cs
        val wPx = max(1, (CAR_W * cs).roundToInt())
        val hPx = max(1, (CAR_H * cs).roundToInt())
        val density: Density = this
        fun bake(block: DrawScope.() -> Unit) = ImageBitmap(wPx, hPx).also { img ->
            CanvasDrawScope().draw(density, layoutDirection, Canvas(img), Size(wPx.toFloat(), hPx.toFloat())) {
                withTransform({
                    translate(-CAR_X0 * cs, -CAR_Y0 * cs)
                    scale(cs, cs, Offset.Zero)
                }) { block() }
            }
        }
        carBody = bake { drawCarBody() }
        carWash = bake { drawPath(BODY, LAMPLIGHT) }
        carLamps = bake { for (lamp in arrayOf(LAMP_L, LAMP_R)) drawPath(lamp, LAMP_FILL) }
    }

    /** One of the unit glows below, moved to ([cx], [cy]) and stretched to radii [rx] by [ry]. */
    private fun DrawScope.glow(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f, blend: BlendMode = BlendMode.Plus) {
        withTransform({
            translate(cx, cy)
            scale(rx, ry, Offset.Zero)
        }) { drawCircle(brush, 1f, Offset.Zero, alpha = alpha, blendMode = blend) }
    }

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val t = s.time
        val port = h > w
        val hy = h * if (port) 0.46f else 0.52f
        val u = min(w, h) / 780f
        prepare(w, h, hy)

        // The road ahead in perspective: z = 4 is the bottom edge of the screen.
        // Upright the road is drawn wider, so near the car it reaches the screen edges as it does sideways.
        val f = w * if (port) 0.2f else 0.13f
        val ys = (h * 1.02f - hy) * 4f
        val bend = sin(dist * 0.0031f) * 0.9f + sin(dist * 0.0012f + 1f) * 0.5f
        fun rx(xw: Float, z: Float): Float {
            val d = min(1f, z / 260f)
            return w / 2 + xw * f / z + bend * d * d * w * 0.32f
        }
        fun ry(z: Float) = hy + ys / z

        // Sky and stars.
        drawRect(sky, size = Size(w, hy + 2))
        val starSize = Size(1.4f * u, 1.4f * u)
        for (st in stars) {
            val y = st[1] * hy
            val a = (0.25f + 0.55f * abs(sin(t * 0.7f + st[2]))) * (1 - y / hy)
            drawRect(STAR, Offset(st[0] * w, y), starSize, alpha = a)
        }

        // The drop: an aurora unrolling, shooting stars.
        if (drop > 0.02f) {
            // Each ribbon is a row of thin soft slices (one shared gradient, moved into place): a
            // thick curved stroke that changes shape every frame is far slower to draw.
            val cols = 60
            val cw = w / cols + 1f
            val half = hy * 0.07f
            for (k in 0..2) {
                val brush = if (k == 0) AURORA_A_UNIT else AURORA_B_UNIT
                for (i in 0..cols) {
                    val x = i * w / cols
                    val y = hy * (0.22f + k * 0.07f) + sin(x / w * 6f + t * 0.8f + k) * hy * 0.06f
                    withTransform({
                        translate(x, y)
                        scale(cw, half, Offset.Zero)
                    }) { drawRect(brush, Offset(0f, -1f), Size(1f, 2f), alpha = drop * 0.32f, blendMode = BlendMode.Plus) }
                }
            }
            for (k in 0..3) {
                val p = s.frac(dropAge * 0.7f + k * 0.23f)
                val sx = w * (0.15f + k * 0.22f) + p * w * 0.2f
                val sy = hy * (0.1f + k * 0.05f) + p * hy * 0.3f
                drawLine(Color.White, Offset(sx, sy), Offset(sx - w * 0.05f, sy - hy * 0.07f), 2f * u, StrokeCap.Round, alpha = drop * (1 - p), blendMode = BlendMode.Plus)
            }
        }

        // The moon in its halo, breathing a touch with the kick.
        val mx = w * if (port) 0.5f else 0.68f
        val my = hy * if (port) 0.42f else 0.38f
        val mr = min(w, h) * (if (port) 0.13f else 0.11f) * (1 + s.kick * 0.015f)
        val hr = mr * (4.5f + drop * 2f)
        clipRect(0f, 0f, w, hy) { glow(HALO_UNIT, mx, my, hr, hr, alpha = 0.28f + drop * 0.3f) }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFAF0), Color(0xFFD9D4E8)), center = Offset(mx - mr * 0.3f, my - mr * 0.3f), radius = mr * 1.3f), mr, Offset(mx, my))
        for ((cx, cy, cr) in CRATERS) drawCircle(CRATER, cr * mr, Offset(mx + cx * mr, my + cy * mr))

        // Three ranges of hills with fog settling between them.
        for (li in 0..2) {
            val amp = HILL_AMP[li]
            val fr = HILL_FREQ[li]
            val par = HILL_PAR[li]
            // The ridge line down to the horizon, as a strip of vertical slices.
            var n = 0
            for (i in 0..HILL_COLS) {
                val x = i * w / HILL_COLS
                val q = x / w + bend * 0.04f * par + dist * 0.0001f * par
                strip[n++] = x; strip[n++] = hy - h * amp * (0.45f + 0.55f * sin(q * fr + li * 2) * sin(q * fr * 0.37f + 1))
                strip[n++] = x; strip[n++] = hy + 1f
            }
            fillStrip(n / 2, HILL[li].toArgb())
            drawRect(fog[li]!!, Offset(0f, hy - h * amp * 1.1f), Size(w, h * amp * 1.1f))
        }

        // Land, and the sea beyond the right side of the road.
        drawRect(LAND, Offset(0f, hy), Size(w, h - hy))
        // The sea: from the road's far side out to the right edge, horizon to the bottom.
        var n = 0
        var z = 2000f
        while (z >= 4f && n < strip.size - 4) {
            strip[n++] = rx(60f, z); strip[n++] = ry(z)
            strip[n++] = w; strip[n++] = ry(z)
            z *= 0.85f
        }
        fillStrip(n / 2, -1, seaFill)
        // The moon's path on the water.
        for (i in 0 until 40) {
            val k = i / 40f
            val yy = hy + 2 + k.pow(1.6f) * (h - hy)
            val ww = (6 + i * 3) * u * (0.6f + 0.4f * sin(t * 3f + i * 1.7f))
            if (mx - ww < rx(60f, ys / max(1f, yy - hy))) continue
            drawRect(SHIMMER, Offset(mx - ww / 2 + sin(t * 2f + i) * 6 * u, yy), Size(ww, (1.6f + i * 0.06f) * u), alpha = 0.35f * (1 - k), blendMode = BlendMode.Plus)
        }

        // The road: surface, centre dashes, soft edge lines.
        n = 0
        z = 300f
        while (n < strip.size - 4) {
            val zz = max(z, 4f)
            strip[n++] = rx(-HALF, zz); strip[n++] = ry(zz)
            strip[n++] = rx(HALF, zz); strip[n++] = ry(zz)
            if (zz == 4f) break
            z *= 0.9f
        }
        fillStrip(n / 2, ROAD.toArgb())
        for (k in 0 until 40 step 2) {
            val z0 = 6 + k * 7 - dist % 14f
            if (z0 < 4f) continue
            val x0 = rx(-0.25f, z0)
            drawRect(DASH, Offset(x0, ry(z0 + 3.2f)), Size(max(1f, rx(0.25f, z0) - x0), ry(z0) - ry(z0 + 3.2f)))
        }
        for (side in floatArrayOf(-HALF + 0.6f, HALF - 0.6f)) {
            z = 4f
            var px = rx(side, z)
            var py = ry(z)
            while (z <= 300f) {
                z /= 0.9f
                val nx = rx(side, z)
                val ny = ry(z)
                drawLine(EDGE, Offset(px, py), Offset(nx, ny), 1.5f * u)
                px = nx
                py = ny
            }
        }

        // Street lamps on the left, one passing per beat; the nearest washes the car in light.
        val gap = SPEED * s.songBeatSec.toFloat().coerceIn(0.3f, 1f)
        var wash = 0f
        for (k in 13 downTo 0) {
            val lz = 4f + (k - s.frac(s.beat)) * gap
            if (lz < 3f) continue
            val bx = rx(-HALF - 2, lz)
            val by = ry(lz)
            val sc = f / lz
            val top = by - sc * 16f
            val hx = bx + sc * 5f
            drawLine(POLE, Offset(bx, by), Offset(bx, top), max(1f, sc * 0.5f))
            drawLine(POLE, Offset(bx, top), Offset(hx, top), max(1f, sc * 0.4f))
            val lr = max(1f, sc * 9f)
            glow(LAMP_UNIT, hx, top, lr, lr)
            // A soft pool of light on the road under the lamp, fading out as it passes the car.
            val near = ((lz - 4f) / 8f).coerceIn(0f, 1f)
            if (near > 0f) glow(POOL_UNIT, hx, by, max(1f, sc * 8f), max(1f, sc * 2.5f), alpha = near)
            if (lz < 14f) wash = max(wash, 1 - (lz - 3f) / 11f)
        }

        // The car.
        // The car takes about 40% of the road's width at its depth, upright and sideways alike.
        val cs = if (port) w / 1250f else min(w / 1500f, h / 560f)
        val cy = h * (if (port) 0.9f else 0.97f) + sin(t * 9f) * cs
        bakeCar(cs)
        val ox = w / 2 + CAR_X0 * cs
        val oy = cy + CAR_Y0 * cs
        withTransform({
            translate(w / 2, cy)
            scale(cs, cs, Offset.Zero)
        }) { drawCarUnder(s.kick) }
        drawImage(carBody!!, Offset(ox, oy))
        if (wash > 0f) drawImage(carWash!!, Offset(ox, oy), alpha = wash, blendMode = BlendMode.Plus)
        drawImage(carLamps!!, Offset(ox, oy), alpha = 0.55f + s.kick * 0.45f)
        withTransform({
            translate(w / 2, cy)
            scale(cs, cs, Offset.Zero)
        }) { drawCarLive(s.kick) }

        drawRect(vignette)
    }

    // A dark sports coupe from behind, in its own units: about 540 wide, the road at y = 0.

    /** Under the car, every frame: its shadow and the tail lights' glow on the road. */
    private fun DrawScope.drawCarUnder(kick: Float) {
        glow(SHADOW_UNIT, 0f, -2f, 300f, 26f, blend = BlendMode.SrcOver)
        glow(TAIL_UNIT, 0f, 18f, 300f, 40f, alpha = 0.2f + kick * 0.15f)
    }

    /** The parts of the car that never change, painted once by [bakeCar]. */
    private fun DrawScope.drawCarBody() {
        drawRoundRect(TYRE_L, Offset(-258f, -74f), Size(62f, 74f), CornerRadius(10f))
        drawRoundRect(TYRE_R, Offset(196f, -74f), Size(62f, 74f), CornerRadius(10f))
        // Body: graphite metallic, the sky on its upper curve, shaded at the sides so it reads round.
        drawPath(BODY, PAINT)
        drawPath(BODY, SKY_ON_BODY)
        drawPath(BODY, SIDE_SHADE)
        drawPath(SHOULDER, RIM, alpha = 0.6f, style = Stroke(2f))
        drawPath(DECK, DECK_FILL)
        drawRect(Color(0xFF0D0D11), Offset(-190f, -158.5f), Size(380f, 2.5f))
        // Cabin and rear window, two headrests behind the glass.
        drawPath(CABIN, CABIN_FILL)
        drawPath(GLASS, GLASS_FILL)
        for (hx in floatArrayOf(-52f, 52f)) drawRoundRect(Color(0xFF121219), Offset(hx - 24f, -212f), Size(48f, 34f), CornerRadius(12f))
        drawPath(GLARE_A, RIM, alpha = 0.18f)
        drawPath(GLARE_B, RIM, alpha = 0.08f)
        drawPath(ROOF, RIM, alpha = 0.7f, style = Stroke(1.8f))
        drawPath(FIN, Color(0xFF16171D))
        drawPath(MIRROR_ARMS, Color(0xFF1B1C23))
        for (mx in floatArrayOf(-220f, 182f)) drawRoundRect(MIRROR, Offset(mx, -198f), Size(38f, 22f), CornerRadius(8f))
        // The meter's dark strip and the unlit lamps; the lit lamps are a separate picture, faded with the kick.
        drawRect(Color(0xFF16040A), Offset(-118f, -142f), Size(236f, 6f))
        for (lamp in arrayOf(LAMP_L, LAMP_R)) {
            drawPath(lamp, Color(0xFF2A0509))
            drawPath(lamp, Color.White, alpha = 0.12f, style = Stroke(1f))
        }
        // Plate, lower bumper with diffuser fins, four exhaust tips.
        drawRoundRect(Color(0xFF0C0C10), Offset(-56f, -110f), Size(112f, 38f), CornerRadius(6f))
        drawRoundRect(Color(0xFFD8D5CB), Offset(-46f, -104f), Size(92f, 26f), CornerRadius(3f))
        for (k in 0 until 6) drawRect(Color(0xFF2A2F45), Offset(-35f + k * 12f, -98f), Size(8f, 14f))
        drawPath(BUMPER, Color(0xFF08080B))
        for (k in -3..3) drawLine(Color(0xFF1D1E25), Offset(k * 22f, -50f), Offset(k * 22f, -24f), 3f)
        for (ex in floatArrayOf(-150f, -112f, 112f, 150f)) {
            drawOval(EXHAUST, Offset(ex - 15f, -46f), Size(30f, 18f))
            drawOval(Color(0xFF050506), Offset(ex - 11f, -43f), Size(22f, 12f))
        }
    }

    /** The car's live parts, over the pictures: the meter, the lamps' LED lines and their bloom. */
    private fun DrawScope.drawCarLive(kick: Float) {
        // The light strip between the lamps: the spectrum, bass in the middle.
        val seg = 236f / STRIP
        for (i in 0 until STRIP) {
            val b = bands[(abs(i - (STRIP - 1) / 2f) / ((STRIP - 1) / 2f) * 11).toInt().coerceIn(0, 11)]
            drawRect(Color(1f, (36 + b * 80) / 255f, (56 + b * 40) / 255f, 0.2f + b * 0.8f), Offset(-118f + i * seg + 0.8f, -141f), Size(seg - 1.6f, 4f))
        }
        val led = 0.5f + kick * 0.4f
        drawRect(Color(0xFFFFBEC8), Offset(-256f, -143f), Size(128f, 2.2f), alpha = led)
        drawRect(Color(0xFFFFBEC8), Offset(128f, -143f), Size(128f, 2.2f), alpha = led)
        drawRect(BLOOM, Offset(-280f, -175f), Size(560f, 75f), alpha = 0.18f + kick * 0.2f, blendMode = BlendMode.Plus)
    }

    companion object {
        private const val SPEED = 42f
        private const val HALF = 14f
        private const val STRIP = 26
        private const val HILL_COLS = 90

        // The car's pictures cover x -300..300, y -260..40 in car units.
        private const val CAR_X0 = -300f
        private const val CAR_Y0 = -260f
        private const val CAR_W = 600f
        private const val CAR_H = 300f

        private val STAR = Color(0xFFE6EBFF)
        // An aurora slice: soft at both edges, bright in a thin core. Height 2 around y = 0.
        private val AURORA_A_UNIT = Brush.verticalGradient(
            0f to Color.Transparent, 0.3f to Color(0x4050E6AA), 0.47f to Color(0xFF50E6AA), 0.53f to Color(0xFF50E6AA), 0.7f to Color(0x4050E6AA), 1f to Color.Transparent,
            startY = -1f, endY = 1f,
        )
        private val AURORA_B_UNIT = Brush.verticalGradient(
            0f to Color.Transparent, 0.3f to Color(0x4078E6D2), 0.47f to Color(0xFF78E6D2), 0.53f to Color(0xFF78E6D2), 0.7f to Color(0x4078E6D2), 1f to Color.Transparent,
            startY = -1f, endY = 1f,
        )
        private val CRATER = Color(0x2E9691AF)
        private val CRATERS = listOf(Triple(0.3f, -0.2f, 0.22f), Triple(-0.35f, 0.25f, 0.16f), Triple(0.1f, 0.4f, 0.12f))
        private val HILL = listOf(Color(0xFF2A2C55), Color(0xFF1A1B3C), Color(0xFF0E0F24))
        private val HILL_AMP = floatArrayOf(0.11f, 0.075f, 0.045f)
        private val HILL_FREQ = floatArrayOf(3.1f, 5.3f, 8.7f)
        private val HILL_PAR = floatArrayOf(0.25f, 0.5f, 0.85f)
        private val FOG = Color(0xFFAA96BE)
        private val LAND = Color(0xFF07081A)
        private val SHIMMER = Color(0xFFDCE1FF)
        private val ROAD = Color(0xFF111225)
        private val DASH = Color(0x8CF0E6D2)
        private val EDGE = Color(0x59C8BEE6)
        private val POLE = Color(0xFF0A0A14)
        private val RIM = Color(0xFFBEC8FF)
        private val TAIL = Color(0xFFFF1E3C)

        // Glows of radius 1 at the origin, placed and sized with a transform.
        private val HALO_UNIT = Brush.radialGradient(listOf(Color(0xFFC8D2FF), Color.Transparent), center = Offset.Zero, radius = 1f)
        private val LAMP_UNIT = Brush.radialGradient(0f to Color(0xE6FFD696), 0.2f to Color(0x40FFAA5A), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
        private val POOL_UNIT = Brush.radialGradient(listOf(Color(0x38FFB46E), Color.Transparent), center = Offset.Zero, radius = 1f)
        private val SHADOW_UNIT = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent), center = Offset.Zero, radius = 1f)
        private val TAIL_UNIT = Brush.radialGradient(listOf(TAIL, Color.Transparent), center = Offset.Zero, radius = 1f)

        // The car's fills, in its own units.
        private val TYRE_L = Brush.horizontalGradient(0f to Color(0xFF0B0B0E), 0.2f to Color(0xFF1B1B21), 1f to Color(0xFF09090B), startX = -258f, endX = -196f)
        private val TYRE_R = Brush.horizontalGradient(0f to Color(0xFF0B0B0E), 0.8f to Color(0xFF1B1B21), 1f to Color(0xFF09090B), startX = 196f, endX = 258f)
        private val PAINT = Brush.verticalGradient(0f to Color(0xFF5A5D6B), 0.12f to Color(0xFF2B2D36), 0.5f to Color(0xFF17181E), 1f to Color(0xFF0A0A0D), startY = -158f, endY = -22f)
        private val SKY_ON_BODY = Brush.verticalGradient(0f to RIM.copy(alpha = 0.35f), 1f to Color.Transparent, startY = -160f, endY = -120f)
        private val SIDE_SHADE = Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.55f), 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), startX = -270f, endX = 270f,
        )
        private val LAMPLIGHT = Brush.verticalGradient(0f to Color(0xFFFFC890).copy(alpha = 0.22f), 1f to Color.Transparent, startY = -160f, endY = -100f)
        private val DECK_FILL = Brush.verticalGradient(listOf(Color(0xFF6A6D7C), Color(0xFF2A2C35)), startY = -170f, endY = -158f)
        private val CABIN_FILL = Brush.verticalGradient(listOf(Color(0xFF4B4E5B), Color(0xFF1D1E25)), startY = -236f, endY = -170f)
        private val GLASS_FILL = Brush.verticalGradient(listOf(Color(0xFF1A1C2A), Color(0xFF07070B)), startY = -226f, endY = -174f)
        private val MIRROR = Brush.verticalGradient(listOf(Color(0xFF5A5D6B), Color(0xFF1C1D24)), startY = -198f, endY = -176f)
        private val LAMP_FILL = Brush.verticalGradient(0f to Color(0xFFFF3C50), 0.5f to Color(0xFFFF1428), 1f to Color(0xFF8C0014), startY = -150f, endY = -124f)
        private val BLOOM = Brush.verticalGradient(0f to Color.Transparent, 0.55f to TAIL, 1f to Color.Transparent, startY = -175f, endY = -100f)
        private val EXHAUST = Brush.verticalGradient(listOf(Color(0xFFC9CCD4), Color(0xFF5B5E66)), startY = -46f, endY = -28f)

        private val BODY = Path().apply {
            moveTo(-236f, -22f); lineTo(-252f, -30f)
            cubicTo(-266f, -62f, -270f, -98f, -262f, -122f); cubicTo(-256f, -140f, -238f, -152f, -206f, -158f)
            lineTo(206f, -158f)
            cubicTo(238f, -152f, 256f, -140f, 262f, -122f); cubicTo(270f, -98f, 266f, -62f, 252f, -30f)
            lineTo(236f, -22f); close()
        }
        private val SHOULDER = Path().apply {
            moveTo(-258f, -130f); cubicTo(-250f, -148f, -232f, -155f, -206f, -158f)
            lineTo(206f, -158f); cubicTo(232f, -155f, 250f, -148f, 258f, -130f)
        }
        private val DECK = Path().apply { moveTo(-196f, -158f); lineTo(-188f, -170f); lineTo(188f, -170f); lineTo(196f, -158f); close() }
        private val CABIN = Path().apply {
            moveTo(-168f, -170f); cubicTo(-156f, -204f, -136f, -228f, -96f, -236f)
            lineTo(96f, -236f); cubicTo(136f, -228f, 156f, -204f, 168f, -170f); close()
        }
        private val GLASS = Path().apply {
            moveTo(-132f, -174f); cubicTo(-122f, -202f, -106f, -220f, -78f, -226f)
            lineTo(78f, -226f); cubicTo(106f, -220f, 122f, -202f, 132f, -174f); close()
        }
        // The glass's reflections, kept inside the window so they need no clip.
        private val GLARE_A = Path().apply { moveTo(-60f, -224f); lineTo(-24f, -224f); lineTo(-94f, -176f); lineTo(-127f, -176f); close() }
        private val GLARE_B = Path().apply { moveTo(8f, -224f); lineTo(26f, -224f); lineTo(-34f, -176f); lineTo(-52f, -176f); close() }
        private val ROOF = Path().apply {
            moveTo(-150f, -206f); cubicTo(-136f, -226f, -120f, -234f, -96f, -236f)
            lineTo(96f, -236f); cubicTo(120f, -234f, 136f, -226f, 150f, -206f)
        }
        private val FIN = Path().apply { moveTo(-9f, -236f); quadraticTo(-4f, -250f, 6f, -248f); lineTo(9f, -236f); close() }
        private val MIRROR_ARMS = Path().apply {
            for (sx in floatArrayOf(-1f, 1f)) {
                moveTo(sx * 166f, -182f); lineTo(sx * 184f, -186f); lineTo(sx * 184f, -180f); lineTo(sx * 166f, -176f); close()
            }
        }
        private val LAMP_R = Path().apply {
            moveTo(118f, -144f); lineTo(236f, -150f); cubicTo(250f, -148f, 258f, -140f, 260f, -128f)
            lineTo(232f, -124f); lineTo(130f, -132f); close()
        }
        private val LAMP_L = Path().apply {
            moveTo(-118f, -144f); lineTo(-236f, -150f); cubicTo(-250f, -148f, -258f, -140f, -260f, -128f)
            lineTo(-232f, -124f); lineTo(-130f, -132f); close()
        }
        private val BUMPER = Path().apply { moveTo(-236f, -22f); lineTo(-246f, -52f); lineTo(246f, -52f); lineTo(236f, -22f); close() }
    }
}
