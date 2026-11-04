package com.autodj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The frosted-glass look: translucent white panels with a bright top edge, floating over one
 * solid ground colour, with no gradients. Panels have no live backdrop blur, which keeps every
 * frame cheap, on phones and on TVs.
 */
object Glass {
    /** The ground everything floats on: a deep plum. */
    val Ground = Color(0xFF1B1030)
    val Fill = Color.White.copy(alpha = 0.08f)
    val FillStrong = Color.White.copy(alpha = 0.14f)
    val FillSelected = Color.White.copy(alpha = 0.22f)
    /** Over a busy visualizer: darker, so text on it stays readable. */
    val FillDark = Color(0xFF140E12).copy(alpha = 0.55f)
    val Text = Color.White
    val Muted = Color.White.copy(alpha = 0.78f)
    /** The one accent: flat orange. Text on it is [OnAccent]. */
    val Ember = Color(0xFFFF9F0A)
    val EmberText = Color(0xFFFFB340)
    val OnAccent = Color.Black
    /** Main buttons: flat orange (black text on it reads at about 10:1). */
    val Fire: Brush = SolidColor(Ember)
    val FireSelected = Ember.copy(alpha = 0.18f)

    /** The edge: light catching the top of the glass, fading down its sides. */
    val Edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f)))
}

/** A frosted panel: [fill] behind, a lit edge round [shape]. */
fun Modifier.glass(shape: Shape, fill: Color = Glass.Fill, edge: Brush = Glass.Edge, edgeWidth: Dp = 1.dp): Modifier =
    this.clip(shape).background(fill, shape).border(edgeWidth, edge, shape)

/** The ground behind the glass: one solid colour, plum unless a playing song lends its own. */
@Composable
fun AmbientLight(modifier: Modifier, color: Color = Glass.Ground) {
    Box(modifier.background(color))
}

/**
 * A platter of frosted glass: fine grooves outside [grooveFrom] of its radius, light catching its
 * upper left, and a sheen that shows it turning when it's rotated. [content] sits on top, centred.
 */
@Composable
fun GlassPlatter(modifier: Modifier, grooveFrom: Float = 0.62f, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.glass(CircleShape, Color.Transparent, edgeWidth = 1.5.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f)), Offset(r * 0.6f, r * 0.5f), r * 1.4f))
            val step = 4.dp.toPx()
            var g = r * grooveFrom
            while (g < r - step) {
                drawCircle(Color.White.copy(alpha = 0.07f), g, style = Stroke(1f))
                g += step
            }
            // Two soft highlights opposite each other: the sheen moves as the platter turns.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.2f), 0.18f to Color.Transparent,
                    0.55f to Color.Transparent, 0.62f to Color.White.copy(alpha = 0.12f), 0.72f to Color.Transparent,
                ),
            )
        }
        content()
    }
}

/** A round glass button for an icon, labelled for screen readers. */
@Composable
fun GlassIconButton(
    description: String,
    size: Dp = 44.dp,
    fill: Color = Glass.Fill,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).glass(CircleShape, fill)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** A glass segmented control: one option lit. Remote focus shows as a white ring. */
@Composable
fun <T> GlassSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    fontSize: TextUnit = 15.sp,
    fill: Color = Glass.Fill,
    selectedFill: Color = Glass.FillSelected,
    selectedText: Color = Glass.Text,
) {
    Row(
        modifier.glass(RoundedCornerShape(14.dp), fill).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).height(height).clip(shape)
                    .background(if (on) selectedFill else Color.Transparent, shape)
                    .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
                    .selectable(on, interactionSource = interaction, indication = null, role = Role.RadioButton) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o), fontSize = fontSize, maxLines = 1,
                    color = if (on) selectedText else Glass.Muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A small caps section heading over glass lists. */
@Composable
fun GlassHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Glass.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, modifier = modifier)
}

/** True when Android's "Remove animations" is on: records and discs then hold still. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Room the floating tab bar takes at the bottom: 64dp of bar and its 10dp margin, plus a little air. */
val TAB_BAR_SPACE = 84.dp

/** How much of the bottom the floating tab bar covers; scrolling content pads its end by this. */
val LocalTabBarInset = compositionLocalOf { 0.dp }

/** One tab of the floating glass tab bar; [tourKey] lets the guided tour point at it. */
class GlassTab(val label: String, val tourKey: String? = null, val icon: @Composable (Color) -> Unit)

/** Glass that content scrolls under: nearly solid, since nothing blurs what's behind it, so text under it never shows through. */
val GlassOverContent = Color(0xFF2A1F3D).copy(alpha = 0.97f)

/** The floating glass tab bar along the bottom. */
@Composable
fun GlassTabBar(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fill: Color = GlassOverContent) {
    Row(modifier.fillMaxWidth().height(64.dp).glass(CircleShape, fill).padding(5.dp).selectableGroup()) {
        tabs.forEachIndexed { i, t -> TabItem(t, i == selected) { onSelect(i) } }
    }
}

/**
 * A big screen held sideways, like a tablet: wide enough and tall enough for the roomy layouts
 * (a wider rail, bigger records, the mix plan and what's coming up). A phone on its side is
 * wide but too short for them.
 */
@Composable
fun isRoomyLandscape(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp >= 840 && c.screenHeightDp >= 600
}

/**
 * The same tabs as a column down the side, for landscape where height is short, spread evenly
 * down it, with [footer] (Cast and the like) under a divider at the bottom. [roomy] widens it for
 * a big screen.
 */
@Composable
fun GlassTabRail(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    roomy: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxHeight().width(if (roomy) 96.dp else 76.dp).glass(RoundedCornerShape(if (roomy) 48.dp else 38.dp))) {
        Column(Modifier.weight(1f).fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.SpaceEvenly) { RailTabs(tabs, selected, onSelect) }
        if (footer != null) {
            Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { footer() }
        }
    }
}

@Composable
private fun RailTabs(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit) {
    tabs.forEachIndexed { i, t ->
        val on = i == selected
        val color = if (on) Glass.Ember else Glass.Muted
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp).fillMaxWidth().height(72.dp).then(t.tourKey?.let { Modifier.tourTarget(it) } ?: Modifier)
                .clip(RoundedCornerShape(20.dp)).background(if (on) Glass.FillStrong else Color.Transparent)
                .selectable(on, role = Role.Tab) { onSelect(i) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { t.icon(color) }
            Text(t.label, color = color, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: GlassTab, on: Boolean, onClick: () -> Unit) {
    val color = if (on) Glass.Ember else Glass.Muted
    Column(
        Modifier.weight(1f).fillMaxHeight().then(tab.tourKey?.let { Modifier.tourTarget(it) } ?: Modifier)
            .clip(CircleShape).background(if (on) Glass.FillStrong else Color.Transparent)
            .selectable(on, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { tab.icon(color) }
        Text(tab.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
    }
}
