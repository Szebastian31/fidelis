package com.fidelis.player.ui.theme

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max

private val SLEEVE = Color(0xFF111113)
private val DISC = Color(0xFF1C1D20)
private val RUNOUT = Color(0xFF17181B)
private val LABEL = Color(0xFFD6D6D2)
private val INK = Color(0xFF3A3A3D)

/** The cover for songs that have none: a record with a blank label, the song's name printed on it. */
class VinylPainter(private val title: String, private val artist: String) : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() = drawVinyl(title, artist)
}

/** A vinyl cover for this song, or null when there's no song to name. */
@Composable
fun rememberVinyl(title: String, artist: String): Painter? =
    remember(title, artist) { if (title.isBlank()) null else VinylPainter(title, artist) }

fun DrawScope.drawVinyl(title: String, artist: String) {
    drawRect(SLEEVE)
    val c = center
    // Fills the space like a cropped cover: on a tall screen the record runs off the sides.
    val r = size.maxDimension / 2f * 0.98f
    drawCircle(DISC, r, c)
    val line = max(1f, r * 0.0035f)
    var k = 0.97f
    var i = 0
    while (k > 0.42f) {
        drawCircle(Color.White.copy(alpha = if (i % 4 == 0) 0.075f else 0.035f), r * k, c, style = Stroke(line))
        k -= 0.011f; i++
    }
    drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent,
        Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), c), r, c)
    drawCircle(RUNOUT, r * 0.4f, c)
    val lr = r * 0.33f
    drawCircle(LABEL, lr, c)
    drawCircle(Color.Black.copy(alpha = 0.10f), lr * 0.72f, c, style = Stroke(max(1f, r * 0.006f)))
    drawCircle(Color.White.copy(alpha = 0.45f), lr * 0.70f, c, style = Stroke(max(1f, r * 0.003f)))
    // Only big enough covers get the song's name; on a small one it would be a smudge.
    if (lr > 60f) {
        val nc = drawContext.canvas.nativeCanvas
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK.toArgb(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = lr * 0.14f
        }
        val w = lr * 1.3f
        nc.drawText(TextUtils.ellipsize(title, p, w, TextUtils.TruncateAt.END).toString(), c.x, c.y - lr * 0.32f, p)
        if (artist.isNotBlank()) {
            p.typeface = Typeface.DEFAULT; p.textSize = lr * 0.11f; p.color = INK.copy(alpha = 0.75f).toArgb()
            nc.drawText(TextUtils.ellipsize(artist, p, w, TextUtils.TruncateAt.END).toString(), c.x, c.y + lr * 0.42f, p)
        }
    }
    drawCircle(Color(0xFFF4F4F2), r * 0.03f, c)
}
