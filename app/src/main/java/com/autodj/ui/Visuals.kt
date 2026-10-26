package com.autodj.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autodj.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/** The club palette from the approved mockup. */
object Dj {
    val Bg = Color(0xFF0D0D10)
    val Surface = Color(0xFF17171C)
    val Border = Color(0xFF2A2A32)
    val Divider = Color(0xFF1C1C22)
    val Text = Color(0xFFF2F2F5)
    val Muted = Color(0xFFB4B4BE)
    val Accent = Color(0xFFFF7A45)
    val AccentBg = Color(0xFF241A15)
    val Blue = Color(0xFF5AA8FF)
    val BlueText = Color(0xFF8EC5FF)
    val BlueDim = Color(0xFF2F5E8F)
    val PhaseIdle = Color(0xFF1F2A3A)
    val BarIdle = Color(0xFF34343C)
    val LightIdle = Color(0xFF3A3A42)
    val Warn = Color(0xFFFFB08F)
    val Groove = Color(0xFF1C1C21)
    val Platter = Color(0xFF111114)
}

@Composable
fun rememberArtwork(path: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { decode(it, 512) } }
    }
    return image
}

private fun decode(path: String, maxPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}

/** Cover art, or a generated cover (stable per track) when there is none. */
@Composable
fun Artwork(path: String?, seed: String, title: String, modifier: Modifier, shape: Shape) {
    val image = rememberArtwork(path)
    val described = modifier.clip(shape).semantics { contentDescription = "Album artwork for $title" }
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = described)
    } else {
        GeneratedArt(seed, described)
    }
}

private val artPalette = listOf(
    Color(0xFF1E2A44), Color(0xFFFF7A45), Color(0xFF5AA8FF), Color(0xFFF2C14E),
    Color(0xFF123B3A), Color(0xFF9BE7D8), Color(0xFF3B1F2B), Color(0xFF2B2440),
)

@Composable
private fun GeneratedArt(seed: String, modifier: Modifier) {
    val h = abs(seed.hashCode())
    val bg = artPalette[h % artPalette.size]
    val c1 = artPalette[(h / 8 + 1) % artPalette.size].let { if (it == bg) Dj.Accent else it }
    val c2 = artPalette[(h / 64 + 2) % artPalette.size].let { if (it == bg) Dj.Blue else it }
    val cx = 0.3f + (h % 40) / 100f
    Canvas(modifier) {
        drawRect(bg)
        drawCircle(c1, radius = size.minDimension * 0.24f, center = Offset(size.width * cx, size.height * 0.38f))
        drawRect(c2, topLeft = Offset(size.width * 0.6f, size.height * 0.56f), size = Size(size.width * 0.24f, size.height * 0.24f))
        val wave = Path().apply {
            moveTo(0f, size.height * 0.74f)
            var x = 0f
            val step = size.width / 4
            while (x < size.width) {
                quadraticBezierTo(x + step / 2, size.height * 0.6f, x + step, size.height * 0.74f)
                x += step
            }
        }
        drawPath(wave, Dj.Text, style = Stroke(width = size.minDimension * 0.03f))
    }
}

/**
 * A record on the platter with the cover art as its centre label. Spins at 33⅓ rpm while
 * [spinning]; [reverse] spins it backwards fast (backspin).
 */
@Composable
fun Vinyl(
    artPath: String?,
    seed: String,
    title: String,
    diameter: Dp,
    spinning: Boolean,
    reverse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning, reverse) {
        if (!spinning) return@LaunchedEffect
        val dir = if (reverse) -1f else 1f
        val period = if (reverse) 600 else 1800
        while (true) {
            angle.animateTo(angle.value + 360f * dir, tween(period, easing = LinearEasing))
            angle.snapTo(angle.value % 360f)
        }
    }
    Box(
        modifier
            .size(diameter)
            .border(6.dp, Dj.Surface, CircleShape)
            .graphicsLayer { rotationZ = angle.value }
            .clip(CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Dj.Platter)
            val step = 3.dp.toPx()
            var r = size.minDimension * 0.24f
            while (r < size.minDimension / 2 - step) {
                drawCircle(Dj.Groove, radius = r, style = Stroke(width = 1f))
                r += step
            }
        }
        Artwork(artPath, seed, title, Modifier.align(Alignment.Center).size(diameter * 0.44f), CircleShape)
        Box(Modifier.align(Alignment.Center).size(diameter * 0.036f).background(Dj.Bg, CircleShape))
    }
}

@Composable
fun Tonearm(modifier: Modifier) {
    Canvas(modifier.size(110.dp, 230.dp)) {
        val s = size.width / 110f
        drawCircle(Dj.Border, radius = 18 * s, center = Offset(86 * s, 26 * s))
        drawCircle(Color(0xFF5C5C66), radius = 7 * s, center = Offset(86 * s, 26 * s))
        drawLine(Dj.Muted, Offset(86 * s, 26 * s), Offset(44 * s, 190 * s), strokeWidth = 5 * s, cap = StrokeCap.Round)
        rotate(14f, pivot = Offset(42 * s, 200 * s)) {
            drawRoundRect(Dj.Muted, topLeft = Offset(30 * s, 186 * s), size = Size(24 * s, 30 * s), cornerRadius = CornerRadius(4 * s))
        }
    }
}

/** Bar-by-bar loudness of the track, played part lit, AI mix-out zone and marker in blue. */
@Composable
fun EnergyMap(track: TrackInfo, positionSec: Double, exitSec: Double?, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val n = 56
        val gap = 2.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val sec = (i + 0.5) / n * track.durationSec
            val bar = track.barAt(sec)
            val e = track.barEnergy.getOrElse(bar) { 0.3f }
            val h = 8.dp.toPx() + e * 46.dp.toPx()
            val color = when {
                sec <= positionSec -> Dj.Accent
                exitSec != null && sec >= exitSec -> Dj.BlueDim
                else -> Dj.BarIdle
            }
            drawRoundRect(color, topLeft = Offset(i * (bw + gap), size.height - h), size = Size(bw, h), cornerRadius = CornerRadius(2.dp.toPx()))
        }
        if (exitSec != null) {
            val x = (exitSec / track.durationSec * size.width).toFloat()
            drawRect(Dj.Blue, topLeft = Offset(x, 0f), size = Size(2.dp.toPx(), size.height))
        }
    }
}

@Composable
fun PlayIcon(color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * 0.29f, size.height * 0.17f)
            lineTo(size.width * 0.83f, size.height * 0.5f)
            lineTo(size.width * 0.29f, size.height * 0.83f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun PauseIcon(color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val w = size.width * 0.21f
        val r = CornerRadius(2f)
        drawRoundRect(color, Offset(size.width * 0.21f, size.height * 0.17f), Size(w, size.height * 0.66f), r)
        drawRoundRect(color, Offset(size.width * 0.58f, size.height * 0.17f), Size(w, size.height * 0.66f), r)
    }
}

@Composable
fun StopIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        drawRoundRect(color, Offset(size.width * 0.2f, size.height * 0.2f), Size(size.width * 0.6f, size.height * 0.6f), CornerRadius(3f))
    }
}

fun formatTime(sec: Double): String {
    val s = max(0, sec.toInt())
    return "%d:%02d".format(s / 60, s % 60)
}
