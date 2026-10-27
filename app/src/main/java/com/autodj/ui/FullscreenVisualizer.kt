package com.autodj.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.autodj.ui.vis.AmbientVisualizer
import com.autodj.ui.vis.VisualizerStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.autodj.dj.EngineState
import com.autodj.dj.VisualFrame
import kotlinx.coroutines.delay

/**
 * The club: [ClubVisualizer] edge to edge, system bars hidden, screen kept on. A tap brings
 * up the track, what the AI is doing next, pause and close for a few seconds; Back closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenVisualizer(
    state: EngineState,
    artwork: Map<String, String>,
    visual: () -> VisualFrame?,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    style: VisualizerStyle = VisualizerStyle.CLUB,
    onStyle: (VisualizerStyle) -> Unit = {},
) {
    Immersive()
    // Turns with the phone while open; the rotate button holds it in landscape.
    var holdLandscape by rememberSaveable { mutableStateOf(false) }
    Orientation(if (holdLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
    BackHandler(onBack = onClose)
    val deck = state.current
    // Bumped on every tap; the controls hide again a few seconds after the last one.
    var touches by remember { mutableIntStateOf(1) }
    var shown by remember { mutableIntStateOf(1) }
    LaunchedEffect(touches) {
        shown = touches
        delay(CONTROLS_MS)
        shown = 0
    }
    val controls = shown != 0

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touches++ },
    ) {
        AmbientVisualizer(visual, style, Modifier.fillMaxSize())

        AnimatedVisibility(controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp)
                    .glass(RoundedCornerShape(24.dp), Glass.FillDark).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(deck?.title.orEmpty(), color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(deck?.artist?.takeIf { it.isNotBlank() }, deck?.let { "%.0f BPM".format(it.bpm) }, deck?.key, state.mode.label)
                    Text(sub.joinToString(" · "), color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.height(44.dp).glass(RoundedCornerShape(22.dp), Glass.FillStrong)
                        .clickable(role = Role.Button, onClickLabel = if (holdLandscape) "Turn with the phone" else "Hold landscape") { holdLandscape = !holdLandscape; touches++ }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (holdLandscape) "Auto-rotate" else "Landscape", color = Glass.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                GlassIconButton("Close full screen", fill = Glass.FillStrong, onClick = onClose) { CloseGlyph(Glass.Text) }
            }
        }

        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp)
                    .widthIn(max = 520.dp)
                    .glass(RoundedCornerShape(30.dp), Glass.FillDark).padding(horizontal = 14.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Pick a look; the choice is remembered.
                GlassSegmented(
                    VisualizerStyle.entries, style, { it.short }, { onStyle(it); touches++ },
                    Modifier.fillMaxWidth(), height = 44.dp, fontSize = 12.sp,
                    selectedFill = Color.White, selectedText = Color(0xFF120A10),
                )
                val next = state.next
                val status = when {
                    state.mixing -> "Mixing into ${next?.title ?: "the next track"} · ${state.recipe.orEmpty()}"
                    state.trickNow != null -> "Now: ${state.trickNow}"
                    next != null && state.secondsToMix != null -> "Next: ${next.title} in ${formatTime(state.secondsToMix)}"
                    else -> null
                }
                if (status != null) {
                    Text(
                        status, color = if (state.mixing) Dj.BlueText else Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundButton(68.dp, Color.White, if (state.paused) "Resume the set" else "Pause the set", onToggle) {
                    if (state.paused) PlayIcon(Color(0xFF120A10)) else PauseIcon(Color(0xFF120A10))
                }
            }
        }
    }
}

/** Hides the status and navigation bars and keeps the screen on while shown. */
@Composable
private fun Immersive() {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

@Composable
private fun RoundButton(size: Dp, fill: Color, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).background(fill, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CloseGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = 2.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
    }
}

/** Equalizer bars of different heights: "visualizer". */
@Composable
fun EqualizerGlyph(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val bar = w * 0.16f
        val heights = floatArrayOf(0.45f, 0.85f, 0.6f, 1f)
        for (i in heights.indices) {
            val x = w * (0.11f + i * 0.26f)
            drawLine(color, Offset(x, w), Offset(x, w * (1 - heights[i])), bar, StrokeCap.Round)
        }
    }
}

private const val CONTROLS_MS = 3500L

/** Sets the activity's orientation while shown, and puts the old one back after. */
@Composable
private fun Orientation(orientation: Int) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val before = activity.requestedOrientation
        onDispose { activity.requestedOrientation = before }
    }
    LaunchedEffect(orientation) { activity.requestedOrientation = orientation }
}
