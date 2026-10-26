package com.autodj.ui.vis

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.autodj.dj.fx.FxType
import com.autodj.ui.Dj
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * E: the club itself: a light truss, an LED wall showing the spectrum behind the DJ booth,
 * and a crowd that bounces with the kick and throws its hands up on big moments (with
 * confetti, sparks and CO2 plumes of its own). Lays out wider in landscape.
 */
class ClubScene : Scene {
    private val rnd = Random(11)
    private val seen = HashSet<FxType>()
    private var hype = 0f

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Color, val size: Float, val kind: Int)

    private val particles = ArrayList<Particle>()

    override fun step(s: VisualState) {
        val now = HashSet<FxType>()
        for ((a, _) in s.fx) {
            now += a.type
            val fresh = a.type !in seen
            when (a.type) {
                FxType.IMPACT, FxType.SUB_DROP -> if (fresh) { hype = 1f; co2(); confetti(40) }
                FxType.HORN -> if (fresh) { hype = max(hype, 0.8f); confetti(50) }
                FxType.CRASH -> if (fresh) sparks(30, Color.White)
                FxType.RISER, FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL -> {
                    hype = max(hype, a.progress * a.amount)
                    if (rnd.nextFloat() < s.dt * (10 + 50 * a.progress)) sparks(1, Color(0xFFFFC27A))
                }
                else -> Unit
            }
        }
        seen.clear()
        seen += now
        hype = max(0f, hype - s.dt * 0.35f)
        val dt = if (s.paused) 0f else s.dt
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            when (p.kind) {
                0 -> { p.vy += 0.25f * dt; p.vx *= 0.99f }
                2 -> { p.vx *= 0.96f; p.vy *= 0.96f }
                else -> Unit
            }
        }
    }

    private fun confetti(count: Int) = repeat(count) {
        particles += Particle(rnd.nextFloat(), -0.05f, (rnd.nextFloat() - 0.5f) * 0.15f, 0.08f + 0.15f * rnd.nextFloat(), 3.5f, 3.5f, CONFETTI[rnd.nextInt(CONFETTI.size)], 5f + 5f * rnd.nextFloat(), 0)
    }

    private fun sparks(count: Int, color: Color) = repeat(count) {
        particles += Particle(0.2f + 0.6f * rnd.nextFloat(), 0.62f, (rnd.nextFloat() - 0.5f) * 0.1f, -(0.3f + 0.5f * rnd.nextFloat()), 1.4f, 1.4f, color, 2.5f + 2.5f * rnd.nextFloat(), 1)
    }

    private fun co2() {
        for (side in listOf(0.18f, 0.82f)) repeat(40) {
            particles += Particle(side, 0.62f, (rnd.nextFloat() - 0.5f) * 0.25f, -(0.6f + 0.6f * rnd.nextFloat()), 1.6f, 1.6f, Color.White, 18f + 20f * rnd.nextFloat(), 2)
        }
    }

    override fun draw(ds: DrawScope, s: VisualState) = with(ds) {
        val w = size.width
        val h = size.height
        val wide = w > h
        val pi = PI.toFloat()
        val (phrase, _) = s.palette
        val deckTint = if (s.present[1] && s.gains[1] > s.gains[0]) Dj.Blue else Dj.Accent
        val main = lerp(phrase, deckTint, 0.35f)
        val roll = s.active(FxType.LOOP_ROLL)?.first

        drawRect(Brush.verticalGradient(listOf(Color(0xFF05050A), Color(0xFF0B0814), Color(0xFF07060B))))
        val glowR = h * 0.6f
        drawCircle(Brush.radialGradient(listOf(main.copy(alpha = 0.25f + 0.35f * s.energy + 0.3f * s.kick), Color.Transparent), Offset(w / 2, h * 0.42f), glowR), glowR, Offset(w / 2, h * 0.42f))
        for (k in 0 until 5) {
            val fx0 = w * (0.1f + 0.8f * s.frac(0.13f * s.time * (1 + k * 0.3f) + k * 0.21f))
            val fy = h * (0.35f + 0.08f * k)
            val r = max(w, h) * 0.45f
            drawCircle(Brush.radialGradient(listOf(main.copy(alpha = 0.06f + 0.1f * hype), Color.Transparent), Offset(fx0, fy), r), r, Offset(fx0, fy))
        }

        // LED wall: the live spectrum as lit LED columns (lows in the middle), both decks.
        val wallTop = h * (if (wide) 0.12f else 0.16f)
        val wallBottom = h * (if (wide) 0.52f else 0.5f)
        val wallW = min(w * (if (wide) 0.62f else 0.9f), (wallBottom - wallTop) * 2.4f)
        val wallLeft = (w - wallW) / 2
        drawRoundRect(Color(0xFF0A0A10), Offset(wallLeft - 6f, wallTop - 6f), Size(wallW + 12f, wallBottom - wallTop + 12f), CornerRadius(10f))
        val cols = 24
        val rows = 14
        val cw = wallW / cols
        val rh = (wallBottom - wallTop) / rows
        val inset = cw * 0.14f
        for (c in 0 until cols) {
            val mid = (cols - 1) / 2f
            val band = ((abs(c - mid) / (mid + 0.5f)) * (s.n - 1)).toInt().coerceIn(0, s.n - 1)
            for (slot in 0..1) {
                if (!s.present[slot] && s.bands[slot][band] < 0.02f) continue
                val lit = (s.bands[slot][band] * rows).toInt()
                val col = if (slot == 0) main else lerp(Dj.Blue, main, 0.2f)
                for (r in 0 until rows) {
                    val on = r < lit
                    val alpha = (if (on) 0.95f else 0.06f) * (if (slot == 1) s.gains[1].coerceAtLeast(0.15f) else 1f)
                    drawRect(
                        (if (on && r >= rows - 2) Color.White else col).copy(alpha = alpha),
                        Offset(wallLeft + c * cw + inset, wallBottom - (r + 1) * rh + inset), Size(cw - 2 * inset, rh - 2 * inset),
                        blendMode = if (slot == 1) BlendMode.Plus else BlendMode.SrcOver,
                    )
                }
            }
        }

        // Truss with moving heads: they sweep on the beat, tilt up through builds, lock during loop rolls.
        val trussY = h * 0.07f
        drawLine(Color(0xFF2A2A32), Offset(0f, trussY), Offset(w, trussY), 6f)
        val heads = if (wide) 9 else 6
        val build = s.fx.filter { it.first.type in BUILDS }.maxOfOrNull { it.first.progress * it.first.amount } ?: 0f
        val fall = s.active(FxType.DOWNLIFTER)?.first?.progress ?: 0f
        for (i in 0 until heads) {
            val x = w * (i + 0.5f) / heads
            val dir = if (i % 2 == 0) 1f else -1f
            val sweep = if (roll != null) 0.25f * dir else 0.45f * sin(pi * s.beat / 2f + i * 0.9f) * dir
            val aim = sweep * (1 - build) + dir * build * 1.1f + fall * dir * 0.2f
            val len = h * (1.05f - 0.45f * build)
            val col = if (i % 3 == 1 && s.present[1]) lerp(Dj.Blue, main, 0.3f) else main
            val a = ((0.18f + 0.5f * s.energy + 0.5f * s.kick + 0.4f * build) * (1 - 0.6f * fall)).coerceIn(0f, 0.85f)
            beam(Offset(x, trussY + 8f), aim, 0.12f, len, col, a)
            drawCircle(lerp(col, Color.White, 0.6f).copy(alpha = min(1f, 0.4f + a)), 7f, Offset(x, trussY + 8f))
        }

        // Booth: the DJ behind the desk; its deck lights follow the crossfade.
        val boothTop = h * (if (wide) 0.54f else 0.54f)
        val boothH = h * (if (wide) 0.1f else 0.08f)
        val boothW = min(w * 0.6f, wallW * 0.7f)
        val boothLeft = (w - boothW) / 2
        val bob = 4f * s.kick
        drawCircle(Color.Black, boothH * 0.32f, Offset(w / 2, boothTop - boothH * 0.55f + bob))
        drawRoundRect(Color.Black, Offset(w / 2 - boothH * 0.6f, boothTop - boothH * 0.25f + bob), Size(boothH * 1.2f, boothH * 0.6f), CornerRadius(boothH * 0.3f))
        drawRoundRect(Color(0xFF111117), Offset(boothLeft, boothTop), Size(boothW, boothH), CornerRadius(8f))
        drawLine(main.copy(alpha = 0.8f), Offset(boothLeft, boothTop), Offset(boothLeft + boothW, boothTop), 3f)
        for (slot in 0..1) {
            val cx = boothLeft + boothW * (if (slot == 0) 0.25f else 0.75f)
            val g = if (s.present[slot]) s.gains[slot] else 0f
            drawCircle(s.deckColor(slot).copy(alpha = 0.15f + 0.85f * g), boothH * 0.28f, Offset(cx, boothTop + boothH / 2), style = Stroke(3f))
            drawCircle(s.deckColor(slot).copy(alpha = 0.5f * g), boothH * 0.12f, Offset(cx, boothTop + boothH / 2))
        }

        // Dance floor and crowd: rows fill the width in either orientation.
        val floorTop = boothTop + boothH
        drawRect(Brush.verticalGradient(listOf(Color(0xCC09080E), Color(0x99050508)), floorTop, h), Offset(0f, floorTop), Size(w, h - floorTop))
        for (i in 0 until 3) {
            val px = w * (0.2f + 0.3f * i + 0.08f * sin(pi * s.beat / 4f + i))
            val py = h * 0.8f
            drawOval(Brush.radialGradient(listOf(main.copy(alpha = 0.18f + 0.3f * s.kick), Color.Transparent), Offset(px, py), w * 0.3f), Offset(px - w * 0.3f, py - h * 0.05f), Size(w * 0.6f, h * 0.1f), blendMode = BlendMode.Plus)
        }
        for (back in listOf(true, false)) {
            val r = if (back) min(w * 0.026f, h * 0.035f) else min(w * 0.036f, h * 0.05f)
            val spacing = r * 3.3f
            val count = ceil(w / spacing).toInt()
            val baseY = h * (if (back) 0.76f else 0.9f)
            val shade = if (back) Color(0xFF121219) else Color(0xFF060609)
            for (k in 0 until count) {
                val seed = (k * 7919 + if (back) 0 else 104729) % 1000 / 1000f
                val x = spacing * (k + 0.5f + if (back) 0f else 0.3f)
                val bounce = (if (s.paused) 0f else abs(sin(pi * (s.beat + seed * 0.3f)))) * r * (0.4f + 1.4f * s.energy)
                val y = baseY - bounce
                if (hype > 0.35f || (seed > 0.6f && s.energy > 0.55f)) {
                    val lift = r * (2.2f + 0.6f * sin(s.time * 6 + seed * 6))
                    drawLine(shade, Offset(x - r * 0.7f, y + r), Offset(x - r * 1.1f, y - lift), r * 0.35f, StrokeCap.Round)
                    drawLine(shade, Offset(x + r * 0.7f, y + r), Offset(x + r * 1.1f, y - lift), r * 0.35f, StrokeCap.Round)
                }
                drawRoundRect(shade, Offset(x - r * 1.2f, y + r * 0.8f), Size(r * 2.4f, h - y), CornerRadius(r))
                drawCircle(shade, r, Offset(x, y))
                drawCircle(lerp(main, Color.White, 0.3f).copy(alpha = 0.35f + 0.4f * s.kick), r, Offset(x, y - 1f), style = Stroke(2f))
            }
        }

        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val pos = Offset(p.x * w, p.y * h)
            when (p.kind) {
                0 -> drawRect(p.color.copy(alpha = a), pos, Size(p.size, p.size * 0.6f))
                2 -> drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * a), Color.Transparent), pos, p.size * 3), p.size * 3, pos)
                else -> drawCircle(p.color.copy(alpha = a), p.size, pos, blendMode = BlendMode.Plus)
            }
        }
    }

    private companion object {
        val BUILDS = setOf(FxType.RISER, FxType.SNARE_ROLL, FxType.REVERSE_CYMBAL)
        val CONFETTI = listOf(Color(0xFFFF7A45), Color(0xFFFF3D8B), Color(0xFF5AA8FF), Color(0xFFF2C14E), Color.White, Color(0xFF3DFFC2))
    }
}
