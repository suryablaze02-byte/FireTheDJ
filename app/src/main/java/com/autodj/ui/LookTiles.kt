package com.autodj.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.ui.vis.VisualizerStyle

/**
 * A look to pick: a small still of what it shows and its short name. The picked one is ringed in
 * ember; on a TV the one the remote is on grows and is ringed in white.
 */
@Composable
fun LookTile(style: VisualizerStyle, selected: Boolean, onClick: () -> Unit, width: Dp = 64.dp, height: Dp = 64.dp, fontSize: TextUnit = 12.sp, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow by animateFloatAsState(if (focused) 1.1f else 1f, label = "focus")
    val shape = RoundedCornerShape(height * 0.28f)
    Column(
        modifier.width(width + 4.dp).scale(grow)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${style.label}: ${style.blurb}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LookArt(
            style,
            Modifier.size(width, height).clip(shape)
                .border(
                    when {
                        focused -> 3.dp
                        selected -> 3.dp
                        else -> 1.dp
                    },
                    when {
                        focused -> Color.White
                        selected -> Glass.Ember
                        else -> Color.White.copy(alpha = 0.18f)
                    },
                    shape,
                ),
        )
        Text(style.short, color = if (selected) Glass.EmberText else Color.White.copy(alpha = 0.85f), fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
    }
}

/** A still that says what a look is: rings, liquid colour, an LED wall, a stage, a night road and so on. */
@Composable
fun LookArt(style: VisualizerStyle, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            VisualizerStyle.TUNNEL -> {
                drawRect(Color(0xFF0B1726))
                for (i in 0 until 6) {
                    val s = 1f - i * 0.15f
                    drawRoundRect(
                        if (i % 2 == 0) Color(0xFF2E7FD6) else Color(0xFF2DD4BF),
                        topLeft = Offset(w * (1 - s) / 2, h * (1 - s) / 2), size = Size(w * s, h * s),
                        cornerRadius = CornerRadius(w * 0.12f * s), style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
            VisualizerStyle.LIQUID -> {
                drawRect(Color(0xFF1E1035))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF472B6), Color.Transparent), Offset(w * 0.3f, h * 0.3f), w * 0.6f), w * 0.6f, Offset(w * 0.3f, h * 0.3f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF6366F1), Color.Transparent), Offset(w * 0.72f, h * 0.7f), w * 0.6f), w * 0.6f, Offset(w * 0.72f, h * 0.7f))
            }
            VisualizerStyle.ACID -> {
                // Eight glowing petals round a jewel: the breathing fractal.
                drawRect(Color(0xFF120A1C))
                val c = Offset(w / 2, h / 2)
                for (i in 0 until 8) rotate(i * 45f, c) {
                    drawOval(Brush.verticalGradient(listOf(Color(0xFFE9A86B), Color(0xFF9C3D7A)), startY = c.y - h * 0.46f, endY = c.y), Offset(c.x - w * 0.06f, c.y - h * 0.46f), Size(w * 0.12f, h * 0.36f))
                }
                drawCircle(Color(0xFF3FA7A0), w * 0.09f, c)
                drawCircle(Color(0xFF120A1C), w * 0.04f, c)
            }
            VisualizerStyle.FIELD -> {
                drawRect(Color(0xFF140A04))
                val step = w / 8
                for (x in 0 until 8) for (y in 0 until 8) {
                    val lit = (x * 7 + y * 3) % 5 != 0
                    drawCircle(if (lit) Color(0xFFFF9F0A) else Color(0xFF5A2A06), step * 0.28f, Offset(step * (x + 0.5f), step * (y + 0.5f) * h / w))
                }
            }
            VisualizerStyle.CLUB -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFF3B1478))), size = Size(w, h * 0.5f))
                drawRect(Color(0xFFF97316), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.1f))
                drawRect(Color(0xFF1C0F2E), topLeft = Offset(0f, h * 0.6f), size = Size(w, h * 0.4f))
                for (i in 0 until 5) drawCircle(Color(0xFF2A1840), w * 0.07f, Offset(w * (0.1f + i * 0.2f), h * 0.74f))
            }
            VisualizerStyle.DRIVE -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF070B1E), Color(0xFF3A3A66)), endY = h * 0.6f), size = Size(w, h * 0.6f))
                drawCircle(Color(0xFFF2EEF8), w * 0.12f, Offset(w * 0.68f, h * 0.28f))
                drawRect(Color(0xFF1A1B3C), topLeft = Offset(0f, h * 0.52f), size = Size(w, h * 0.08f))
                drawRect(Color(0xFF07081A), topLeft = Offset(0f, h * 0.6f), size = Size(w, h * 0.4f))
                drawRect(Color(0xFFFF2840), topLeft = Offset(w * 0.3f, h * 0.8f), size = Size(w * 0.4f, h * 0.035f))
            }
            VisualizerStyle.LOFI -> {
                drawRect(Color(0xFFFDBA74), size = Size(w, h * 0.5f))
                drawRect(Color(0xFF4338CA), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                for (i in 0 until 4) drawRect(Color(0xFF312E81), topLeft = Offset(w * (0.05f + i * 0.24f), h * (0.32f + (i % 2) * 0.08f)), size = Size(w * 0.18f, h * 0.2f))
            }
            VisualizerStyle.BEAT_POP -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))))
                for (i in 0 until 3) drawRoundRect(Color.White.copy(alpha = 0.85f - i * 0.25f), topLeft = Offset(w * 0.18f, h * (0.3f + i * 0.16f)),
                    size = Size(w * (0.64f - i * 0.12f), h * 0.08f), cornerRadius = CornerRadius(h * 0.04f))
            }
            VisualizerStyle.RABBIT_HOLE -> {
                // Rings of a lattice shrinking into a dark centre.
                drawRect(Color(0xFF0E0815))
                val c = Offset(w / 2, h / 2)
                for (i in 0 until 6) {
                    val r = w * 0.62f * (1 - i * 0.15f)
                    drawCircle(if (i % 2 == 0) Color(0xFFE9A86B) else Color(0xFF9C3D7A), r, c, alpha = 0.35f + i * 0.1f, style = Stroke(r * 0.08f))
                }
                drawCircle(Color.Black, w * 0.08f, c)
            }
        }
    }
}
