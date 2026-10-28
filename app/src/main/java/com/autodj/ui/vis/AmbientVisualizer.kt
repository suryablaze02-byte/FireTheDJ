package com.autodj.ui.vis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autodj.dj.VisualFrame

/** The full-screen looks the user can pick between. */
enum class VisualizerStyle(val label: String, val blurb: String, /** For tight spaces: the look switcher. */ val short: String) {
    HAZE("Haze & Beams", "Light through smoke", "Haze"),
    TUNNEL("Neon Tunnel", "Rings racing at the beat", "Tunnel"),
    LIQUID("Liquid Light", "Soft colour that breathes", "Liquid"),
    FIELD("LED Field", "A wall of pixels", "LED"),
    CLUB("Club", "Stage, LED wall and crowd", "Club"),
    BEAT_POP("Beat Pop", "Lyrics land on the beat", "Beat Pop"),
    KARAOKE("Karaoke", "Sing along, word by word", "Karaoke"),
    ;

    /** Shows the song's words over the scene ([LyricOverlay]). */
    val lyrics get() = this == BEAT_POP || this == KARAOKE

    fun scene(): Scene = when (this) {
        HAZE -> HazeScene()
        TUNNEL -> TunnelScene()
        LIQUID -> LiquidScene()
        FIELD -> FieldScene()
        CLUB -> ClubScene()
        BEAT_POP -> GlowScene(low = false)
        KARAOKE -> GlowScene(low = true)
    }
}

/**
 * The full-screen visualizer: the picked [style]'s scene, with the shared [EffectLayer] on top
 * (callouts, fire cannons, effect animations), all driven by the live mix each frame.
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
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
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
            // The lyric looks keep the screen for the words: effects flash and burn, but no shouted callouts.
            if (!style.lyrics) layer.drawCallout(this, state, measurer)
        }
        if (style.lyrics) LyricOverlay(style, state, tick, Modifier.fillMaxSize(), lyricsClearBottom)
    }
}
