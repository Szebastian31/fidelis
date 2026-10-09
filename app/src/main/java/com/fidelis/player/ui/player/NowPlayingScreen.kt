package com.fidelis.player.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons. Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.QueueEntry
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.delay

private val ROW = 64.dp

@Composable
fun NowPlayingScreen(onBack: () -> Unit, vm: NowPlayingViewModel = hiltViewModel()) {
    val snap by vm.state.collectAsState()
    val queue by vm.queue.collectAsState()
    val history by vm.history.collectAsState()
    val art by vm.artwork.collectAsState()
    val density = LocalDensity.current

    val duration = snap?.durationMs ?: 0L
    var position by remember(snap) { mutableLongStateOf(snap?.positionMs ?: 0L) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(snap) {
        if (snap?.isPaused == false) while (true) {
            delay(500); if (!scrubbing) position = (position + 500).coerceAtMost(duration)
        }
    }

    // ---- Swipe down to close ----
    var dragY by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)
    val closeAt = with(density) { 140.dp.toPx() }
    val close = { if (!closing) { closing = true; currentOnBack() } }
    val sheet = remember(closeAt) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dragY > 0f && available.y < 0f) {
                    val before = dragY
                    dragY = (dragY + available.y).coerceAtLeast(0f)
                    return Offset(0f, dragY - before)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.Drag) {
                    dragY += available.y * 0.7f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY <= 0f) return Velocity.Zero
                if (dragY > closeAt || available.y > 2000f) close()
                else animate(dragY, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragY = v }
                return available
            }
        }
    }

    // ---- Drag to reorder ----
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(density) { ROW.toPx() }
    var sheetEntry by remember { mutableStateOf<QueueEntry?>(null) }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                translationY = dragY
                alpha = 1f - (dragY / size.height).coerceIn(0f, 1f) * 0.4f
                clip = dragY > 0f
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            }
            .nestedScroll(sheet),
    ) {
        FidelisBackground {
            Box(Modifier.fillMaxSize()) {
                art?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize().blur(80.dp).alpha(0.5f),
                        contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Fidelis.bg0.copy(alpha = 0.3f), Fidelis.bg0.copy(alpha = 0.92f)))
                    ))
                }

                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { PlayerHeader(snap, art, position, duration, vm, close,
                        onScrub = { scrubbing = true; position = it },
                        onScrubEnd = { vm.seekTo(position); scrubbing = false }) }

                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (queue.isEmpty()) "UP NEXT" else "UP NEXT · ${queue.size}",
                                color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f))
                            if (queue.size > 1) FidelisChip("Shuffle", false, vm::reshuffle)
                            if (queue.isNotEmpty()) { Spacer(Modifier.width(8.dp)); FidelisChip("Clear", false, vm::clear) }
                        }
                    }

                    if (queue.isEmpty()) {
                        item {
                            Text("Nothing queued. Use ⋮ › Play next or Add to queue on any song.",
                                color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        }
                    }

                    items(queue, key = { it.id }) { e ->
                        val dragging = draggingId == e.id
                        QueueRow(
                            e = e, dragging = dragging,
                            modifier = Modifier.zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                            onClick = { vm.playAt(vm.indexOf(e.id)) },
                            onMore = { sheetEntry = e },
                            handle = Modifier.pointerInput(e.id) {
                                detectDragGestures(
                                    onDragStart = { draggingId = e.id; dragOffset = 0f },
                                    onDragEnd = { draggingId = null; dragOffset = 0f },
                                    onDragCancel = { draggingId = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val i = vm.indexOf(e.id)
                                        if (dragOffset > rowPx / 2 && i < vm.queue.value.lastIndex) {
                                            vm.move(i, i + 1); dragOffset -= rowPx
                                        } else if (dragOffset < -rowPx / 2 && i > 0) {
                                            vm.move(i, i - 1); dragOffset += rowPx
                                        }
                                    },
                                )
                            },
                        )
                    }

                    if (history.isNotEmpty()) {
                        item { SectionLabel("Recently played", Modifier.padding(horizontal = 24.dp)) }
                        items(history.asReversed(), key = { "h-${it.uri}" }) { t ->
                            TrackRow(title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                                onClick = { vm.playNow(t) })
                        }
                    }
                }
            }
        }
    }

    sheetEntry?.let { e ->
        TrackSheet(
            title = e.track.title, subtitle = e.track.artist, imageUrl = e.track.imageUrl,
            actions = listOf(
                SheetAction("Play now", Icons.Rounded.PlayArrow) { vm.playAt(vm.indexOf(e.id)) },
                SheetAction("Move to top", Icons.Rounded.VerticalAlignTop) { vm.move(vm.indexOf(e.id), 0) },
                SheetAction("Remove from queue", Icons.Rounded.RemoveCircleOutline) { vm.remove(vm.indexOf(e.id)) },
            ),
            onDismiss = { sheetEntry = null },
        )
    }
}

@Composable
private fun PlayerHeader(
    snap: com.fidelis.player.integration.spotify.SpotifyRemote.Snapshot?,
    art: android.graphics.Bitmap?,
    position: Long, duration: Long, vm: NowPlayingViewModel, close: () -> Unit,
    onScrub: (Long) -> Unit, onScrubEnd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
            .size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Fidelis.inkFaint))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.KeyboardArrowDown, "Collapse", onClick = close)
            Text("NOW PLAYING", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 40.dp))
        }

        Spacer(Modifier.height(28.dp))
        val shape = RoundedCornerShape(28.dp)
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(36.dp, shape).clip(shape).background(Fidelis.glass)) {
            art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }

        Spacer(Modifier.height(28.dp))
        Text(snap?.title?.ifBlank { null } ?: "Nothing playing", color = Fidelis.ink,
            style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(snap?.artist ?: "", color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)

        Spacer(Modifier.height(16.dp))
        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { onScrub((it * duration).toLong()) },
            onValueChangeFinished = onScrubEnd,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Fidelis.violet,
                inactiveTrackColor = Fidelis.glassStrong),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(position), color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(fmt(duration), color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.SkipPrevious, "Previous", onClick = vm::prev, size = 56.dp)
            IconCircle(
                if (snap?.isPaused == false) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (snap?.isPaused == false) "Pause" else "Play",
                onClick = vm::togglePlay, size = 80.dp, filled = true,
            )
            IconCircle(Icons. Rounded.SkipNext, "Next", onClick = vm::next, size = 56.dp)
        }
    }
}

@Composable
private fun QueueRow(
    e: QueueEntry, dragging: Boolean, modifier: Modifier,
    onClick: () -> Unit, onMore: () -> Unit, handle: Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(ROW)
            .background(if (dragging) Fidelis.glassStrong else Color.Transparent)
            .pressable(onClick = onClick).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass)) {
            e.track.imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.track.title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow. Ellipsis)
            Text(e.track.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).pressable(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, "More", tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
        }
        Box(handle.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DragHandle, "Reorder", tint = if (dragging) Fidelis.violet else Fidelis.inkMute,
                modifier = Modifier.size(22.dp))
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
