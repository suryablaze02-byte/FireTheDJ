package com.autodj.ui.vis

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.rememberTextMeasurer
import com.autodj.dj.VisualFrame

/** The full-screen looks the user can pick between. */
enum class VisualizerStyle(val label: String, val blurb: String, /** For tight spaces: the look switcher. */ val short: String) {
    HAZE("Haze & Beams", "Light through smoke", "Haze"),
    TUNNEL("Neon Tunnel", "Rings racing at the beat", "Tunnel"),
    LIQUID("Liquid Light", "Soft colour that breathes", "Liquid"),
    FIELD("LED Field", "A wall of pixels", "LED"),
    CLUB("Club", "Stage, LED wall and crowd", "Club"),
    ;

    fun scene(): Scene = when (this) {
        HAZE -> HazeScene()
        TUNNEL -> TunnelScene()
        LIQUID -> LiquidScene()
        FIELD -> FieldScene()
        CLUB -> ClubScene()
    }
}

/**
 * The full-screen visualizer: the picked [style]'s scene, with the shared [EffectLayer] on top
 * (callouts, fire cannons, effect animations), all driven by the live mix each frame.
 */
@Composable
fun AmbientVisualizer(frame: () -> VisualFrame?, style: VisualizerStyle, modifier: Modifier = Modifier) {
    val state = remember { VisualState() }
    val layer = remember { EffectLayer() }
    val scene = remember(style) { style.scene() }
    val measurer = rememberTextMeasurer()
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scene) {
        while (true) withFrameNanos { now ->
            state.update(frame(), now)
            scene.step(state)
            layer.step(state)
            tick = now
        }
    }
    Canvas(modifier) {
        tick // redraw every frame
        val (sx, sy, rot) = layer.motion(state)
        withTransform({
            translate(sx, sy)
            rotate(rot, center)
        }) {
            scene.draw(this, state)
            // Echo: the scene's afterimages, zoomed out from the centre and added on top.
            for ((scale, alpha) in layer.echoCopies(state)) {
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { this.alpha = alpha; blendMode = BlendMode.Plus })
                withTransform({ scale(scale, scale, center) }) { scene.draw(this, state) }
                canvas.restore()
            }
        }
        layer.drawOverlays(this, state)
        layer.drawCallout(this, state, measurer)
    }
}
