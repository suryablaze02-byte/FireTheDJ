package com.autodj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The ways music gets into the library, each with its own look so they can't be confused. */
enum class SourceKind(val title: String, val hint: String, val color: Color) {
    PHONE("This phone", "a music folder", Color(0xFFF2C14E)),
    YOUTUBE("YT Music", "curated playlists", Color(0xFFFF4E45)),
    SPOTIFY("Spotify", "played from YouTube", Color(0xFF1ED760)),
    /** The door to YT Music and Spotify. */
    STREAM("Stream", "YT Music or Spotify", Color(0xFFFF6A5C)),
    LINK("Link", "other services", Color(0xFF5AA8FF)),
}

/** A library header tile: tinted icon, a name and a hint of what it adds. */
@Composable
fun SourceTile(kind: SourceKind, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().background(Dj.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, kind.color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Add music: ${kind.title}", onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(28.dp).background(kind.color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                when (kind) {
                    SourceKind.PHONE -> folderGlyph(kind.color)
                    SourceKind.YOUTUBE, SourceKind.STREAM -> playGlyph(kind.color)
                    SourceKind.SPOTIFY -> listGlyph(kind.color)
                    SourceKind.LINK -> linkGlyph(kind.color)
                }
            }
        }
        Text(kind.title, color = Dj.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(kind.hint, color = Dj.Muted, fontSize = 10.sp, maxLines = 1)
    }
}

/** Clear, kept quiet so it never reads as another way to add music. */
@Composable
fun ClearTile(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().alpha(if (enabled) 1f else 0.4f).background(Dj.Bg, RoundedCornerShape(14.dp))
            .border(1.dp, Dj.Border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Clear the library", onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) { binGlyph(Dj.Muted) }
        }
        Text("Clear", color = Dj.Muted, fontSize = 13.sp, maxLines = 1)
    }
}

private fun DrawScope.playGlyph(c: Color) {
    val w = size.width
    // A play triangle, nudged right so it looks centred.
    val play = Path().apply {
        moveTo(w * 0.24f, w * 0.10f); lineTo(w * 0.92f, w * 0.50f); lineTo(w * 0.24f, w * 0.90f); close()
    }
    drawPath(play, c)
}

/** Three lines of a list with a note at the end: a playlist. */
private fun DrawScope.listGlyph(c: Color) {
    val w = size.width
    val s = w * 0.12f
    drawLine(c, Offset(w * 0.06f, w * 0.18f), Offset(w * 0.7f, w * 0.18f), s, StrokeCap.Round)
    drawLine(c, Offset(w * 0.06f, w * 0.42f), Offset(w * 0.7f, w * 0.42f), s, StrokeCap.Round)
    drawLine(c, Offset(w * 0.06f, w * 0.66f), Offset(w * 0.42f, w * 0.66f), s, StrokeCap.Round)
    drawLine(c, Offset(w * 0.84f, w * 0.4f), Offset(w * 0.84f, w * 0.82f), s, StrokeCap.Round)
    drawCircle(c, w * 0.13f, Offset(w * 0.72f, w * 0.84f))
}

private fun DrawScope.folderGlyph(c: Color) {
    val w = size.width
    val tab = Path().apply {
        moveTo(0f, w * 0.18f); lineTo(w * 0.38f, w * 0.18f); lineTo(w * 0.48f, w * 0.30f); lineTo(0f, w * 0.30f); close()
    }
    drawPath(tab, c)
    drawRoundRect(c, Offset(0f, w * 0.28f), Size(w, w * 0.58f), CornerRadius(w * 0.08f))
}

private fun DrawScope.linkGlyph(c: Color) {
    val w = size.width
    val stroke = Stroke(w * 0.13f, cap = StrokeCap.Round)
    // Two rounded links at 45°, overlapping in the middle.
    rotate45 {
        drawRoundRect(c, Offset(w * 0.02f, w * 0.34f), Size(w * 0.56f, w * 0.32f), CornerRadius(w * 0.16f), style = stroke)
        drawRoundRect(c, Offset(w * 0.42f, w * 0.34f), Size(w * 0.56f, w * 0.32f), CornerRadius(w * 0.16f), style = stroke)
    }
}

private fun DrawScope.binGlyph(c: Color) {
    val w = size.width
    val s = w * 0.1f
    drawLine(c, Offset(w * 0.08f, w * 0.2f), Offset(w * 0.92f, w * 0.2f), s, StrokeCap.Round)
    drawLine(c, Offset(w * 0.38f, w * 0.06f), Offset(w * 0.62f, w * 0.06f), s, StrokeCap.Round)
    val body = Path().apply {
        moveTo(w * 0.18f, w * 0.3f); lineTo(w * 0.82f, w * 0.3f); lineTo(w * 0.74f, w * 0.96f); lineTo(w * 0.26f, w * 0.96f); close()
    }
    drawPath(body, c, style = Stroke(s))
    drawLine(c, Offset(w * 0.42f, w * 0.44f), Offset(w * 0.42f, w * 0.82f), s * 0.8f, StrokeCap.Round)
    drawLine(c, Offset(w * 0.58f, w * 0.44f), Offset(w * 0.58f, w * 0.82f), s * 0.8f, StrokeCap.Round)
}

private inline fun DrawScope.rotate45(block: DrawScope.() -> Unit) {
    drawContext.transform.rotate(-45f, center)
    block()
    drawContext.transform.rotate(45f, center)
}
