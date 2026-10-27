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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The frosted-glass look: translucent white panels with a bright top edge, over a softly lit
 * ground. The ground is already soft (blurred light or artwork), so the panels read as frosted
 * glass without a live backdrop blur, which keeps every frame cheap, on phones and on TVs.
 */
object Glass {
    val Fill = Color.White.copy(alpha = 0.08f)
    val FillStrong = Color.White.copy(alpha = 0.14f)
    val FillSelected = Color.White.copy(alpha = 0.22f)
    /** Over a busy visualizer: darker, so text on it stays readable. */
    val FillDark = Color(0xFF140E12).copy(alpha = 0.55f)
    val Text = Color.White
    val Muted = Color.White.copy(alpha = 0.74f)
    val Ember = Color(0xFFFFB43A)
    val EmberText = Color(0xFFFFC98A)
    val Fire = Brush.horizontalGradient(listOf(Color(0xFFE8174A), Color(0xFFFF5A1F), Color(0xFFFFB43A)))
    val FireSelected = Color(0xFFFF6A1F).copy(alpha = 0.32f)

    /** The edge: light catching the top of the glass, fading down its sides. */
    val Edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f)))
}

/** A frosted panel: [fill] behind, a lit edge round [shape]. */
fun Modifier.glass(shape: Shape, fill: Color = Glass.Fill, edge: Brush = Glass.Edge, edgeWidth: Dp = 1.dp): Modifier =
    this.clip(shape).background(fill, shape).border(edgeWidth, edge, shape)

/** Light behind the glass: soft pools of colour on near-black, like stage light through haze. */
@Composable
fun AmbientLight(modifier: Modifier, a: Color = Color(0xFFFF5A1F), b: Color = Color(0xFFC2185B), c: Color = Color(0xFF5B2BD6)) {
    Canvas(modifier) {
        drawRect(Color(0xFF0B0A0F))
        fun pool(color: Color, x: Float, y: Float, r: Float, alpha: Float) {
            val center = Offset(size.width * x, size.height * y)
            val radius = size.maxDimension * r
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius), radius, center)
        }
        pool(a, 0.05f, 0.02f, 0.62f, 0.55f)
        pool(b, 1.0f, 0.45f, 0.5f, 0.42f)
        pool(c, 0.2f, 1.0f, 0.58f, 0.48f)
    }
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

/** One tab of the floating glass tab bar; [tourKey] lets the guided tour point at it. */
class GlassTab(val label: String, val tourKey: String? = null, val icon: @Composable (Color) -> Unit)

/** The floating glass tab bar along the bottom. */
@Composable
fun GlassTabBar(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(64.dp).glass(RoundedCornerShape(26.dp)).selectableGroup()) {
        tabs.forEachIndexed { i, t -> TabItem(t, i == selected) { onSelect(i) } }
    }
}

@Composable
private fun RowScope.TabItem(tab: GlassTab, on: Boolean, onClick: () -> Unit) {
    val color = if (on) Glass.Ember else Glass.Muted
    Column(
        Modifier.weight(1f).fillMaxHeight().then(tab.tourKey?.let { Modifier.tourTarget(it) } ?: Modifier)
            .selectable(on, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { tab.icon(color) }
        Text(tab.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
    }
}
