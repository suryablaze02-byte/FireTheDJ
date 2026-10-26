package com.autodj.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One stop on the tour: what to spotlight (a [tourTarget] key, or none for a centred card) and what to say. */
class TourStep(val target: String?, val title: String, val body: String)

/** Where the tour's targets are on screen, as they report themselves. */
class TourState {
    val targets = mutableStateMapOf<String, Rect>()
}

/** The running tour, if any; screens tag their parts with [tourTarget] through it. */
val LocalTour = compositionLocalOf<TourState?> { null }

/** Marks this element as the tour target [key]: its bounds are spotlighted at that step. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val tour = LocalTour.current ?: return this
    return onGloballyPositioned { tour.targets[key] = it.boundsInRoot() }
}

/** The phone app's first-launch tour, over the Library screen. */
val PHONE_TOUR = listOf(
    TourStep(null, "Welcome to FireTheDJ 🔥", "Your AI DJ. It picks every song, mixes on the beat and drops the effects, all on its own. Here's a 30-second tour."),
    TourStep("sources", "Add your music", "Pick a folder on this phone, browse YT Music's curated playlists, or paste a link. Each song is analysed once: tempo, key, drops and genre."),
    TourStep("crates", "Genre crates", "Songs sort themselves into crates by genre. Pick one to keep the party in one style, or All for a set that moves across genres."),
    TourStep("mode", "Smooth or Trickster", "Smooth blends long and clean. Trickster throws in scratches, rolls, echo throws and air horns."),
    TourStep("start", "Start the mix", "FireTheDJ takes it from here: it chooses each next track and plans every transition. Songs still being analysed join the mix as they're ready."),
    TourStep("cast", "Take it to the TV", "Cast the mix to a Chromecast or smart TV, and the visualizers fill the big screen."),
)

/** Whether the first-launch tour has been shown (or skipped) on this device, per app. */
object TourMemory {
    private const val PREFS = "tour"
    private const val DONE = "done_v1"
    fun done(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(DONE, false)
    fun markDone(context: Context) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(DONE, true).apply()
}

/**
 * The guided tour: the screen dims, the current step's target shows through a rounded hole with
 * an accent ring, and a card explains it, above or below the target. Next / Skip on the phone;
 * on a TV the remote stays inside the card (OK = next, Back = skip).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TourOverlay(state: TourState, steps: List<TourStep>, tv: Boolean, onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val last = index == steps.lastIndex
    val next: () -> Unit = { if (last) onDone() else index++ }
    val hole = step.target?.let { state.targets[it] }
    val density = LocalDensity.current
    val pad = with(density) { 10.dp.toPx() }
    val nextFocus = remember { FocusRequester() }
    BackHandler(onBack = onDone)
    LaunchedEffect(index) { runCatching { nextFocus.requestFocus() } }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            // Taps on the dimmed screen go nowhere; the tour is driven from its card.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                hole?.let { addRoundRect(RoundRect(it.inflate(pad), CornerRadius(pad * 1.6f))) }
            }
            drawPath(scrim, Color.Black.copy(alpha = .74f))
            hole?.let {
                val r = it.inflate(pad)
                drawRoundRect(Dj.Accent, r.topLeft, Size(r.width, r.height), CornerRadius(pad * 1.6f), style = Stroke(with(density) { 3.dp.toPx() }))
            }
        }

        // The card goes below the target if it's in the top half, above it otherwise.
        val placement = when {
            hole == null -> Modifier.align(Alignment.Center)
            hole.center.y < heightPx / 2 ->
                Modifier.align(Alignment.TopCenter).padding(top = with(density) { (hole.bottom + pad).toDp() } + 18.dp)
            else ->
                Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (heightPx - hole.top + pad).toDp() } + 18.dp)
        }
        Column(
            placement.padding(horizontal = 16.dp)
                .widthIn(max = if (tv) 560.dp else 380.dp)
                .background(Dj.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, Dj.Border, RoundedCornerShape(20.dp))
                .padding(if (tv) 28.dp else 20.dp)
                // On a TV the remote must not wander off into the dimmed screen behind.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${index + 1} of ${steps.size}", color = Dj.Accent, fontSize = if (tv) 15.sp else 12.sp, fontWeight = FontWeight.SemiBold)
            Text(step.title, color = Dj.Text, fontSize = if (tv) 26.sp else 19.sp, fontWeight = FontWeight.Bold)
            Text(step.body, color = Dj.Muted, fontSize = if (tv) 18.sp else 14.sp, lineHeight = if (tv) 26.sp else 20.sp)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!last) TourButton("Skip tour", primary = false, tv = tv, onClick = onDone)
                Spacer(Modifier.weight(1f))
                TourButton(if (last) "Let's go" else "Next", primary = true, tv = tv, modifier = Modifier.focusRequester(nextFocus), onClick = next)
            }
        }
    }
}

@Composable
private fun TourButton(label: String, primary: Boolean, tv: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .border(if (focused) 3.dp else 0.dp, if (focused) Dj.Text else Color.Transparent, shape)
            .padding(if (focused) 3.dp else 0.dp)
            .background(if (primary) Dj.Accent else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (tv) 26.dp else 20.dp, vertical = if (tv) 12.dp else 10.dp),
    ) {
        Text(label, color = if (primary) Dj.Bg else Dj.Muted, fontSize = if (tv) 18.sp else 14.sp, fontWeight = FontWeight.Bold)
    }
}
