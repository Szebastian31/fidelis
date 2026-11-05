package com.fidelis.player.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime. Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** One line of text. When it doesn't fit it scrolls, with a gap before it starts again, like Spotify. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val max = constraints.maxWidth
        val long = remember(text, style, max) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width > max }
        Text(text, color = color, style = style, maxLines = 1, softWrap = false,
            modifier = if (long) Modifier.fadedEdges().basicMarquee(
                iterations = Int.MAX_VALUE, initialDelayMillis = 2_000, repeatDelayMillis = 2_000,
                spacing = MarqueeSpacing(56.dp), velocity = 34.dp,
            ) else Modifier)
    }
}

/** Soft edges, so scrolling text fades in and out rather than being cut off. */
private fun Modifier.fadedEdges() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (14.dp.toPx() / size.width).coerceIn(0f, 0.2f)
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn)
    }
