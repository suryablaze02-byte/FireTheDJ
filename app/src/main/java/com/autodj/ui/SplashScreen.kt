package com.autodj.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.autodj.R
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launch splash in the glass look: the plum ground, a glass record that settles in and turns with
 * the app icon on its label, the name and the credit, then a fade into the app. A tap skips it.
 */
@Composable
fun SplashScreen(tv: Boolean = false, onDone: () -> Unit) {
    // A TV is seen from across the room: everything a size up. Only a little, since a TV screen is just 540dp tall.
    val k = if (tv) 1.1f else 1f
    val record = remember { Animatable(0f) }
    val icon = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    val turn = remember { Animatable(0f) }
    val still = rememberReduceMotion()
    LaunchedEffect(Unit) {
        if (!still) launch { turn.animateTo(140f, tween(3200, easing = LinearEasing)) }
        launch { record.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(200)
        launch { icon.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        delay(250)
        text.animateTo(1f, tween(650))
        delay(1500)
        out.animateTo(0f, tween(350))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = out.value }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        AmbientLight(Modifier.fillMaxSize())
        // The credit sits in the same column as the logo, so on a short screen it moves down rather than overlapping.
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(210.dp * k), contentAlignment = Alignment.Center) {
                    // The record turns underneath; the icon on its label stays upright.
                    GlassPlatter(
                        Modifier.fillMaxSize().graphicsLayer {
                            val s = 0.82f + 0.18f * record.value
                            scaleX = s; scaleY = s; alpha = record.value; rotationZ = turn.value
                        },
                        grooveFrom = 0.66f,
                    )
                    Box(
                        Modifier.size(112.dp * k).graphicsLayer {
                            val s = 0.6f + 0.4f * icon.value
                            scaleX = s; scaleY = s; alpha = icon.value
                        }.clip(RoundedCornerShape(30.dp * k)),
                    ) {
                        // Launchers show the middle 72 of the icon's 108 units: scale up by 1.5 to match.
                        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                    }
                }
                Spacer(Modifier.height(30.dp * k))
                Column(
                    Modifier.graphicsLayer { alpha = text.value; translationY = (1 - text.value) * 24f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FireTheDjWordmark(46.sp * k)
                    Text("Your AI DJ for every party", color = Glass.Text, fontSize = 17.sp * k, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.padding(bottom = 28.dp).graphicsLayer { alpha = text.value }
                    .glass(RoundedCornerShape(999.dp)).padding(horizontal = 16.dp * k, vertical = 8.dp * k),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Made with passion", color = Glass.Muted, fontSize = 13.sp * k)
                Icon(Icons.Filled.Favorite, "love", tint = Color(0xFFFF375F), modifier = Modifier.size(14.dp * k))
                Text("by Surya", color = Glass.Muted, fontSize = 13.sp * k)
            }
        }
    }
}

/** The app icon as the launcher shows it: the flat orange tile with the bar flame, rounded by [corner]. */
@Composable
fun AppIcon(size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(corner))) {
        // Launchers show the middle 72 of the icon's 108 units: scale up by 1.5 to match.
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
    }
}

/** The app's name as on the splash: "Fire" in flat orange with a soft glow, then "TheDJ". Used wherever the name is a title. */
@Composable
fun FireTheDjWordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "Fire",
            style = TextStyle(shadow = Shadow(Color(0x8CFF7814), Offset.Zero, 36f)),
            color = Glass.Ember, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-1).sp,
        )
        Text("TheDJ", color = Dj.Text, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
    }
}
