package com.fidelis.player.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation. Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.IconCircle
import com.fidelis.player.ui.theme.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen first page of the player: the cover with an ambient glow, title, ❤, progress and controls.
 * Tap the cover to see it whole (it grows back after 5s or another tap); swipe it for next / previous.
 */
@Composable
internal fun Hero(
    modifier: Modifier,
    snap: SpotifyRemote.Snapshot?, art: Bitmap?, wash: Color, base: Color, liked: Boolean,
    position: Long, duration: Long,
    onClose: () -> Unit, onToggleLike: () -> Unit,
    onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onScrub: (Float) -> Unit, onScrubEnd: () -> Unit,
    onLyrics: () -> Unit, onShuffle: () -> Unit, onQueue: () -> Unit,
) {
    val img = remember(art) { art?.asImageBitmap() }
    val scope = rememberCoroutineScope()
    val swipeAt = with(LocalDensity.current) { 90.dp.toPx() }
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrev)

    var compact by remember { mutableStateOf(false) }
    LaunchedEffect(compact) { if (compact) { delay(5_000); compact = false } }
    LaunchedEffect(snap?.uri) { compact = false }
    val t by animateFloatAsState(if (compact) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "compact")
    var swipeX by remember { mutableFloatStateOf(0f) }

    Box(modifier.background(base)) {
        if (img != null) {
            // Ambient light: the cover, heavily blurred, glows under the title and controls.
            Image(img, null,
                Modifier.fillMaxSize().blur(70.dp, BlurredEdgeTreatment.Rectangle),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(wash.copy(alpha = 0.3f), BlendMode.Color))
            // The big cover, fading softly into the glow at the bottom.
            Image(img, null,
                Modifier.fillMaxWidth().fillMaxHeight(0.86f)
                    .graphicsLayer {
                        alpha = 1f - t
                        translationX = swipeX * 0.6f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn)
                    },
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(wash.copy(alpha = 0.22f), BlendMode.Color))
        }
        // Keeps the status bar and the controls readable without hiding the glow.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.4f), 0.14f to Color.Transparent, 0.5f to Color.Transparent,
            0.74f to base.copy(alpha = 0.5f), 1f to base.copy(alpha = 0.82f),
        )))

        // Tapped: the whole cover as a card.
        if (img != null && t > 0f) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f).padding(top = 72.dp), contentAlignment = Alignment.Center) {
                Image(img, "Album artwork",
                    Modifier.fillMaxWidth(0.8f).aspectRatio(1f)
                        .graphicsLayer {
                            alpha = t; scaleX = 0.9f + 0.1f * t; scaleY = 0.9f + 0.1f * t
                            translationX = swipeX * 0.6f
                        }
                        .shadow(28.dp, RoundedCornerShape(20.dp), ambientColor = wash, spotColor = wash)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop)
            }
        }

        // Gestures on the cover: tap to toggle the full view, swipe for next / previous.
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.62f)
                .pointerInput(Unit) { detectTapGestures { compact = !compact } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val x = swipeX
                            if (x < -swipeAt) next() else if (x > swipeAt) prev()
                            scope.launch {
                                animate(x, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> swipeX = v }
                            }
                        },
                        onDragCancel = { swipeX = 0f },
                    ) { change, dx -> change.consume(); swipeX += dx }
                },
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.KeyboardArrowDown, "Collapse", onClick = onClose)
            Text("NOW PLAYING", color = Fidelis.inkSoft, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(snap?.title?.ifBlank { null } ?: "Nothing playing", color = Color.White,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(snap?.artist.orEmpty(), color = Fidelis.inkSoft, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                val pop by animateFloatAsState(if (liked) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "heart")
                Box(Modifier.pressable(onClick = onToggleLike).size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                        tint = if (liked) Fidelis.pink else Color.White,
                        modifier = Modifier.size(28.dp).graphicsLayer { scaleX = pop; scaleY = pop })
                }
            }
            Spacer(Modifier.height(18.dp))
            SeekBar(
                fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onSeek = onScrub, onDone = onScrubEnd,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(position), color = Fidelis.inkSoft, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("-" + fmt((duration - position).coerceAtLeast(0)), color = Fidelis.inkSoft,
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                PlainIcon(Icons.Rounded.SkipPrevious, "Previous", 40.dp, onPrev)
                val playing = snap?.isPaused == false
                Box(
                    Modifier.pressable(onClick = onToggle).size(76.dp)
                        .shadow(24.dp, CircleShape, ambientColor = wash, spotColor = wash)
                        .clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(38.dp))
                }
                PlainIcon(Icons.Rounded.SkipNext, "Next", 40.dp, onNext)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PlainIcon(Icons.Rounded.ChatBubbleOutline, "Lyrics", 24.dp, onLyrics, Fidelis.inkSoft)
                PlainIcon(Icons.Rounded.Shuffle, "Shuffle Up next", 24.dp, onShuffle, Fidelis.inkSoft)
                PlainIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", 24.dp, onQueue, Fidelis.inkSoft)
            }
        }
    }
}

/** Thin white progress line without a thumb; it thickens while you drag. Tap or drag to seek. */
@Composable
private fun SeekBar(fraction: Float, onSeek: (Float) -> Unit, onDone: () -> Unit) {
    val seek by rememberUpdatedState(onSeek)
    val done by rememberUpdatedState(onDone)
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val h by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "bar")
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { o -> seek((o.x / width).coerceIn(0f, 1f)); done() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; seek((o.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; done() },
                    onDragCancel = { dragging = false; done() },
                ) { change, _ -> change.consume(); seek((change.position.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(fraction).height(h).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun PlainIcon(icon: ImageVector, cd: String, size: Dp, onClick: () -> Unit, tint: Color = Color.White) {
    Box(Modifier.pressable(onClick = onClick).size(size + 24.dp), contentAlignment = Alignment. Center) {
        Icon(icon, cd, tint = tint, modifier = Modifier.size(size))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
