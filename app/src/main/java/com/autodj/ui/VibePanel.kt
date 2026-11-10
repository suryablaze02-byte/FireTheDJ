package com.autodj.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.autodj.dj.EngineState
import com.autodj.dj.MixMode
import com.autodj.dj.SetEnergy
import com.autodj.dj.SetPlanner
import com.autodj.dj.VibePreview

/** Where the Change the vibe choices are shown: each has its own room. */
enum class VibeRoom(val tile: Dp, val title: TextUnit, val hint: TextUnit, val row: Dp, val gap: Dp, val heading: TextUnit) {
    /** The phone upright: a sheet from the bottom. */
    SHEET(58.dp, 15.sp, 12.sp, 44.dp, 14.dp, 22.sp),

    /** The phone sideways: a screen of its own, in two columns. */
    SIDE(68.dp, 15.sp, 12.sp, 48.dp, 12.dp, 22.sp),

    /** The TV's side panel, worked with the remote. */
    TV(46.dp, 15.sp, 12.sp, 38.dp, 10.dp, 30.sp),
}

/** A little on/off switch to sit in a row that is itself the control. */
@Composable
fun GlassSwitch(on: Boolean, modifier: Modifier = Modifier) {
    // The off track is dark enough to read on a white card, and on differs by more than colour: the knob moves and ticks.
    val offTrack = if (LocalGlassDark.current) Color.White.copy(alpha = 0.38f) else Color(0xFF77777F)
    Box(modifier.size(56.dp, 34.dp).background(if (on) Glass.Ember else offTrack, CircleShape).padding(4.dp)) {
        Box(
            Modifier.offset(x = if (on) 22.dp else 0.dp).size(26.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp)) }
    }
}

/** The whole-row Only bangers switch on the phone's Mix tab. */
@Composable
fun OnlyBangersRow(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(shape).background(if (on) Glass.FireSelected else Glass.FillSelected, shape)
            .border(if (on) 2.dp else 1.dp, if (on) Glass.Ember else Glass.Text.copy(alpha = 0.2f), shape)
            .clickable(role = Role.Switch, onClickLabel = if (on) "Turn off" else "Turn on") { onChange(!on) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(SetEnergy.BANGERS.label, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(SetEnergy.BANGERS.hint, color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassSwitch(on)
    }
}

@Composable
private fun VibeTile(title: String, hint: String, on: Boolean, height: Dp, room: VibeRoom, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(height).clip(shape).background(if (focused) Glass.FillSelected else if (on) Glass.FireSelected else Glass.Fill, shape)
            .border(if (focused) 3.dp else if (on) 2.dp else 1.dp, if (focused) Glass.Text else if (on) Glass.Ember else Color.White.copy(alpha = 0.14f), shape)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Glass.Text, fontSize = room.title, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(hint, color = Glass.Muted, fontSize = room.hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LanguageChip(name: String, on: Boolean, room: VibeRoom, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier.height(room.row).clip(CircleShape).background(if (focused) Glass.FillSelected else if (on) Glass.FireSelected else Glass.Fill, CircleShape)
            .border(if (focused) 3.dp else if (on) 2.dp else 0.dp, if (focused) Glass.Text else if (on) Glass.Ember else Color.Transparent, CircleShape)
            .clickable(interaction, indication = null, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(name, color = Glass.Text, fontSize = (room.hint.value + 1).sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun VibeLabel(text: String) {
    Text(text, color = Glass.EmberText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun VibeButton(label: String, primary: Boolean, enabled: Boolean, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier.height(height).clip(CircleShape).background(if (primary && enabled) Glass.Ember else Glass.FillStrong, CircleShape)
            .border(if (focused) 3.dp else 0.dp, if (focused) Glass.Text else Color.Transparent, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary && enabled) Glass.OnAccent else if (enabled) Glass.Text else Glass.Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Change the vibe: the energy, the style and the languages of every song after the playing one.
 * The choices apply only with Apply. [first] takes the remote's focus on a TV.
 */
@OptIn(UnstableApi::class)
@Composable
fun VibeOptions(vm: DjViewModel, startEnergy: SetEnergy, startMode: MixMode, room: VibeRoom, onClose: () -> Unit, first: FocusRequester? = null) {
    var energy by remember { mutableStateOf(startEnergy) }
    var mode by remember { mutableStateOf(startMode) }
    val current by vm.vibeLanguages.collectAsStateWithLifecycle()
    var languages by remember { mutableStateOf(current) }
    val choices = remember { vm.vibeLanguageChoices() }
    val key = energy to languages
    val computed by produceState<Pair<Any, VibePreview>?>(null, key) { value = key to vm.vibePreview(energy, languages) }
    // What's shown stays put while a new choice is counted; Apply waits for the answer to this one.
    val shown = computed?.second
    val preview = computed?.takeIf { it.first == key }?.second
    val enough = preview != null && preview.songs.size >= SetPlanner.MIN_VIBE_SONGS
    val fit = when {
        shown == null -> "Counting songs"
        shown.songs.size >= SetPlanner.MIN_VIBE_SONGS -> "${shown.songs.size} of ${shown.total} songs fit"
        else -> "Too few songs fit. Try another choice."
    }
    val apply = { if (preview != null && vm.applyVibe(energy, mode, languages, preview)) onClose() }

    val style: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            VibeLabel("STYLE")
            GlassSegmented(MixMode.entries, mode, { it.label }, { mode = it }, Modifier.fillMaxWidth(), height = room.row - 8.dp)
        }
    }
    val languagesRow: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            VibeLabel("LANGUAGES")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.forEach { l ->
                    LanguageChip(l, l in languages, room, Modifier.weight(1f)) { languages = if (l in languages) languages - l else languages + l }
                }
            }
        }
    }
    val energyTiles: @Composable () -> Unit = {
        Column {
            VibeLabel("ENERGY")
            val rows = SetEnergy.entries.filter { it != SetEnergy.BANGERS }.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { e ->
                            val focus = if (first != null && e == energy) Modifier.focusRequester(first) else Modifier
                            VibeTile(e.label, e.hint, e == energy, room.tile, room, Modifier.weight(1f).then(focus)) { energy = e }
                        }
                    }
                }
                val focus = if (first != null && energy == SetEnergy.BANGERS) Modifier.focusRequester(first) else Modifier
                VibeTile(SetEnergy.BANGERS.label, SetEnergy.BANGERS.hint, energy == SetEnergy.BANGERS, room.tile, room, Modifier.fillMaxWidth().then(focus)) { energy = SetEnergy.BANGERS }
            }
        }
    }
    val heading: @Composable () -> Unit = {
        Column {
            Text("Change the vibe", color = Glass.Text, fontSize = room.heading, fontWeight = FontWeight.ExtraBold)
            Text("This song plays on. Every song after it is picked again.", color = Glass.Muted, fontSize = 13.sp)
        }
    }
    val options: @Composable () -> Unit = {
        heading()
        energyTiles()
        style(Modifier)
        if (choices.size > 1) languagesRow(Modifier)
    }
    val detail: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(fit, color = Glass.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            val next = shown?.next
            if (next != null && shown.songs.size >= SetPlanner.MIN_VIBE_SONGS) Text("Next up: ${displayTitle(next.title)}", color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (room == VibeRoom.SIDE) {
        // Two columns with everything in view: no scrolling, and Cancel and Apply always on screen.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Change the vibe", color = Glass.Text, fontSize = room.heading, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) { energyTiles() }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(room.gap)) {
                    style(Modifier)
                    if (choices.size > 1) languagesRow(Modifier)
                    Spacer(Modifier.weight(1f))
                    detail(Modifier)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VibeButton("Cancel", false, true, 48.dp, Modifier.weight(1f), onClose)
                        VibeButton("Apply", true, enough, 48.dp, Modifier.weight(1f), apply)
                    }
                }
            }
        }
    } else if (room == VibeRoom.SHEET) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(room.gap)) {
            options()
            detail(Modifier)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VibeButton("Cancel", false, true, 52.dp, Modifier.weight(1f), onClose)
                VibeButton("Apply", true, enough, 52.dp, Modifier.weight(1f), apply)
            }
        }
    } else {
        // Apply stays pinned at the bottom; only the choices scroll, if they ever need to.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(room.gap)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(room.gap)) { options() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                detail(Modifier.weight(1f))
                VibeButton("Apply", true, enough, 48.dp, Modifier.width(124.dp), apply)
            }
        }
    }
}

/** The phone upright: Change the vibe from the bottom. */
@Composable
fun VibeSheet(vm: DjViewModel, state: EngineState, onClose: () -> Unit) {
    GlassSheet(onClose) { VibeOptions(vm, state.energy, state.mode, VibeRoom.SHEET, onClose) }
}

/** The phone sideways: Change the vibe as a screen of its own. Nothing moves behind it, and the music plays on. */
@Composable
fun VibeFullScreen(vm: DjViewModel, state: EngineState, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    SystemBarIcons(darkGround = LocalGlassDark.current)
    Box(Modifier.fillMaxSize().background(Glass.Sheet).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        VibeOptions(vm, state.energy, state.mode, VibeRoom.SIDE, onClose)
    }
}

/** On a TV: Change the vibe down the right side, the same panel as Mix several. */
@Composable
fun VibeSidePanel(vm: DjViewModel, state: EngineState, room: VibeRoom, width: Dp, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            val shape = RoundedCornerShape(if (room == VibeRoom.TV) 32.dp else 26.dp)
            Column(
                Modifier.padding(if (room == VibeRoom.TV) 20.dp else 8.dp).fillMaxHeight().width(width)
                    .background(Glass.Sheet, shape).glass(shape, Glass.Fill).padding(horizontal = 20.dp, vertical = if (room == VibeRoom.TV) 20.dp else 12.dp),
            ) { VibeOptions(vm, state.energy, state.mode, room, onClose, first.takeIf { room == VibeRoom.TV }) }
        }
    }
    if (room == VibeRoom.TV) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** The Vibe button on the Coming up card. */
@Composable
internal fun VibeLinkButton(onClick: () -> Unit, height: Dp = 32.dp) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.height(height).clip(CircleShape).background(Glass.FireSelected, CircleShape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Glass.Text else Glass.Ember, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Change the vibe", onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text("Vibe", color = Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
