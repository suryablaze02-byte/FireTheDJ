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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The screens a phone tour step can show the app on: it goes there for that step. */
enum class TourScreen { MIX, MUSIC, SETTINGS }

/**
 * One stop on the tour: what to spotlight (a [tourTarget] key, or none for a centred card), what
 * to say, the [screen] it's on, and the version that brought it ([since]; the what's-new tour
 * shows only steps newer than the last tour someone saw).
 */
class TourStep(
    val target: String?,
    val title: String,
    val body: String,
    val screen: TourScreen = TourScreen.MIX,
    val since: String = "1.0",
    /** A picture for a step with nothing to spotlight: the welcome record, or the looks. */
    val art: TourArt? = null,
)

/** The pictures a centred tour card can show. */
enum class TourArt { WELCOME, LOOKS }

/** Where the tour's targets are on screen, as they report themselves, and where the tour wants the app. */
class TourState {
    val targets = mutableStateMapOf<String, Rect>()

    /** A tour is showing: the app follows [screen] while it does. */
    var active by mutableStateOf(false)
    var screen by mutableStateOf(TourScreen.MIX)
}

/** The running tour, if any; screens tag their parts with [tourTarget] through it. */
val LocalTour = compositionLocalOf<TourState?> { null }

/** Marks this element as the tour target [key]: its bounds are spotlighted at that step. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val tour = LocalTour.current ?: return this
    return onGloballyPositioned { tour.targets[key] = it.boundsInRoot() }
}

/** The phone app's tour for a new user: the whole app as it is now. [stems]: the phone can run stem separation. */
fun phoneTour(stems: Boolean) = listOfNotNull(
    TourStep(null, "Welcome to FireTheDJ", "Your AI DJ. Here's a one-minute tour.", art = TourArt.WELCOME),
    TourStep("tab_music", "Add your music", "The Music tab: a folder on this phone, Stream, or a link. Each song is analysed once for tempo, key, drops, vocals, language and genre."),
    TourStep("stream", "Stream", "YT Music, Spotify and Apple Music playlists, with one search across all three. Spotify and Apple Music songs play from YouTube: no account needed.", TourScreen.MUSIC),
    TourStep("tabbar", "Slide between tabs", "Tap a tab, or press and slide along the bar: the glass follows your finger and opens the tab you let go on. Sideways, slide up and down the rail."),
    TourStep("set", "Your set", "What the crate you pick will make: its songs, hours and tempo range. Choose Smooth or Trickster here, then Start, and FireTheDJ takes it from there."),
    TourStep("crates", "Pick a crate", "Songs sort themselves into genre crates, each with a few of its covers. One crate keeps the party in one style; All genres moves across them. All music picks a folder or playlist."),
    TourStep("mode", "Smooth or Trickster", "Smooth blends long and clean. Trickster drops a trick about every 30 seconds: rolls, bass kills, scratches, word loops, and sounds that suit each song's language."),
    TourStep("tab_now", "Now Playing", "The record, the next mix and skip: tap it to mix out at the next phrase, again for the next bar. While a set plays, Mix and Music show it in a mini player too. Turn the phone sideways for two decks and the effects, live."),
    TourStep(null, "Pick a look", "Full-screen visualizers from Now Playing. Tap the screen while one plays to pick another; FireTheDJ remembers it.", art = TourArt.LOOKS),
    if (!com.autodj.BuildConfig.CASTING) null else TourStep("cast", "Take it to the TV", "Cast to a Chromecast or smart TV, and the visualizers fill the big screen."),
    TourStep("settings", "Settings", "Light, dark or as your phone is set, analysis in the background, updates and what's new, and stem separation."),
    if (stems) STEMS_STEP else null,
)

private val STEMS_STEP = TourStep(
    "stems", "Stem separation",
    "A one-time 163 MB download. During a set, each mix is split into drums, bass, vocals and the rest: bass swaps keep the kick, vocal swaps come clean. " +
        "Stems for tricks does the same for Trickster's drops and word loops.",
    TourScreen.SETTINGS, since = "1.6.0",
)

/** What's new on the phone since 1.3, oldest first: each step shows once, to people who had an earlier version. */
private val PHONE_WHATS_NEW = listOf(
    TourStep(null, "Lyrics on screen", "Beat Pop puts the song's words on the visualizer, in time with the singing. And when two songs share a word, FireTheDJ can mix on it.", since = "1.3.0"),
    TourStep("mode", "Trickster, rebuilt", "A trick about every 30 seconds: bass kills that slam back on the drop, rising rolls, tape stops, key lifts, and Latin, Arabic and Indian sounds that follow each song's language.", since = "1.4.0"),
    TourStep("tab_now", "Skip", "On Now Playing, next to play: the song mixes out at its next phrase; tap again for the next bar. Headphone and car next buttons do the same.", since = "1.4.0"),
    TourStep("tab_now", "Turn it sideways", "Now Playing shows both decks, the mix and the effects as they play. There's a new look, Acid Trip, and effect names now show only on Club and LED Field.", since = "1.5.0"),
    TourStep("stream", "Stream", "YT Music and Spotify playlists on one page, with one search. Spotify songs play from YouTube, and talking intros and skits are skipped.", TourScreen.MUSIC, since = "1.5.1"),
    TourStep("tab_now", "Two new looks", "Open the visualizer from Now Playing: Chill Drive cruises a neon highway with fireworks on the beat, and Lo-fi Vibe's rooftop city bounces along. Karaoke has gone; Beat Pop shows the lyrics.", since = "1.6.1"),
    TourStep("settings", "Settings", "What's new for your version, analysis that keeps going in the background, and updates that download as soon as you allow them.", since = "1.3.1"),
    STEMS_STEP,
    TourStep("set", "Your set", "Mix has a new card: what the crate will make, Smooth or Trickster, and Start, together. Crates show a few of their covers now.", since = "2.0.0"),
    TourStep("tabbar", "Slide between tabs", "Press and slide along the tab bar: the glass follows your finger and opens the tab you let go on.", since = "2.0.0"),
    TourStep("tab_now", "Effects, live", "Sideways, the Effects box shows the effect playing now, what's next and every effect in the mix. Mix and Music show the set in a mini player.", since = "2.0.0"),
    TourStep(null, "Pick a look", "The visualizer's looks are pictures now: tap the screen while one plays and choose.", since = "2.0.0", art = TourArt.LOOKS),
)

/**
 * The what's-new tour for someone who last saw the tour at [lastSeen]: the steps newer than
 * that, after a welcome card; empty when there's nothing new. [stems]: the phone can run stems.
 */
fun phoneWhatsNew(lastSeen: String, stems: Boolean) = whatsNew(PHONE_WHATS_NEW.filter { stems || it !== STEMS_STEP }, lastSeen)

/** [steps] newer than [lastSeen], after a welcome card; empty when none are. */
fun whatsNew(steps: List<TourStep>, lastSeen: String): List<TourStep> {
    val fresh = steps.filter { com.autodj.update.Updates.newer(it.since, lastSeen) }
    if (fresh.isEmpty()) return emptyList()
    return listOf(TourStep(null, "What's new in FireTheDJ", "A quick look at what's changed since your last tour. Skip any time.")) + fresh
}

/** Whether the first-launch tour has been shown (or skipped) on this device, per app. */
object TourMemory {
    private const val PREFS = "tour"
    // v2: the tour for the glass redesign, shown once even to people who saw the first one.
    private const val DONE = "done_v2"
    private const val SEEN = "seen_version"

    /** Before versions were remembered, a finished tour was 1.2's at the latest: everything since 1.3 is new. */
    private const val BEFORE_REMEMBERING = "1.2.99"

    fun done(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(DONE, false)
    fun markDone(context: Context) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(DONE, true).apply()

    /** The version whose tour (first or what's new) was last shown here. */
    fun seenVersion(context: Context): String = context.getSharedPreferences(PREFS, 0).getString(SEEN, null) ?: BEFORE_REMEMBERING

    fun markSeen(context: Context, version: String) = context.getSharedPreferences(PREFS, 0).edit().putString(SEEN, version).apply()
}

/**
 * The guided tour: the screen dims, the current step's target shows through a rounded hole with
 * a white ring and a soft glow, and a glass card explains it, above or below the target. Next /
 * Skip on the phone; on a TV the remote stays inside the card (OK = next, Back = skip).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TourOverlay(state: TourState, steps: List<TourStep>, tv: Boolean, onDone: () -> Unit) = TourOverlayBody(state, steps, tv, onDone)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TourOverlayBody(state: TourState, steps: List<TourStep>, tv: Boolean, onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val last = index == steps.lastIndex
    val next: () -> Unit = { if (last) onDone() else index++ }
    val back: () -> Unit = { if (index > 0) index-- }
    val target = step.target?.let { state.targets[it] }
    // The ring glides from one target to the next, so the eye can follow it.
    val glide = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.8f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
    val l by androidx.compose.animation.core.animateFloatAsState(target?.left ?: 0f, glide, label = "l")
    val t by androidx.compose.animation.core.animateFloatAsState(target?.top ?: 0f, glide, label = "t")
    val r by androidx.compose.animation.core.animateFloatAsState(target?.right ?: 0f, glide, label = "r")
    val b by androidx.compose.animation.core.animateFloatAsState(target?.bottom ?: 0f, glide, label = "b")
    val hole = target?.let { Rect(l, t, r, b) }
    val dark = LocalGlassDark.current
    val density = LocalDensity.current
    val pad = with(density) { 10.dp.toPx() }
    val nextFocus = remember { FocusRequester() }
    BackHandler(onBack = onDone)
    LaunchedEffect(index) { runCatching { nextFocus.requestFocus() } }
    // The app goes to the screen each step is about.
    LaunchedEffect(index) { state.screen = step.screen }
    DisposableEffect(Unit) {
        state.active = true
        onDispose { state.active = false; state.screen = TourScreen.MIX }
    }

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
            drawPath(scrim, Color.Black.copy(alpha = if (dark) .66f else .5f))
            hole?.let {
                val r = it.inflate(pad)
                val corner = CornerRadius(pad * 1.8f)
                // A soft ember glow round the ring, then the ring itself in white.
                drawRoundRect(Glass.Ember.copy(alpha = .35f), r.topLeft - Offset(3f, 3f), Size(r.width + 6f, r.height + 6f), corner, style = Stroke(with(density) { 8.dp.toPx() }))
                drawRoundRect(Color.White, r.topLeft, Size(r.width, r.height), corner, style = Stroke(with(density) { 2.dp.toPx() }))
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
                .widthIn(max = if (tv) 600.dp else 400.dp)
                // The app's own glass, light or dark, nearly solid so it reads over the dimmed screen.
                .background(Glass.Sheet, RoundedCornerShape(28.dp))
                .glass(RoundedCornerShape(28.dp), Glass.Fill)
                .padding(if (tv) 26.dp else 20.dp)
                // On a TV the remote must not wander off into the dimmed screen behind.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (step.art) {
                TourArt.WELCOME -> WelcomeArt(tv)
                TourArt.LOOKS -> Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    com.autodj.ui.vis.VisualizerStyle.entries.filter { !tv || it.onTv }.take(if (tv) 8 else 4).forEach { LookArt(it, Modifier.size(if (tv) 56.dp else 64.dp, if (tv) 40.dp else 44.dp).clip(RoundedCornerShape(10.dp))) }
                }
                null -> Unit
            }
            // Where you are in the tour: one dot per step, this one a longer ember bar, and the count.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp).semantics(mergeDescendants = true) {
                contentDescription = "Step ${index + 1} of ${steps.size}"
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.indices.forEach { i ->
                        Box(Modifier.height(6.dp).width(if (i == index) 18.dp else 6.dp)
                            .background(if (i == index) Glass.Ember else if (i < index) Glass.Text.copy(alpha = .55f) else Glass.Text.copy(alpha = .2f), RoundedCornerShape(3.dp)))
                    }
                }
                Text("${index + 1} of ${steps.size}", color = Glass.Muted, fontSize = if (tv) 15.sp else 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(step.title, color = Glass.Text, fontSize = if (tv) 28.sp else 22.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = if (step.art == TourArt.WELCOME) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth())
            Text(step.body, color = Glass.Muted, fontSize = if (tv) 18.sp else 15.sp, lineHeight = if (tv) 26.sp else 21.sp,
                textAlign = if (step.art == TourArt.WELCOME) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth())
            if (step.art == TourArt.WELCOME) WelcomePromises(tv)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!last) TourButton("Skip", primary = false, tv = tv, onClick = onDone)
                Spacer(Modifier.weight(1f))
                if (index > 0) TourButton("Back", primary = false, outlined = true, tv = tv, onClick = back)
                TourButton(if (last) "Let's go" else if (index == 0) "Start the tour" else "Next", primary = true, tv = tv, modifier = Modifier.focusRequester(nextFocus), onClick = next)
            }
            if (tv) Text("◀ ▶ choose · OK press · Back ends the tour", color = Glass.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TourButton(label: String, primary: Boolean, tv: Boolean, modifier: Modifier = Modifier, outlined: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .border(if (focused) 3.dp else 0.dp, if (focused) Glass.Text else Color.Transparent, shape)
            .padding(if (focused) 3.dp else 0.dp)
            .background(if (primary) Glass.Ember else if (outlined) Glass.FillStrong else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = if (tv) 26.dp else 22.dp, vertical = if (tv) 12.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) Glass.OnAccent else if (outlined) Glass.Text else Glass.Muted, fontSize = if (tv) 18.sp else 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** The welcome's picture: the app's mark on a record, a ring of spectrum round it. */
@Composable
private fun WelcomeArt(tv: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (tv) 120.dp else 128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r0 = size.minDimension * 0.4f
                for (i in 0 until 72) {
                    val a = i / 72f * 2f * Math.PI.toFloat()
                    val len = size.minDimension * (0.04f + 0.06f * kotlin.math.abs(kotlin.math.sin(i * 0.7f)))
                    val d = Offset(kotlin.math.cos(a), kotlin.math.sin(a))
                    drawLine(Glass.Ember.copy(alpha = 0.85f), c + d * r0, c + d * (r0 + len), 2.5f * density)
                }
            }
            GlassPlatter(Modifier.fillMaxSize(0.72f)) { AppIcon(if (tv) 40.dp else 44.dp, 12.dp) }
        }
    }
}

/** What FireTheDJ does, in three lines, under the welcome. */
@Composable
private fun WelcomePromises(tv: Boolean) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple("Picks every song", "From your crates, one after another", 0),
            Triple("Mixes on the beat", "Tempo, key and the drop lined up", 1),
            Triple("Drops the effects", "Echoes, rolls and tricks, on its own", 2),
        ).forEach { (title, sub, i) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(Glass.FireSelected, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    when (i) {
                        0 -> NoteGlyph(Glass.EmberText, 20.dp)
                        1 -> BarsGlyph(Glass.EmberText, 20.dp)
                        else -> FlameGlyph(Glass.EmberText, 20.dp)
                    }
                }
                Column {
                    Text(title, color = Glass.Text, fontSize = if (tv) 18.sp else 15.sp, fontWeight = FontWeight.Bold)
                    Text(sub, color = Glass.Muted, fontSize = if (tv) 15.sp else 13.sp)
                }
            }
        }
    }
}
