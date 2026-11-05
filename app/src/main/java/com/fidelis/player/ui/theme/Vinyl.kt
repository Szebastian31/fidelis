package com.fidelis.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter. Painter
import kotlin.math.max

private val SLEEVE = Color(0xFF111113)
private val DISC = Color(0xFF1C1D20)
private val RUNOUT = Color(0xFF17181B)
private val LABEL = Color(0xFFD6D6D2)

/** The cover for songs that have none: a plain record with a blank label. */
@Suppress("UNUSED_PARAMETER")
class VinylPainter(title: String = "", artist: String = "") : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() = drawVinyl()
}

/** The vinyl cover, for any song or list row without art. */
@Composable
fun rememberVinyl(): Painter = remember { VinylPainter() }

/** The vinyl cover for the playing song, or null when nothing is playing. */
@Composable
fun rememberVinyl(title: String, @Suppress("UNUSED_PARAMETER") artist: String): Painter? {
    val vinyl = rememberVinyl()
    return if (title.isBlank()) null else vinyl
}

@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawVinyl(title: String = "", artist: String = "") {
    drawRect(SLEEVE)
    val c = center
    // Fills the space like a cropped cover: on a tall screen the record runs off the sides.
    val r = size.maxDimension / 2f * 0.98f
    drawCircle(DISC, r, c)
    val line = max(1f, r * 0.0035f)
    // Grooves stay a few pixels apart, so a small cover in a list doesn't turn into a grey smudge.
    val step = max(0.011f, 2.5f / r)
    var k = 0.97f
    var i = 0
    while (k > 0.42f) {
        drawCircle(Color.White.copy(alpha = if (i % 4 == 0) 0.075f else 0.035f), r * k, c, style = Stroke(line))
        k -= step; i++
    }
    drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent,
        Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), c), r, c)
    drawCircle(RUNOUT, r * 0.4f, c)
    val lr = r * 0.33f
    drawCircle(LABEL, lr, c)
    drawCircle(Color.Black.copy(alpha = 0.10f), lr * 0.72f, c, style = Stroke(max(1f, r * 0.006f)))
    drawCircle(Color.White.copy(alpha = 0.45f), lr * 0.70f, c, style = Stroke(max(1f, r * 0.003f)))
    drawCircle(Color(0xFFF4F4F2), r * 0.03f, c)
}
