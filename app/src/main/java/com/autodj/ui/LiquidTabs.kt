package com.autodj.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The floating glass tab bar along the bottom. Drag along it and the highlight follows the finger. */
@Composable
fun GlassTabBar(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fill: Color = GlassOverContent) {
    Box(modifier.fillMaxWidth().height(64.dp).floatingGlass(CircleShape, fill).padding(5.dp)) {
        LiquidTabs(tabs, selected, onSelect, vertical = false, lensShape = CircleShape)
    }
}

/**
 * The same tabs as a column down the side, for landscape where height is short, spread evenly
 * down it, with [footer] (Cast and the like) under a divider at the bottom. [roomy] widens it for
 * a big screen. Drag up and down it and the highlight follows the finger.
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
    Column(modifier.fillMaxHeight().width(if (roomy) 96.dp else 76.dp).floatingGlass(RoundedCornerShape(if (roomy) 48.dp else 38.dp))) {
        // The tabs sit together in the middle of the rail rather than spread to its ends.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(RAIL_ITEM * tabs.size).padding(horizontal = 6.dp)) {
                LiquidTabs(tabs, selected, onSelect, vertical = true, lensShape = RoundedCornerShape(20.dp))
            }
        }
        if (footer != null) {
            Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(Glass.Text.copy(alpha = 0.12f)))
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { footer() }
        }
    }
}

private val RAIL_ITEM = 84.dp

/**
 * Tabs in equal slots with one glass lens behind the picked one. A tap glides the lens over; a
 * press-and-drag lifts it (a touch bigger and brighter) and it follows the finger, ticking at each
 * tab it crosses, then settles on the nearest tab with a spring and selects it.
 */
@Composable
private fun LiquidTabs(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, vertical: Boolean, lensShape: Shape) {
    val n = tabs.size
    val position = remember { Animatable(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(selected, dragging) {
        if (!dragging) position.animateTo(selected.toFloat(), spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "lift")
    // While dragging, the tab under the finger lights up; otherwise the picked one.
    val lit = if (dragging) position.value.roundToInt().coerceIn(0, n - 1) else selected

    BoxWithConstraints(
        Modifier.fillMaxSize().selectableGroup().pointerInput(n, vertical) {
            val slot = (if (vertical) size.height else size.width) / n.toFloat()
            fun at(px: Float) = (px / slot - 0.5f).coerceIn(0f, n - 1f)
            var last = -1
            detectDragGestures(
                onDragStart = { o ->
                    dragging = true
                    val p = at(if (vertical) o.y else o.x)
                    last = p.roundToInt()
                    scope.launch { position.animateTo(p, spring(stiffness = Spring.StiffnessHigh)) }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val p = at(if (vertical) change.position.y else change.position.x)
                    scope.launch { position.snapTo(p) }
                    val i = p.roundToInt()
                    if (i != last) {
                        last = i
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onDragEnd = {
                    val i = position.value.roundToInt().coerceIn(0, n - 1)
                    dragging = false
                    if (i != selected) onSelect(i)
                },
                onDragCancel = { dragging = false },
            )
        },
    ) {
        val slot: Dp = (if (vertical) maxHeight else maxWidth) / n
        val shadow = if (LocalGlassDark.current) Color.Black.copy(alpha = 0.45f) else Color(0xFF181428).copy(alpha = 0.25f)
        // The lens: glass that brightens and swells a little while it's held.
        Box(
            Modifier
                .then(if (vertical) Modifier.offset(y = slot * position.value).fillMaxWidth().height(slot) else Modifier.offset(x = slot * position.value).width(slot).fillMaxHeight())
                .graphicsLayer {
                    val s = 1f + 0.12f * lift
                    scaleX = s
                    scaleY = s
                }
                .shadow((10 * lift).dp, lensShape, clip = false, ambientColor = shadow, spotColor = shadow)
                .background(lerpColor(Glass.FillStrong, Glass.FillSelected, lift), lensShape)
                .border(1.dp, Glass.Edge, lensShape),
        )
        val items: @Composable () -> Unit = {
            tabs.forEachIndexed { i, t ->
                val on = i == lit
                val color = if (on) Glass.Ember else Glass.Muted
                Column(
                    Modifier.then(if (vertical) Modifier.fillMaxWidth().height(slot) else Modifier.width(slot).fillMaxHeight())
                        .then(t.tourKey?.let { Modifier.tourTarget(it) } ?: Modifier)
                        .selectable(i == selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { t.icon(color) }
                    Text(
                        t.label, color = color, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (vertical) Column(Modifier.fillMaxSize()) { items() } else androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) { items() }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
