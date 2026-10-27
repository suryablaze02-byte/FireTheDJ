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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.autodj.R
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launch splash in the glass look: stage light, a glass record that settles in and turns with
 * the app icon on its label, the burning name and the credit, then a fade into the app. A tap skips it.
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
    LaunchedEffect(Unit) {
        launch { turn.animateTo(140f, tween(3200, easing = LinearEasing)) }
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
            Text(
                "Made with passion ❤️ by Surya",
                color = Glass.Muted, fontSize = 13.sp * k,
                modifier = Modifier.padding(bottom = 28.dp).graphicsLayer { alpha = text.value }
                    .glass(RoundedCornerShape(999.dp)).padding(horizontal = 16.dp * k, vertical = 8.dp * k),
            )
        }
    }
}

/** The app's name as on the splash: "Fire" burning, then "TheDJ". Used wherever the name is a title. */
@Composable
fun FireTheDjWordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { clock.value = (it - t0) / 1e9f }
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        // "Fire" burns: flames lick up behind it and the letters glow from yellow at the top to red at the base.
        Text(
            "Fire",
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFFBE0), Color(0xFFFFD04A), Color(0xFFFF7A1A), Color(0xFFE0301A))),
                // A dark halo keeps the letters readable against the flames.
                shadow = Shadow(Color(0xFF1A0500), Offset(0f, 2f), 14f),
            ),
            fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-1).sp,
            modifier = Modifier.drawBehind { flames(clock.value) },
        )
        Text("TheDJ", color = Dj.Text, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
    }
}

/**
 * Flickering flames rising from behind the word: a soft glow, then for each tongue a red-orange outer flame
 * with a yellow core, both fading out at the tip, and a few embers drifting up.
 */
private fun DrawScope.flames(t: Float) {
    val w = size.width
    val h = size.height
    // Flames start at the letters' shoulders so the word itself stays readable.
    val base = h * 0.42f
    val glowR = w * 0.75f
    drawCircle(Brush.radialGradient(listOf(Color(0x66FF5A14), Color.Transparent), Offset(w / 2, base), glowR), glowR, Offset(w / 2, base))
    val tongues = 9
    val half = w / tongues * 0.9f
    for (i in 0 until tongues) {
        val cx = w * (i + 0.5f) / tongues
        val flick = 0.5f + 0.5f * sin(t * (6f + i * 1.1f) + i * 2.1f) * sin(t * 2.7f + i * 0.8f)
        val edge = if (i == 0 || i == tongues - 1) 0.55f else 1f
        val tall = h * (0.45f + 0.45f * flick) * edge
        val sway = sin(t * 3.5f + i * 1.7f) * half * 0.6f
        tongue(cx, base, half, tall, sway, Color(0xFFFF3D0F), Color(0xFFFF9A1F))
        tongue(cx, base, half * 0.5f, tall * 0.6f, sway * 0.7f, Color(0xFFFFB020), Color(0xFFFFF1A8))
    }
    for (i in 0 until 12) {
        val life = (t * 0.55f + i * 0.137f) % 1f
        val x = w * ((i * 0.293f) % 1f) + sin(t * 2f + i) * 8f
        val y = base - h * 0.2f - life * h * 0.9f
        drawCircle(Color(0xFFFFC04A).copy(alpha = (1f - life) * 0.9f), 1.8f + (1f - life) * 2.2f, Offset(x, y), blendMode = BlendMode.Plus)
    }
}

/** One teardrop flame: [root] colour at the base fading through [tip] colour to nothing at the top. */
private fun DrawScope.tongue(cx: Float, base: Float, half: Float, tall: Float, sway: Float, root: Color, tip: Color) {
    val top = Offset(cx + sway, base - tall)
    val path = Path().apply {
        moveTo(cx - half, base)
        cubicTo(cx - half * 1.1f, base - tall * 0.45f, top.x - half * 0.15f, base - tall * 0.75f, top.x, top.y)
        cubicTo(top.x + half * 0.15f, base - tall * 0.75f, cx + half * 1.1f, base - tall * 0.45f, cx + half, base)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(tip.copy(alpha = 0f), tip.copy(alpha = 0.75f), root.copy(alpha = 0.9f)), top.y, base), blendMode = BlendMode.Plus)
}
