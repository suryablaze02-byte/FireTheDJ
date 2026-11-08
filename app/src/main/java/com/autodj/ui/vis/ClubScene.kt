package com.autodj.ui.vis

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.autodj.dj.fx.FxType
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The club from inside the crowd: a pixel LED wall with side wings, beams through haze, a backlit
 * DJ and three depths of people. Mostly dark, so the drops land. Everything that doesn't change
 * (room, rig, people) is drawn once into images and only moved each frame, so it holds up on a TV.
 */
class ClubScene : Scene {
    private enum class Part { GROOVE, BREAK, BUILD, DROP }

    private class Confetti(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val color: Color, val size: Float)

    private val confetti = ArrayList<Confetti>()
    private val rnd = kotlin.random.Random(11)
    private val seen = HashSet<FxType>()
    private var part = Part.GROOVE
    private var build = 0f
    private var hype = 0f
    private var flash = 0f
    private var dropAge = 99f
    private var dropCool = 0f
    private var slowEnergy = 0f

    private val pixels = IntArray(LED_W * LED_H)
    private val led = Bitmap.createBitmap(LED_W, LED_H, Bitmap.Config.ARGB_8888)
    private val ledImage = led.asImageBitmap()
    private val ledPaint = Paint().apply { isFilterBitmap = false }
    private val beamSprite = beamBitmap().asImageBitmap()
    private val glowSprite = glowBitmap().asImageBitmap()
    private val people = PersonSprites()
    private var room: Room? = null

    override fun step(s: VisualState) {
        val dt = if (s.paused) 0f else s.dt
        val now = HashSet<FxType>()
        var impact = false
        for ((a, _) in s.fx) {
            now += a.type
            val fresh = a.type !in seen
            if (fresh && a.type in IMPACTS) impact = true
        }
        seen.clear()
        seen += now
        build = s.fx.filter { it.first.type in BUILDS }.maxOfOrNull { it.first.progress * it.first.amount } ?: 0f

        dropAge += dt
        dropCool -= dt
        if (!s.paused && dropCool <= 0f && (impact || (s.bass > 0.45f && s.bass > s.slowBass * 1.7f))) {
            dropCool = 12f
            dropAge = 0f
            flash = 1f
            hype = 1f
            repeat(46) {
                confetti += Confetti(rnd.nextFloat(), -0.05f, (rnd.nextFloat() - 0.5f) * 0.12f, 0.1f + 0.14f * rnd.nextFloat(), 4f,
                    CONFETTI[rnd.nextInt(CONFETTI.size)], 4f + 6f * rnd.nextFloat())
            }
        }
        flash = max(0f, flash - s.dt * 2.2f)
        hype = max(0f, hype - dt * 0.12f)
        slowEnergy += (s.energy - slowEnergy) * min(1f, s.dt / 5f)
        part = when {
            dropAge < 10f -> Part.DROP
            build > 0.08f -> Part.BUILD
            s.energy < slowEnergy * 0.55f -> Part.BREAK
            else -> Part.GROOVE
        }
        val it = confetti.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        paintWall(s)
    }

    /** What the LED wall is showing: a tiny picture, scaled up so its pixels are the look. */
    private fun paintWall(s: VisualState) {
        val (ca, cb) = s.palette
        val t = s.beat
        val pi = PI.toFloat()
        val kick = if (part == Part.BREAK) s.kick * 0.15f else s.kick
        for (j in 0 until LED_H) {
            val v = j / (LED_H - 1f)
            val cy = abs(v - 0.5f)
            for (i in 0 until LED_W) {
                val u = i / (LED_W - 1f)
                val cx = abs(u - 0.5f)
                val band = s.mix[(cx * 2f * (s.n - 1)).toInt().coerceIn(0, s.n - 1)]
                var b: Float
                var m: Float
                when (part) {
                    Part.GROOVE -> {
                        b = 0.5f + 0.5f * sin((cx * 7f + v * 4f - t * 0.5f) * pi)
                        b = b * b * (0.35f + 1.1f * band)
                        m = 0.5f + 0.5f * sin(v * 5f + t * 0.25f)
                    }
                    Part.BREAK -> {
                        b = max(0f, 0.45f - hypot(cx * 2.2f, cy)) * 1.1f * (0.7f + 0.3f * sin(t * 0.5f))
                        m = 0.15f
                    }
                    Part.BUILD -> {
                        val lines = 2 + (build * 7f).toInt()
                        val edge = cy * lines * 2f - t * (1f + build * 3f)
                        b = if (edge - floor(edge) < 0.32f + build * 0.4f) 0.25f + build * 0.75f else 0.04f
                        m = build
                    }
                    Part.DROP -> {
                        val spin = atan2(v - 0.5f, (u - 0.5f) * 2.4f) * 4f / (2f * pi) + hypot(cx * 2.4f, cy) * 2.5f - t * 0.5f
                        b = if (spin - floor(spin) < 0.5f) 1f else 0.08f
                        b *= 0.55f + 0.6f * kick
                        val ring = hypot(cx * 2.4f, cy) * 2f - t * 0.25f
                        m = ring - floor(ring)
                    }
                }
                val f = b.coerceIn(0f, 1f)
                val hi = if (part == Part.DROP) kick * 0.5f * f else 0f
                val r = (ca.red + (cb.red - ca.red) * m) * f
                val g = (ca.green + (cb.green - ca.green) * m) * f
                val bl = (ca.blue + (cb.blue - ca.blue) * m) * f
                pixels[j * LED_W + i] = (0xFF shl 24) or
                    (((r + (1 - r) * hi) * 255).toInt() shl 16) or
                    (((g + (1 - g) * hi) * 255).toInt() shl 8) or
                    ((bl + (1 - bl) * hi) * 255).toInt()
            }
        }
        led.setPixels(pixels, 0, LED_W, 0, 0, LED_W, LED_H)
    }

    // The mix's effects play on the wall above the DJ's head.
    override fun effectArea(size: Size): Rect {
        val g = Stage(size.width, size.height)
        return Rect(g.wallX, g.wallY, g.wallX + g.wallW, g.headTop)
    }

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val r = room?.takeIf { it.stage.w == w && it.stage.h == h } ?: Room(Stage(w, h), density).also { room = it }
        val g = r.stage
        val (ca, cb) = s.palette
        val middle = lerp(ca, cb, 0.5f)
        val beat = s.beat
        val pi = PI.toFloat()
        val kick = if (part == Part.BREAK) s.kick * 0.15f else s.kick
        val energy = when (part) {
            Part.BREAK -> 0.12f
            Part.BUILD -> 0.3f + 0.5f * build
            Part.DROP -> max(0.75f, s.energy)
            Part.GROOVE -> s.energy.coerceIn(0.2f, 0.75f)
        }
        val tintA = ColorFilter.tint(ca, BlendMode.SrcIn)
        val tintB = ColorFilter.tint(cb, BlendMode.SrcIn)
        val tintMiddle = ColorFilter.tint(middle, BlendMode.SrcIn)

        fun glow(cx: Float, cy: Float, radius: Float, alpha: Float, tint: ColorFilter) = drawImage(
            glowSprite, IntOffset.Zero, IntSize(GLOW, GLOW), IntOffset((cx - radius).roundToInt(), (cy - radius).roundToInt()),
            IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()), alpha = alpha.coerceIn(0f, 1f), colorFilter = tint, blendMode = BlendMode.Plus,
        )

        drawImage(r.ground)

        // The wall, its wings, and the pixel grid over it.
        drawImage(ledImage, IntOffset.Zero, IntSize(LED_W, LED_H), IntOffset(g.wallX.roundToInt(), g.wallY.roundToInt()), IntSize(g.wallW.roundToInt(), g.wallH.roundToInt()), filterQuality = FilterQuality.None)
        if (g.wide) {
            drawContext.canvas.nativeCanvas.drawBitmapMesh(led, WING_STEPS, 1, r.leftWing, 0, null, 0, ledPaint)
            drawContext.canvas.nativeCanvas.drawBitmapMesh(led, WING_STEPS, 1, r.rightWing, 0, null, 0, ledPaint)
        }
        drawImage(r.grid, topLeft = Offset(g.wallX, g.wallY))

        glow(w / 2, g.wallY + g.wallH / 2, max(w, h) * 0.55f, 0.05f + 0.2f * energy, tintMiddle)
        for (i in 0 until (if (ON_TV) 2 else 4)) {
            val drift = 0.036f * s.time * (1 + i * 0.2f) + i * 0.27f
            glow(w * (drift - floor(drift)), h * (0.24f + 0.09f * i), h * 0.42f, 0.03f + 0.08f * energy, if (i % 2 == 0) tintB else tintA)
        }

        // The polished floor catches the wall.
        clipRect(0f, g.floorY, w, h) {
            withTransform({ scale(1f, -1f, Offset(0f, g.floorY)) }) {
                drawImage(ledImage, IntOffset.Zero, IntSize(LED_W, LED_H),
                    IntOffset((g.wallX - w * 0.06f).roundToInt(), (g.floorY - g.wallH * 0.9f).roundToInt()),
                    IntSize((g.wallW + w * 0.12f).roundToInt(), (g.wallH * 0.9f).roundToInt()), alpha = 0.5f, filterQuality = FilterQuality.Low)
            }
        }
        drawRect(r.floorFade, Offset(0f, g.floorY), Size(w, h - g.floorY))
        drawImage(r.rig)

        // Moving heads: slow in a breakdown, pulling to the centre through a build, wide on the drop.
        val heads = if (g.wide) 10 else 6
        val len = h * 1.12f
        val headY = g.trussY + g.trussH + 10f
        val spot = if (g.wide) 4 else 3
        for (i in 0 until heads) {
            val x = w * (i + 0.5f) / heads
            val dir = if (i % 2 == 0) -1f else 1f
            val tint = if (i % 2 == 0) tintB else tintA
            var ang = 0f
            var a = 0f
            var spread = 0.055f
            when (part) {
                Part.GROOVE -> { ang = sin(beat * 0.785f + i * 0.9f) * 0.55f * dir; a = 0.34f + 0.5f * kick }
                Part.BREAK -> if (i % spot == 1) { ang = sin(s.time * 0.4f + i) * 0.3f; a = 0.5f }
                Part.BUILD -> {
                    val toCentre = atan((w / 2 - x) / h * 1.4f)
                    ang = (sin(beat * 0.785f + i) * 0.4f).let { it + (toCentre - it) * build }
                    a = 0.3f + 0.6f * build * (0.5f + 0.5f * kick)
                    spread = 0.06f - 0.035f * build
                }
                Part.DROP -> { ang = dir * (0.18f + 0.55f * (0.5f + 0.5f * sin(beat * 0.39f + i * 0.5f))); a = 0.4f + 0.6f * kick }
            }
            if (a > 0f) {
                val half = len * spread
                rotate(-ang * 180f / pi, Offset(x, headY)) {
                    drawImage(beamSprite, IntOffset.Zero, IntSize(BEAM_W, BEAM_H), IntOffset((x - half).roundToInt(), headY.roundToInt()),
                        IntSize((half * 2).roundToInt(), len.roundToInt()), alpha = a.coerceIn(0f, 1f), colorFilter = tint, blendMode = BlendMode.Plus, filterQuality = FilterQuality.Low)
                }
                glow(x, headY, 20f + 10f * kick, 0.6f + 0.3f * kick, tint)
            }
        }

        // The DJ, dark against the wall, then the lit booth in front.
        val bob = sin(beat * pi) * (1f + 4f * energy)
        val dj = if (part == Part.DROP || part == Part.BUILD) r.djUp else r.dj
        drawImage(dj, topLeft = Offset(w / 2 - dj.width / 2f, g.boothY - dj.height + 4f + bob * 0.3f))
        drawImage(r.booth, topLeft = Offset(g.boothX, g.boothY))
        drawRect(middle.copy(alpha = 0.55f + 0.45f * kick), Offset(g.boothX + g.boothW * 0.03f, g.boothY), Size(g.boothW * 0.94f, 3f), blendMode = BlendMode.Plus)
        for (slot in 0..1) {
            val cx = g.boothX + g.boothW * (if (slot == 0) 0.2f else 0.8f)
            val cy = g.boothY + g.boothH * 0.42f
            val a = if (s.present[slot]) 0.25f + 0.75f * s.gains[slot] else 0.15f
            glow(cx, cy, g.boothH * 0.55f, 0.6f * a, ColorFilter.tint(s.deckColor(slot), BlendMode.SrcIn))
            drawCircle(s.deckColor(slot).copy(alpha = a), g.boothH * 0.2f, Offset(cx, cy), style = Stroke(2.5f))
        }
        for (i in 0 until 7) {
            val level = s.mix[(i * (s.n - 1)) / 6]
            drawRect(middle.copy(alpha = 0.3f + 0.6f * level), Offset(w / 2 - g.boothW * 0.08f + i * g.boothW * 0.024f, g.boothY + g.boothH * (0.7f - 0.5f * level)), Size(g.boothW * 0.012f, g.boothH * 0.14f))
        }

        // The crowd, back to front: rim-lit from above, more hands up as the night peaks.
        val handsUp = when (part) { Part.DROP -> 0.75f; Part.BUILD -> build * 0.6f; Part.GROOVE -> 0.16f; Part.BREAK -> 0f }
        for (row in r.rows) {
            val shade = SHADES[row.index]
            val bodyColor = lerp(middle, ROOM_DARK, shade)
            val body = ColorFilter.tint(bodyColor, BlendMode.SrcIn)
            val rimColor = lerp(lerp(ca, cb, row.index / 2f), Color.White, 0.35f)
            val rim = ColorFilter.tint(rimColor.copy(alpha = ((0.5f + 0.5f * energy) * (0.65f + 0.35f * kick)).coerceIn(0f, 1f)), BlendMode.SrcIn)
            for (p in row.people) {
                val bounce = if (s.paused) 0f else abs(sin(beat * pi + p.phase * 0.15f)) * row.radius * p.amount * (0.2f + 1.4f * energy)
                val arms = when {
                    p.seed >= handsUp -> 0
                    p.seed > 0.45f * handsUp -> 2
                    else -> 1
                }
                val pr = row.radius * p.height
                val top = IntOffset((p.x - PersonSprites.ORIGIN_X * pr).roundToInt(), (row.y - bounce - PersonSprites.ORIGIN_Y * pr).roundToInt())
                val sized = IntSize((PersonSprites.WIDTH * pr).roundToInt(), (PersonSprites.HEIGHT * pr).roundToInt())
                val sprite = people.body[p.style][arms]
                val src = IntSize(sprite.width, sprite.height)
                drawImage(sprite, IntOffset.Zero, src, top, sized, colorFilter = body, filterQuality = FilterQuality.Low)
                // A cut-out ends at the waist; the rest of the body carries on down past the screen.
                val feet = (top.y + sized.height - 1).toFloat()
                if (feet < h) drawRect(bodyColor, Offset(p.x - pr * 1.65f, feet), Size(pr * 3.3f, h - feet))
                drawImage(people.rim[p.style][arms], IntOffset.Zero, src, top, sized, colorFilter = rim, blendMode = BlendMode.Plus, filterQuality = FilterQuality.Low)
                if (row.index == 1 && arms > 0 && p.seed > 0.1f && p.phone) {
                    val px = p.x - pr * 1.9f
                    val py = row.y - bounce - pr * 3.7f
                    drawRect(PHONE, Offset(px, py), Size(pr * 0.55f, pr * 0.9f))
                }
            }
        }

        for (c in confetti) drawRect(c.color.copy(alpha = (c.life / 4f).coerceIn(0f, 1f)), Offset(c.x * w, c.y * h), Size(c.size, c.size * 0.6f))

        // Blinders: the one moment the whole room lights up.
        if (flash > 0f) {
            for (i in 0 until 6) glow(w * (i + 0.5f) / 6, g.trussY + g.trussH, h * 0.5f, 0.7f * flash, WARM)
            drawRect(Color(0xFFFFF0D7).copy(alpha = 0.45f * flash), blendMode = BlendMode.Plus)
        }
        // Another full-screen layer: a TV's graphics chip needs that time for the picture itself.
        if (!ON_TV) drawImage(r.vignette)
    }

    /** Where everything stands, sideways or upright. */
    private class Stage(val w: Float, val h: Float) {
        val wide = w > h
        val wallX = w * (if (wide) 0.25f else 0.05f)
        val wallY = h * (if (wide) 0.14f else 0.16f)
        val wallW = w * (if (wide) 0.5f else 0.9f)
        val wallH = h * (if (wide) 0.42f else 0.32f)
        val floorY = h * (if (wide) 0.64f else 0.58f)
        val boothY = h * (if (wide) 0.5f else 0.42f)
        val boothH = h * (if (wide) 0.13f else 0.085f)
        val boothW = w * (if (wide) 0.42f else 0.72f)
        val boothX = (w - boothW) / 2
        val trussY = h * (if (wide) 0.06f else 0.07f)
        val trussH = h * 0.035f
        val headRadius = boothH * 0.3f
        val headTop = boothY - boothH * 0.38f - headRadius * 1.3f
    }

    private class Person(val x: Float, val height: Float, val phase: Float, val seed: Float, val amount: Float, val style: Int, val phone: Boolean)
    private class Row(val index: Int, val y: Float, val radius: Float, val people: List<Person>)

    /** All that only changes when the screen does. */
    private class Room(val stage: Stage, private val density: Float) {
        private val w = stage.w
        private val h = stage.h
        val ground = bake(w, h) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF050509), Color(0xFF0E0C14)), 0f, stage.floorY), Offset.Zero, Size(w, stage.floorY))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF101016), Color(0xFF030305)), stage.floorY, h), Offset(0f, stage.floorY), Size(w, h - stage.floorY))
        }
        val floorFade = Brush.verticalGradient(listOf(Color(0x00030305), Color(0xF2030305)), stage.floorY, stage.floorY + h * 0.2f)
        val grid = bake(stage.wallW, stage.wallH) {
            val cw = size.width / LED_W
            val rh = size.height / LED_H
            val line = Color.Black.copy(alpha = 0.42f)
            for (i in 0..LED_W) drawRect(line, Offset(i * cw - 0.6f, 0f), Size(1.2f, size.height))
            for (j in 0..LED_H) drawRect(line, Offset(0f, j * rh - 0.6f), Size(size.width, 1.2f))
            drawRect(Color(0xFF17171D), style = Stroke(4f))
        }
        val leftWing = wing(0.03f * w, 0.225f * w)
        val rightWing = wing(0.97f * w, 0.775f * w)
        // Only as tall as the rig reaches: a full-screen image would cost its whole area every frame.
        val rig = bake(w, h * 0.46f) {
            if (stage.wide) for (side in listOf(0.015f, 0.935f)) for (i in 0 until 6) {
                val tilt = (if (side < 0.5f) 2f else -2f) * i
                rotate(tilt, Offset(w * side + w * 0.025f, h * 0.12f + i * h * 0.05f)) {
                    drawRect(Color(0xFF08080C), Offset(w * side - w * 0.003f, h * 0.12f + i * h * 0.05f), Size(w * 0.056f, h * 0.046f))
                    drawRect(Color(0xFF1A1A22), Offset(w * side + w * 0.001f, h * 0.128f + i * h * 0.05f), Size(w * 0.048f, h * 0.03f))
                }
            }
            val y = stage.trussY
            val th = stage.trussH
            val steel = Color(0xFF34343F)
            drawLine(steel, Offset(0f, y), Offset(w, y), 2f)
            drawLine(steel, Offset(0f, y + th), Offset(w, y + th), 2f)
            val step = th * 1.2f
            var i = 0
            while (i * step < w) {
                drawLine(steel, Offset(i * step, y + if (i % 2 == 1) th else 0f), Offset((i + 1) * step, y + if (i % 2 == 1) 0f else th), 2f)
                i++
            }
            val heads = if (stage.wide) 10 else 6
            for (k in 0 until heads) drawRect(Color(0xFF17171E), Offset(w * (k + 0.5f) / heads - 9f, y + th), Size(18f, 10f))
        }
        val booth = bake(stage.boothW, stage.boothH) {
            val bw = size.width
            val path = Path().apply { moveTo(bw * 0.03f, 0f); lineTo(bw * 0.97f, 0f); lineTo(bw, size.height); lineTo(0f, size.height); close() }
            drawPath(path, Color(0xFF0B0B10))
        }
        val dj = djSprite(false)
        val djUp = djSprite(true)
        val vignette = bake(w, h) {
            val r = max(w, h) * 0.78f
            val c = Offset(w / 2, h * 0.45f)
            val inner = min(w, h) * 0.4f / r
            drawRect(Brush.radialGradient(0f to Color.Transparent, inner to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f), center = c, radius = r))
        }
        val rows: List<Row> = run {
            val random = Random(77)
            val unit = min(w, h) * (if (stage.wide) 0.042f else 0.06f)
            val depths = if (stage.wide) listOf(0.76f to 0.62f, 0.86f to 0.88f, 0.98f to 1.3f) else listOf(0.72f to 0.62f, 0.82f to 0.88f, 0.94f to 1.3f)
            depths.mapIndexed { index, (y, s) ->
                val list = ArrayList<Person>()
                var x = -unit
                while (x < w + unit) {
                    list += Person(x, 0.85f + random.nextFloat() * 0.3f, random.nextFloat() * 6.28f, random.nextFloat(), 0.4f + random.nextFloat() * 0.8f, random.nextInt(3), random.nextFloat() > 0.82f)
                    x += unit * s * (2.5f + random.nextFloat() * 1.4f)
                }
                Row(index, y * h, unit * s, list)
            }
        }

        /** A wall panel angled towards the room: [nearX] is its edge closest to the viewer. */
        private fun wing(nearX: Float, farX: Float): FloatArray {
            val verts = FloatArray((WING_STEPS + 1) * 2 * 2)
            val cy = stage.wallY + stage.wallH / 2
            for (i in 0..WING_STEPS) {
                val x = nearX + (farX - nearX) * i / WING_STEPS
                val near = abs(x - w / 2) / (w / 2)
                val half = stage.wallH * (0.36f + 0.28f * near)
                verts[i * 2] = x
                verts[i * 2 + 1] = cy - half
                verts[(WING_STEPS + 1 + i) * 2] = x
                verts[(WING_STEPS + 1 + i) * 2 + 1] = cy + half
            }
            return verts
        }

        private fun djSprite(armUp: Boolean): ImageBitmap {
            val hr = stage.headRadius
            val spriteW = hr * 5
            val spriteH = hr * 6.6f
            return bake(spriteW, spriteH) {
                val cx = size.width / 2
                val cy = hr * 2.7f
                val ink = Color(0xFF020203)
                val hand = if (armUp) Offset(cx + hr * 1.8f, cy - hr * 2.2f) else Offset(cx + hr * 1.7f, cy + hr * 2.7f)
                drawLine(ink, Offset(cx + hr * 1.2f, cy + hr * 1.6f), hand, hr * 0.62f, StrokeCap.Round)
                drawLine(ink, Offset(cx - hr * 1.2f, cy + hr * 1.6f), Offset(cx - hr * 1.4f, cy + hr * 3f), hr * 0.62f, StrokeCap.Round)
                drawCircle(ink, hr, Offset(cx, cy))
                val body = Path().apply {
                    moveTo(cx - hr * 1.9f, size.height)
                    lineTo(cx - hr * 1.8f, cy + hr * 2f)
                    quadraticBezierTo(cx - hr * 1.5f, cy + hr * 1.05f, cx, cy + hr * 0.9f)
                    quadraticBezierTo(cx + hr * 1.5f, cy + hr * 1.05f, cx + hr * 1.8f, cy + hr * 2f)
                    lineTo(cx + hr * 1.9f, size.height)
                    close()
                }
                drawPath(body, ink)
                drawArc(ink, 190f, 160f, false, Offset(cx - hr * 1.14f, cy - hr * 1.14f), Size(hr * 2.28f, hr * 2.28f), style = Stroke(hr * 0.2f))
                drawOval(ink, Offset(cx - hr * 1.3f, cy - hr * 0.42f), Size(hr * 0.46f, hr * 0.84f))
                drawOval(ink, Offset(cx + hr * 0.84f, cy - hr * 0.42f), Size(hr * 0.46f, hr * 0.84f))
            }
        }

        private fun bake(bw: Float, bh: Float, block: DrawScope.() -> Unit): ImageBitmap =
            ImageBitmap(bw.roundToInt().coerceAtLeast(1), bh.roundToInt().coerceAtLeast(1)).also { img ->
                CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat()), block)
            }
    }

    private val DrawScope.density get() = drawContext.density.density

    /** White cut-outs of a person, tinted when drawn: three builds, with no, one or both hands up. */
    private class PersonSprites {
        val body = Array(3) { style -> Array(3) { arms -> sprite(style, arms, false) } }
        val rim = Array(3) { style -> Array(3) { arms -> sprite(style, arms, true) } }

        private fun sprite(style: Int, arms: Int, rimOnly: Boolean): ImageBitmap {
            val r = SPRITE_R
            val img = ImageBitmap((WIDTH * r).roundToInt(), (HEIGHT * r).roundToInt())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
                val x = ORIGIN_X * r
                val y = ORIGIN_Y * r
                val white = Color.White
                if (rimOnly) {
                    val line = Stroke(r * 0.12f, cap = StrokeCap.Round)
                    drawArc(white, 200f, 140f, false, Offset(x - r * 0.92f, y - r * 0.92f), Size(r * 1.84f, r * 1.84f), style = line)
                    drawPath(Path().apply { moveTo(x - r * 1.5f, y + r * 1.35f); quadraticBezierTo(x - r * 1.1f, y + r * 1.02f, x - r * 0.5f, y + r * 0.92f) }, white, style = line)
                    drawPath(Path().apply { moveTo(x + r * 1.5f, y + r * 1.35f); quadraticBezierTo(x + r * 1.1f, y + r * 1.02f, x + r * 0.5f, y + r * 0.92f) }, white, style = line)
                    return@draw
                }
                val arm = r * 0.62f
                if (arms >= 1) drawLine(white, Offset(x - r * 1.2f, y + r * 1.5f), Offset(x - r * 1.6f, y - r * 2.4f), arm, StrokeCap.Round)
                if (arms == 2) drawLine(white, Offset(x + r * 1.2f, y + r * 1.5f), Offset(x + r * 1.6f, y - r * 2.4f), arm, StrokeCap.Round)
                val body = Path().apply {
                    moveTo(x - r * 1.65f, size.height)
                    lineTo(x - r * 1.65f, y + r * 2f)
                    quadraticBezierTo(x - r * 1.5f, y + r * 1.05f, x - r * 0.4f, y + r * 0.85f)
                    lineTo(x + r * 0.4f, y + r * 0.85f)
                    quadraticBezierTo(x + r * 1.5f, y + r * 1.05f, x + r * 1.65f, y + r * 2f)
                    lineTo(x + r * 1.65f, size.height)
                    close()
                }
                drawPath(body, white)
                drawCircle(white, r * 0.92f, Offset(x, y))
                if (style == 1) drawOval(white, Offset(x + r * 0.43f, y - r * 0.05f), Size(r * 0.64f, r * 1.9f))
                if (style == 2) drawOval(white, Offset(x - r * 1.2f, y - r * 0.8f), Size(r * 2.4f, r * 0.6f))
            }
            return img
        }

        companion object {
            const val SPRITE_R = 48f
            const val WIDTH = 5.2f
            const val HEIGHT = 13f
            const val ORIGIN_X = 2.6f
            const val ORIGIN_Y = 4f
        }
    }

    private companion object {
        val ON_TV = com.autodj.BuildConfig.FLAVOR == "tv"
        const val LED_W = 64
        const val LED_H = 26
        const val WING_STEPS = 8
        const val BEAM_W = 64
        const val BEAM_H = 256
        const val GLOW = 128
        val BUILDS = setOf(FxType.RISER, FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL)
        val IMPACTS = setOf(FxType.IMPACT, FxType.SUB_DROP, FxType.HORN)
        val SHADES = floatArrayOf(0.84f, 0.92f, 0.975f)
        val ROOM_DARK = Color(0xFF040407)
        val PHONE = Color(0xF2EBF0FF)
        val WARM = ColorFilter.tint(Color(0xFFFFE2AA), BlendMode.SrcIn)
        val CONFETTI = listOf(Color(0xFFFF7A45), Color(0xFFFF3D8B), Color(0xFF5AA8FF), Color(0xFFF2C14E), Color.White, Color(0xFF3DFFC2))

        /** A soft white beam, narrow at the top and fading out along its length. */
        fun beamBitmap(): Bitmap {
            val px = IntArray(BEAM_W * BEAM_H)
            val mid = (BEAM_W - 1) / 2f
            for (y in 0 until BEAM_H) {
                val along = y / (BEAM_H - 1f)
                val halfW = max(0.8f, along * mid)
                val fade = (1 - along).pow(1.4f)
                for (x in 0 until BEAM_W) {
                    val d = (abs(x - mid) / halfW).coerceAtMost(1f)
                    val core = 0.55f * exp(-d * d * 18f) + 0.45f * (1 - d) * (1 - d)
                    px[y * BEAM_W + x] = ((core * fade * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
                }
            }
            return Bitmap.createBitmap(px, BEAM_W, BEAM_H, Bitmap.Config.ARGB_8888)
        }

        fun glowBitmap(): Bitmap {
            val px = IntArray(GLOW * GLOW)
            val mid = (GLOW - 1) / 2f
            for (y in 0 until GLOW) for (x in 0 until GLOW) {
                val d = (hypot(x - mid, y - mid) / mid).coerceAtMost(1f)
                val a = (1 - d) * (1 - d)
                px[y * GLOW + x] = ((a * 255).toInt() shl 24) or 0xFFFFFF
            }
            return Bitmap.createBitmap(px, GLOW, GLOW, Bitmap.Config.ARGB_8888)
        }
    }
}
