package com.autodj.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.ui.Glass
import com.autodj.ui.glass

/**
 * A round glass button for the remote, holding an icon. When focused it lifts, grows and gets a
 * white ring with a gap, as on Apple TV, so it's obvious from across the room.
 */
@Composable
fun TvRoundButton(
    diameter: Dp,
    fill: Color,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "focus")
    Box(
        modifier.size(diameter + 20.dp).scale(scale)
            .border(3.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .padding(10.dp)
            .glass(CircleShape, fill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/**
 * A glass card for the remote: the D-pad lifts it, grows it and rings it in white; OK presses it.
 * [selected] marks the current choice in the fire colour; [accent] tints its edge (a source's colour).
 */
@Composable
fun TvCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color? = null,
    titleColor: Color = Glass.Text,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focus")
    val shape = RoundedCornerShape(24.dp)
    val edge = when {
        focused -> SolidColor(Color.White)
        selected -> SolidColor(Glass.Ember)
        accent != null -> SolidColor(accent.copy(alpha = .45f))
        else -> Glass.Edge
    }
    Row(
        modifier
            .scale(scale)
            .glass(shape, if (focused) Glass.FillSelected else if (selected) Glass.FireSelected else Glass.Fill, edge, if (focused) 3.dp else 1.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon?.invoke()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = titleColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = if (focused) Glass.Text else Glass.Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
