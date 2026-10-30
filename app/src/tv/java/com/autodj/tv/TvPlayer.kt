package com.autodj.tv

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateDpAsState
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.autodj.dj.EngineState
import com.autodj.ui.Artwork
import com.autodj.ui.DjViewModel
import com.autodj.ui.Glass
import com.autodj.ui.formatTime
import com.autodj.ui.glass
import com.autodj.ui.PauseIcon
import com.autodj.ui.PlayIcon
import com.autodj.ui.SkipIcon
import com.autodj.ui.StopIcon
import com.autodj.ui.vis.AmbientVisualizer
import com.autodj.ui.vis.VisualizerStyle
import kotlinx.coroutines.delay

/** How long the track card stays up after a new track starts or the look changes. */
private const val INFO_MS = 7_000L
/** Controls hide after this long without a key press. */
private const val CONTROLS_MS = 6_000L

/**
 * The mix on the big screen: the look the viewer picked, full screen, and the playing track in
 * the corner whenever it changes. The look stays until the viewer changes it: left/right on the
 * remote, or the Look button in the controls (it is remembered, like on the phone). OK opens
 * the controls, Back returns to the decks and the mix plays on.
 */
@UnstableApi
@Composable
fun TvPlayer(vm: DjViewModel, onHome: () -> Unit, onBack: () -> Unit) {
    val state by vm.engine.state.collectAsStateWithLifecycle()
    val artwork by vm.artwork.collectAsStateWithLifecycle()
    val styles = VisualizerStyle.entries
    val style by vm.visualizerStyle.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(false) }
    var infoUntil by remember { mutableStateOf(0L) }
    // Bumped by any key while the controls are up; they hide after a few idle seconds.
    var touched by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    // Heights of the panels at the bottom, so the lyric looks can rise above the open one.
    var controlsPx by remember { mutableIntStateOf(0) }
    var cardPx by remember { mutableIntStateOf(0) }
    // The key that brought the controls up: its release must not also press Play/Pause, now focused.
    var opener by remember { mutableStateOf<Key?>(null) }

    KeepScreenOn()
    BackHandler { if (controls) controls = false else onBack() }

    /** Steps to the next (or previous) look and keeps it, showing its name for a moment. */
    fun changeLook(step: Int) {
        val i = styles.indexOf(style).coerceAtLeast(0)
        vm.setVisualizerStyle(styles[Math.floorMod(i + step, styles.size)])
        infoUntil = System.currentTimeMillis() + INFO_MS
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

    val cardUp = (now < infoUntil || state.paused) && !controls
    val clearPx = if (controls) controlsPx else if (cardUp) cardPx else 0
    val clear by animateDpAsState(with(LocalDensity.current) { clearPx.toDp() }, label = "lyrics clear")

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == opener) { opener = null; return@onPreviewKeyEvent true }
                if (controls) { touched++; return@onPreviewKeyEvent false }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { changeLook(1); true }
                    Key.DirectionLeft -> { changeLook(-1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionDown, Key.DirectionUp -> { opener = e.key; controls = true; true }
                    else -> false
                }
            },
    ) {
        Crossfade(targetState = style, animationSpec = tween(900), label = "look") { look ->
            AmbientVisualizer(vm.engine::visual, look, Modifier.fillMaxSize(), lyricsClearBottom = clear)
        }

        val deck = state.current
        val art = deck?.let { artwork[it.uri] }

        // The track card: on its own when a track or look changes, or while paused.
        AnimatedVisibility((now < infoUntil || state.paused) && !controls, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.onSizeChanged { cardPx = it.height }.padding(start = 40.dp, bottom = 36.dp).widthIn(max = 700.dp)
                    .glass(RoundedCornerShape(30.dp), Glass.FillDark).padding(start = 16.dp, end = 28.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (deck != null) Artwork(art, deck.uri, deck.title, Modifier.size(80.dp).border(2.dp, Color.White.copy(alpha = .35f), CircleShape), CircleShape)
                TrackLines(state, style.label)
            }
        }

        // The look on screen and how to change it: at the top while the track card or controls are up.
        AnimatedVisibility(now < infoUntil || controls, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Text(
                "Look · ${style.label}   ◀ ▶ to change",
                color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 28.dp, end = 40.dp)
                    .glass(RoundedCornerShape(999.dp), Glass.FillDark)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        // The controls: one glass bar along the bottom, as on Apple TV. Home, stop, then play/pause (focused first).
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.onSizeChanged { controlsPx = it.height }.padding(horizontal = 40.dp, vertical = 30.dp).fillMaxWidth()
                    .glass(RoundedCornerShape(32.dp), Glass.FillDark).padding(horizontal = 32.dp, vertical = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (deck != null) Artwork(art, deck.uri, deck.title, Modifier.size(76.dp).border(2.dp, Color.White.copy(alpha = .35f), CircleShape), CircleShape)
                    TrackLines(state, style.label, Modifier.weight(1f), showNext = false)
                    state.next?.let { next ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                state.secondsToMix?.let { "UP NEXT · IN ${formatTime(it)}" } ?: "UP NEXT",
                                color = Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                            )
                            Text(next.title, color = Glass.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 360.dp))
                        }
                    }
                }
                // Where the set is in this track.
                val progress = if (deck != null && deck.durationSec > 0) (deck.positionSec / deck.durationSec).toFloat().coerceIn(0f, 1f) else 0f
                Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Glass.FillSelected)) {
                    Box(Modifier.fillMaxWidth(progress).height(6.dp).background(Color.White, CircleShape))
                }
                // The playback buttons centred with "AI in control" under them, the look on its own at the right.
                Box(Modifier.padding(top = 10.dp).fillMaxWidth()) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvRoundButton(60.dp, Glass.FillStrong, "Home, the music keeps playing", onClick = { controls = false; onHome() }) {
                                HomeGlyph(Glass.Text, Modifier.size(28.dp))
                            }
                            TvRoundButton(60.dp, Glass.FillStrong, "Stop the mix", onClick = { controls = false; vm.engine.stop() }) {
                                StopIcon(Glass.Text, Modifier.size(26.dp))
                            }
                            TvRoundButton(80.dp, Color.White, if (state.paused) "Play" else "Pause", Modifier.focusRequester(pauseFocus), onClick = { vm.engine.togglePause() }) {
                                if (state.paused) PlayIcon(INK, Modifier.size(38.dp)) else PauseIcon(INK, Modifier.size(38.dp))
                            }
                            // Skip: out at the next phrase, pressed again at the next bar.
                            val skipLabel = when (state.skipping) {
                                0 -> "Skip to the next song"
                                1 -> "Skipping at the next phrase · press again for the next bar"
                                else -> "Skipping at the next bar"
                            }
                            TvRoundButton(60.dp, if (state.skipping > 0) Glass.FillSelected else Glass.FillStrong, skipLabel, onClick = { vm.skip() }) {
                                SkipIcon(if (state.canSkip || state.skipping > 0) Glass.Text else Glass.Muted, Modifier.size(28.dp))
                            }
                        }
                        Text("AI in control", color = Glass.Muted, fontSize = 14.sp)
                    }
                    // While the controls are up ◀ ▶ move between buttons, so the look has its own button.
                    Box(Modifier.align(Alignment.CenterEnd)) { LookButton(style.label) { changeLook(1); touched++ } }
                }
            }
        }
    }
}

/** "Look · Beat Pop": OK steps to the next look. Lifted and ringed in white when focused, like the other buttons. */
@Composable
private fun LookButton(look: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focus")
    Box(
        Modifier.scale(scale)
            .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(999.dp))
            .padding(6.dp)
            .glass(RoundedCornerShape(999.dp), Glass.FillStrong)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Change the look", onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Look · $look", color = Glass.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Dark glyphs on the white play/pause button. */
private val INK = Color(0xFF120A10)

/** The playing track: paused flag, title, details (with the current look) and what's next. */
@Composable
private fun TrackLines(state: EngineState, look: String, modifier: Modifier = Modifier, showNext: Boolean = true) {
    val deck = state.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (state.paused) Text("Paused", color = Glass.Ember, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(deck?.title.orEmpty(), color = Glass.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        val bits = listOfNotNull(deck?.artist?.takeIf { it.isNotBlank() }, deck?.let { "%.0f BPM".format(it.bpm) }, deck?.key, look)
        Text(bits.joinToString(" · "), color = Glass.Muted, fontSize = 18.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (showNext) state.next?.let {
            Text("Up next · ${it.title}", color = Glass.EmberText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
internal fun KeepScreenOn() {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
