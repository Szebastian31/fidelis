package com.fidelis.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit. Dp
import androidx.compose.ui.unit.dp
import com.fidelis.player.integration.spotify.SpotifyRemote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Height of the floating mini player + tab bar, so lists scroll under the glass but end above it. */
object ChromeInsets { var bottom by mutableStateOf(0.dp) }

/** Bottom padding for any scrolling screen. */
@Composable
fun chromeBottom(): Dp = ChromeInsets.bottom + 12.dp

/**
 * Glass mini player. Tap to open the player; swipe left for the next song, right for the previous.
 * The progress line follows Spotify's live position.
 */
@Composable
fun SwipeMini(
    snap: SpotifyRemote.Snapshot, art: Bitmap?,
    onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val tint = rememberArtTint(art)
    val fill = lerp(Fidelis.bg1, tint ?: Fidelis.bg1, 0.35f)
    val img = remember(art) { art?.asImageBitmap() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val swipeAt = with(LocalDensity.current) { 72.dp.toPx() }
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrev)
    var position by remember(snap) { mutableLongStateOf(snap.livePosition()) }
    LaunchedEffect(snap) { while (!snap.isPaused) { delay(500); position = snap.livePosition() } }
    val progress = if (snap.durationMs > 0) (position.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier.fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offset.value
                        val w = size.width.toFloat()
                        scope.launch {
                            if (abs(x) > swipeAt) {
                                haptic.performHapticFeedback(HapticFeedbackType. TextHandleMove)
                                if (x < 0) next() else prev()
                                // Slide out, then the next song slides in from the other side.
                                offset.animateTo(if (x < 0) -w * 0.6f else w * 0.6f, tween(140))
                                offset.snapTo(if (x < 0) w * 0.35f else -w * 0.35f)
                            }
                            offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dx -> change.consume(); scope.launch { offset.snapTo(offset.value + dx) } }
            }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
            }
            .pressable(onClick = onOpen)
            .clip(shape)
            .background(fill.copy(alpha = 0.62f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))), shape),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Fidelis.glass)) {
                // Songs without a cover get the vinyl one.
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else rememberVinyl(snap.title, snap.artist)?.let { Image(it, null, Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(snap.title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snap.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconCircle(
                if (snap.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                if (snap.isPaused) "Play" else "Pause", onToggle, size = 44.dp, filled = true,
            )
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 3.dp)
                .fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White.copy(alpha = 0.85f)))
        }
    }
}
