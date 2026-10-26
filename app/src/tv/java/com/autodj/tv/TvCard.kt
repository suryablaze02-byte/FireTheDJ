package com.autodj.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.ui.Dj

/**
 * A round button for the remote, holding an icon. When focused it grows and an accent ring
 * appears around it, with a gap, so it's obvious from across the room.
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
            .border(4.dp, if (focused) Dj.Accent else Color.Transparent, CircleShape)
            .padding(10.dp)
            .background(fill, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/**
 * A card for the remote: it grows and lights up in the fire colour when the D-pad lands on it,
 * and OK presses it. [selected] marks the current choice (the mixing mode).
 */
@Composable
fun TvCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = Dj.Accent,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focus")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .scale(scale)
            .background(if (focused) accent.copy(alpha = .22f) else if (selected) Dj.AccentBg else Dj.Surface, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) accent else if (selected) accent.copy(alpha = .6f) else Dj.Border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Dj.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, color = if (focused) Dj.Text else Dj.Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
