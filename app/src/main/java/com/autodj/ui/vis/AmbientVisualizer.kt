package com.autodj.ui.vis

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autodj.dj.VisualFrame

/** The full-screen looks the user can pick between. */
enum class VisualizerStyle(val label: String, val blurb: String, /** For tight spaces: the look switcher. */ val short: String) {
    TUNNEL("Neon Tunnel", "Rings racing at the beat", "Tunnel"),
    LIQUID("Liquid Light", "Soft colour that breathes", "Liquid"),
    ACID("Acid Trip", "A breathing fractal that keeps unfolding", "Acid"),
    FIELD("LED Field", "A wall of pixels", "LED"),
    CLUB("Club", "Stage, LED wall and crowd", "Club"),
    DRIVE("Chill Drive", "A moonlit coast road, a lamp on every beat", "Drive"),
    LOFI("Lo-fi Vibe", "A rooftop city that bounces along", "Lo-fi"),
    BEAT_POP("Beat Pop", "Lyrics land on the beat", "Beat Pop"),
    RABBIT_HOLE("Rabbit Hole", "Falling down a breathing tunnel", "Rabbit"),
    ;

    /** Offered on the TV app: the shader looks can't hold 24 fps on a TV's graphics chip at full quality. */
    val onTv get() = this != ACID && this != RABBIT_HOLE

    /** Shows the song's words over the scene ([LyricOverlay]). */
    val lyrics get() = this == BEAT_POP

    fun scene(): Scene = when (this) {
        TUNNEL -> TunnelScene()
        LIQUID -> LiquidScene()
        // The trip looks run on the graphics chip from Android 13; before that, the earlier scenes.
        ACID -> if (Build.VERSION.SDK_INT >= 33) ShaderScene(ShaderScene.FRACTAL) else AcidScene()
        FIELD -> FieldScene()
        CLUB -> ClubScene()
        DRIVE -> DriveScene()
        LOFI -> LofiScene()
        BEAT_POP -> GlowScene()
        RABBIT_HOLE -> if (Build.VERSION.SDK_INT >= 33) ShaderScene(ShaderScene.TUNNEL) else TunnelScene()
    }
}

/**
 * The full-screen visualizer: the picked [style]'s scene, with the shared [EffectLayer] on top
 * (effect animations), all driven by the live mix each frame.
 */
@Composable
fun AmbientVisualizer(
    frame: () -> VisualFrame?,
    style: VisualizerStyle,
    modifier: Modifier = Modifier,
    /** Room the lyric looks leave at the bottom for an open panel. */
    lyricsClearBottom: Dp = 0.dp,
) {
    val state = remember { VisualState() }
    val layer = remember { EffectLayer() }
    val scene = remember(style) { style.scene() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scene) {
        while (true) withFrameNanos { now ->
            state.update(frame(), now)
            scene.step(state)
            layer.step(state)
            tick = now
        }
    }
    Box(modifier) {
        if (scene.lowRes) LowResScene(scene, state) { tick }
        Canvas(Modifier.fillMaxSize()) {
            tick // redraw every frame
            if (!scene.lowRes) scene.draw(this, state)
            // The mix's effect animations play only where the look has room for them (Club's LED
            // wall), drawn as if that were the whole screen; no other look shakes or flashes.
            scene.effectArea(size)?.let { r ->
                clipRect(r.left, r.top, r.right, r.bottom) {
                    withTransform({ translate(r.left, r.top) }) {
                        val full = drawContext.size
                        drawContext.size = r.size
                        layer.drawOverlays(this, state)
                        drawContext.size = full
                    }
                }
            }
            if (state.paused) drawRect(Color.Black.copy(alpha = 0.45f))
        }
        if (style.lyrics) LyricOverlay(state, tick, Modifier.fillMaxSize(), lyricsClearBottom)
    }
}

/** Pixels on the long side a [Scene.lowRes] look is drawn at before it's scaled up to the screen. */
private const val LOW_RES_LONG_SIDE = 560f

/**
 * A [Scene.lowRes] look: drawn into a small offscreen layer, then scaled up smoothly to fill the screen.
 */
@Composable
private fun LowResScene(scene: Scene, state: VisualState, tick: () -> Long) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0 || w == Constraints.Infinity || h == Constraints.Infinity) return@BoxWithConstraints
        val k = max(1f, max(w, h) / LOW_RES_LONG_SIDE)
        val sw = max(1, (w / k).roundToInt())
        val sh = max(1, (h / k).roundToInt())
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .layout { m, _ ->
                        val p = m.measure(Constraints.fixed(sw, sh))
                        layout(sw, sh) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        scaleX = w / sw.toFloat()
                        scaleY = h / sh.toFloat()
                        transformOrigin = TransformOrigin(0f, 0f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            ) {
                tick()
                scene.draw(this, state)
            }
        }
    }
}
