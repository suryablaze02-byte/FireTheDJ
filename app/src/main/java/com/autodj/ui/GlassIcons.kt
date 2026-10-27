package com.autodj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Line icons for the glass screens, drawn on a 24-unit grid like the mockups' strokes.

/** The Mix tab: a flame. */
@Composable
fun FlameGlyph(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val p = Path().apply {
            moveTo(12 * u, 3 * u)
            cubicTo(13 * u, 6 * u, 16 * u, 7.5f * u, 16 * u, 11.5f * u)
            cubicTo(16 * u, 13.7f * u, 14.2f * u, 15.5f * u, 12 * u, 15.5f * u)
            cubicTo(9.8f * u, 15.5f * u, 8 * u, 13.7f * u, 8 * u, 11.5f * u)
            cubicTo(8 * u, 9.9f * u, 8.6f * u, 8.8f * u, 9.5f * u, 7.8f * u)
            cubicTo(9.8f * u, 9.2f * u, 10.5f * u, 10f * u, 11.5f * u, 10.2f * u)
            cubicTo(11 * u, 8 * u, 11.4f * u, 5.4f * u, 12 * u, 3 * u)
            close()
        }
        val s = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(p, color, style = s)
        drawLine(color, Offset(8 * u, 21 * u), Offset(16 * u, 21 * u), 1.9f * u, StrokeCap.Round)
    }
}

/** The Music tab: two beamed notes. */
@Composable
fun NoteGlyph(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val s = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val stem = Path().apply {
            moveTo(9 * u, 18 * u); lineTo(9 * u, 5 * u); lineTo(21 * u, 3 * u); lineTo(21 * u, 16 * u)
        }
        drawPath(stem, color, style = s)
        drawCircle(color, 3 * u, Offset(6 * u, 18 * u), style = s)
        drawCircle(color, 3 * u, Offset(18 * u, 16 * u), style = s)
    }
}

/** The Now Playing tab: equalizer bars. */
@Composable
fun BarsGlyph(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        listOf(4f to 4f, 8f to 12f, 12f to 18f, 16f to 10f, 20f to 4f).forEach { (x, h) ->
            drawLine(color, Offset(x * u, (12 - h / 2) * u), Offset(x * u, (12 + h / 2) * u), 1.9f * u, StrokeCap.Round)
        }
    }
}

/** Open full screen: two arrows out to the corners. */
@Composable
fun ExpandGlyph(color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val s = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply { moveTo(15 * u, 3 * u); lineTo(21 * u, 3 * u); lineTo(21 * u, 9 * u) }, color, style = s)
        drawPath(Path().apply { moveTo(9 * u, 21 * u); lineTo(3 * u, 21 * u); lineTo(3 * u, 15 * u) }, color, style = s)
        drawLine(color, Offset(21 * u, 3 * u), Offset(14 * u, 10 * u), 1.9f * u, StrokeCap.Round)
        drawLine(color, Offset(3 * u, 21 * u), Offset(10 * u, 14 * u), 1.9f * u, StrokeCap.Round)
    }
}

/** An update to download: an arrow down onto a line. */
@Composable
fun DownloadGlyph(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val s = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(12 * u, 4 * u), Offset(12 * u, 16 * u), 2f * u, StrokeCap.Round)
        drawPath(Path().apply { moveTo(7 * u, 11 * u); lineTo(12 * u, 16 * u); lineTo(17 * u, 11 * u) }, color, style = s)
        drawLine(color, Offset(5 * u, 20 * u), Offset(19 * u, 20 * u), 2f * u, StrokeCap.Round)
    }
}

/** A source's kind as a line icon in its colour, for the Music tab and the TV's Add music row. */
@Composable
fun SourceGlyph(kind: SourceKind, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val c = kind.color
        val s = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            SourceKind.PHONE -> drawPath(Path().apply {
                moveTo(4 * u, 20 * u); lineTo(20 * u, 20 * u); lineTo(22 * u, 18 * u); lineTo(22 * u, 8 * u)
                lineTo(20 * u, 6 * u); lineTo(12 * u, 6 * u); lineTo(10 * u, 4 * u); lineTo(4 * u, 4 * u)
                lineTo(2 * u, 6 * u); lineTo(2 * u, 18 * u); close()
            }, c, style = s)
            SourceKind.YOUTUBE -> {
                drawCircle(c, 10 * u, Offset(12 * u, 12 * u), style = s)
                drawPath(Path().apply { moveTo(10 * u, 8 * u); lineTo(16 * u, 12 * u); lineTo(10 * u, 16 * u); close() }, c, style = s)
            }
            SourceKind.LINK -> {
                drawLine(c, Offset(9 * u, 15 * u), Offset(15 * u, 9 * u), 1.8f * u, StrokeCap.Round)
                drawRoundRect(c, Offset(11 * u, 3 * u), androidx.compose.ui.geometry.Size(10 * u, 7 * u),
                    androidx.compose.ui.geometry.CornerRadius(3.5f * u), style = s)
                drawRoundRect(c, Offset(3 * u, 14 * u), androidx.compose.ui.geometry.Size(10 * u, 7 * u),
                    androidx.compose.ui.geometry.CornerRadius(3.5f * u), style = s)
            }
        }
    }
}
