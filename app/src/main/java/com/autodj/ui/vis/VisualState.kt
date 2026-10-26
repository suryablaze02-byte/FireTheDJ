package com.autodj.ui.vis

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.autodj.dj.ActiveFx
import com.autodj.dj.DeckVisual
import com.autodj.dj.SpectrumTap
import com.autodj.dj.VisualFrame
import com.autodj.dj.fx.FxType
import com.autodj.ui.Dj
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One full-screen look. Every scene shares the same [EffectLayer] for effects and callouts. */
interface Scene {
    /** Once per frame: move particles, react to effects. */
    fun step(s: VisualState) {}

    /** Draws the whole scene (opaque). May be called more than once a frame (echo trails), so it must not change state. */
    fun draw(ds: DrawScope, s: VisualState)
}

/**
 * The live mix as the visualizers see it, updated once per frame from [VisualFrame]s:
 * smoothed spectrum per deck and combined, crossfade gains, beat and phrase, kick and energy,
 * and the effects sounding right now (with which deck they're on).
 */
class VisualState {
    val n = SpectrumTap.BANDS

    /** Smoothed band levels per deck slot (0 = the set's deck, 1 = the incoming deck), scaled by its gain. */
    val bands = Array(2) { FloatArray(n) }

    /** Both decks combined: what the room hears. */
    val mix = FloatArray(n)
    val gains = FloatArray(2)
    val present = BooleanArray(2)
    var fx: List<Pair<ActiveFx, Int>> = emptyList()
        private set
    var beat = 0f
        private set
    var barInPhrase = 0
        private set
    var paused = true
        private set
    var time = 0f
        private set
    var dt = 0.016f
        private set

    /** 0..1 smoothed loudness, kick pulse (one per beat) and low end. */
    var energy = 0f
        private set
    var kick = 0f
        private set
    var bass = 0f
        private set

    /** Low end averaged over the last few seconds: a drop is the bass coming back above it. */
    var slowBass = 0f
        private set

    private var lastNanos = 0L
    private var lastKickBeat = -1

    fun update(f: VisualFrame?, now: Long) {
        dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now
        time += dt
        paused = f?.paused ?: true
        val decks = arrayOfNulls<DeckVisual>(2)
        f?.decks?.forEach { decks[if (it.incoming) 1 else 0] = it }
        for (s in 0..1) {
            val d = decks[s]
            present[s] = d != null
            gains[s] += ((d?.gain ?: 0f) - gains[s]) * min(1f, dt * 10)
            for (b in 0 until n) {
                val target = if (d == null || paused) 0f else d.bands[b] * (0.3f + 0.7f * d.gain)
                val cur = bands[s][b]
                bands[s][b] = if (target > cur) cur + (target - cur) * min(1f, dt * 28) else cur + (target - cur) * min(1f, dt * 6)
            }
        }
        for (b in 0 until n) mix[b] = max(bands[0][b], bands[1][b])
        val lead = decks[0] ?: decks[1]
        if (lead != null && !paused) {
            beat = lead.beat
            barInPhrase = lead.barInPhrase
        }
        fx = (0..1).flatMap { s -> decks[s]?.fx.orEmpty().map { it to s } }
        energy += (mix.average().toFloat() - energy) * min(1f, dt * 4)
        bass = (0 until 5).sumOf { mix[it].toDouble() }.toFloat() / 5
        slowBass += (bass - slowBass) * min(1f, dt / 4f)
        val beatNo = floor(beat).toInt()
        if (!paused && beatNo != lastKickBeat) {
            lastKickBeat = beatNo
            kick = max(kick, 0.35f + 0.65f * bass)
        }
        kick = max(0f, kick - dt * 4f)
    }

    fun active(type: FxType) = fx.firstOrNull { it.first.type == type }

    /** The room's two colours: they change every 8 bars, leaning blue while the incoming deck is louder. */
    val palette: Pair<Color, Color>
        get() {
            val (a, b) = PALETTE[floor(beat / 32f).toInt().mod(PALETTE.size)]
            val lean = if (present[1]) (gains[1] - gains[0]).coerceIn(0f, 1f) * 0.5f else 0f
            return lerp(a, Dj.Blue, lean) to lerp(b, Dj.Blue, lean * 0.6f)
        }

    fun deckColor(slot: Int) = if (slot == 0) Dj.Accent else Dj.Blue

    fun frac(x: Float) = x - floor(x)

    companion object {
        val PALETTE = listOf(
            Color(0xFFFF7A45) to Color(0xFFFF3D8B),
            Color(0xFFB45AFF) to Color(0xFF5AA8FF),
            Color(0xFFFF3D8B) to Color(0xFFFFC23D),
            Color(0xFF3DFFC2) to Color(0xFF5AA8FF),
        )
    }
}
