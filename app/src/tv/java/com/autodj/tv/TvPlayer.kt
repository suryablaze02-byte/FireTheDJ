package com.autodj.tv

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.ui.Dj
import com.autodj.ui.DjViewModel
import com.autodj.ui.PauseIcon
import com.autodj.ui.PlayIcon
import com.autodj.ui.StopIcon
import com.autodj.ui.vis.AmbientVisualizer
import com.autodj.ui.vis.VisualizerStyle
import kotlinx.coroutines.delay

/** How long each visualizer runs before the next one fades in. */
private const val ROTATE_MS = 30_000L
/** How long the track card stays up after a new track starts or the look changes. */
private const val INFO_MS = 7_000L
/** Controls hide after this long without a key press. */
private const val CONTROLS_MS = 6_000L

/**
 * The mix on the big screen: the visualizers full screen, rotating through all five with a
 * cross-fade, the playing track in the corner whenever it changes. Left/right on the remote
 * switch looks by hand, OK opens pause/stop/home, Back goes home and the mix plays on.
 */
@UnstableApi
@Composable
fun TvPlayer(vm: DjViewModel, onHome: () -> Unit) {
    val state by vm.engine.state.collectAsStateWithLifecycle()
    val styles = VisualizerStyle.entries
    var index by remember { mutableIntStateOf(styles.indexOf(vm.visualizerStyle.value).coerceAtLeast(0)) }
    // Bumped by a manual change, so the 30 s timer restarts from it.
    var manual by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(false) }
    var infoUntil by remember { mutableStateOf(0L) }
    // When the current look started, for the "next look in m:ss" line.
    var lookSince by remember { mutableStateOf(System.currentTimeMillis()) }
    // Bumped by any key while the controls are up; they hide after a few idle seconds.
    var touched by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    // The key that brought the controls up: its release must not also press Play/Pause, now focused.
    var opener by remember { mutableStateOf<Key?>(null) }

    KeepScreenOn()
    BackHandler { if (controls) controls = false else onHome() }

    LaunchedEffect(manual) {
        lookSince = System.currentTimeMillis()
        while (true) {
            delay(ROTATE_MS)
            index = (index + 1) % styles.size
            lookSince = System.currentTimeMillis()
            infoUntil = System.currentTimeMillis() + INFO_MS
        }
    }
    LaunchedEffect(controls, touched) {
        if (!controls) return@LaunchedEffect
        delay(CONTROLS_MS)
        controls = false
    }
    LaunchedEffect(state.current?.uri) { infoUntil = System.currentTimeMillis() + INFO_MS }
    // The card hides by itself; this just re-checks the clock while it's up.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(infoUntil, controls) { while (controls || System.currentTimeMillis() < infoUntil) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    LaunchedEffect(controls) { runCatching { if (controls) pauseFocus.requestFocus() else rootFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == opener) { opener = null; return@onPreviewKeyEvent true }
                if (controls) { touched++; return@onPreviewKeyEvent false }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { index = (index + 1) % styles.size; manual++; infoUntil = System.currentTimeMillis() + INFO_MS; true }
                    Key.DirectionLeft -> { index = (index - 1 + styles.size) % styles.size; manual++; infoUntil = System.currentTimeMillis() + INFO_MS; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionDown, Key.DirectionUp -> { opener = e.key; controls = true; true }
                    else -> false
                }
            },
    ) {
        Crossfade(targetState = styles[index], animationSpec = tween(1600), label = "look") { style ->
            AmbientVisualizer(vm.engine::visual, style, Modifier.fillMaxSize())
        }

        AnimatedVisibility(now < infoUntil || controls || state.paused, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            val deck = state.current
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .padding(horizontal = 56.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.paused) Text("Paused", color = Dj.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(deck?.title.orEmpty(), color = Dj.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                val bits = listOfNotNull(deck?.artist?.takeIf { it.isNotBlank() }, deck?.let { "%.0f BPM".format(it.bpm) }, deck?.key, styles[index].label)
                Text(bits.joinToString(" · "), color = Dj.Muted, fontSize = 20.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                state.next?.let { Text("Up next · ${it.title}", color = Dj.BlueText, fontSize = 18.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
            }
        }

        // The next look, as information only: at the top while the track card or controls are up.
        AnimatedVisibility(now < infoUntil || controls, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            val left = ((ROTATE_MS - (now - lookSince)) / 1000).coerceAtLeast(0)
            Text(
                "Next look · ${styles[(index + 1) % styles.size].label} · in ${left / 60}:${"%02d".format(left % 60)}",
                color = Dj.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 28.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }

        // Play/pause in the middle, stop beside it.
        AnimatedVisibility(controls, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                // Home on the left, play/pause in the middle, stop on the right.
                Box(contentAlignment = Alignment.Center) {
                    TvRoundButton(
                        76.dp, Color.Black.copy(alpha = .6f), "Home, the music keeps playing",
                        Modifier.offset(x = (-150).dp), onClick = { controls = false; onHome() },
                    ) { HomeGlyph(Dj.Text, Modifier.size(34.dp)) }
                    TvRoundButton(
                        124.dp, Dj.Accent, if (state.paused) "Play" else "Pause",
                        Modifier.focusRequester(pauseFocus), onClick = { vm.engine.togglePause() },
                    ) {
                        if (state.paused) PlayIcon(Dj.Bg, Modifier.size(56.dp)) else PauseIcon(Dj.Bg, Modifier.size(56.dp))
                    }
                    TvRoundButton(
                        76.dp, Color.Black.copy(alpha = .6f), "Stop the mix",
                        Modifier.offset(x = 150.dp), onClick = { controls = false; vm.engine.stop() },
                    ) { StopIcon(Dj.Text, Modifier.size(34.dp)) }
                }
                Text("◀ ▶  change look", color = Dj.Muted, fontSize = 15.sp,
                    modifier = Modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

/** A house: roof, walls and a door. */
@Composable
private fun HomeGlyph(color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w * .11f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val house = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .1f, w * .48f); lineTo(w * .5f, w * .12f); lineTo(w * .9f, w * .48f)
            moveTo(w * .22f, w * .4f); lineTo(w * .22f, w * .88f); lineTo(w * .78f, w * .88f); lineTo(w * .78f, w * .4f)
            moveTo(w * .42f, w * .88f); lineTo(w * .42f, w * .64f); lineTo(w * .58f, w * .64f); lineTo(w * .58f, w * .88f)
        }
        drawPath(house, color, style = stroke)
    }
}

/** No screensaver while the visualizer is up. */
@Composable
private fun KeepScreenOn() {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
