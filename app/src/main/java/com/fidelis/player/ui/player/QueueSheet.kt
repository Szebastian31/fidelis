package com.fidelis.player.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform. LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.QueueEntry
import com.fidelis.player.ui.theme.*

private val QROW = 64.dp

/**
 * The queue as a sheet from the bottom: up to five songs you've played, the one playing now,
 * then what's next. Swipe it down by the handle to hide it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(vm: NowPlayingViewModel, art: Bitmap?, wash: Color, onMore: (QueueEntry) -> Unit, onDismiss: () -> Unit) {
    val snap by vm.state.collectAsState()
    val queue by vm.queue.collectAsState()
    val history by vm.history.collectAsState()
    val queueFrom by vm.source.collectAsState()
    val spotifyCtx by vm.spotifyContext.collectAsState()
    val contextMode by vm.contextMode.collectAsState()
    val recent = remember(history) { history.takeLast(5) }
    // Opens on the song playing now, with the ones you've played just above it.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (recent.isEmpty()) 0 else recent.size + 1)
    val rowPx = with(LocalDensity.current) { QROW.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = lerp(Color(0xFF0D0D11), wash, 0.16f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", color = Fidelis.ink, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                val total = queue.sumOf { it.track.durationMs }
                Text(
                    when {
                        queue.isEmpty() -> "Nothing up next"
                        total > 0 -> "${queue.size} up next · ${formatTotal(total)}"
                        else -> "${queue.size} up next"
                    },
                    color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                )
            }
            if (queue.size > 1) FidelisChip("Shuffle", false, vm::reshuffle)
            if (queue.isNotEmpty()) { Spacer(Modifier.width(8.dp)); FidelisChip("Clear", false, vm::clear) }
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.88f), state = list,
            contentPadding = PaddingValues(bottom = 24.dp)) {
            if (recent.isNotEmpty()) {
                item(key = "h-label") { SheetLabel("Previously played") }
                itemsIndexed(recent, key = { i, t -> "h$i-${t.uri}" }) { _, t ->
                    SongRow(t.title, t.artist, t.imageUrl, null, Modifier.alpha(0.55f), onClick = { vm.playNow(t) })
                }
            }
            item(key = "now-label") { SheetLabel("Now playing") }
            item(key = "now") {
                val s = snap
                if (s == null || s.title.isBlank()) {
                    Text("Nothing playing", color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                } else {
                    SongRow(s.title, s.artist, null, art,
                        Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(16.dp)).background(wash.copy(alpha = 0.28f)),
                        now = if (s.isPaused) "Paused" else "Playing", onClick = {})
                }
            }
            if (queue.isEmpty()) {
                item(key = "empty") {
                    Text(if (contextMode) "Spotify picks what plays next${spotifyCtx?.let { " from $it" } ?: ""}. Anything you add plays first, then it carries on."
                        else "Nothing queued. Tap ⋮ on any song, then Play next or Add to queue.",
                        color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }
            itemsIndexed(queue, key = { _, it -> it.id }) { qi, e ->
                Column {
                    if (qi == 0 || queue[qi - 1].manual != e.manual) SheetLabel(
                        when {
                            e.manual -> "Next in queue"
                            queueFrom != null -> "Next from: $queueFrom"
                            contextMode && spotifyCtx != null -> "Next from: $spotifyCtx"
                            else -> "Next up"
                        },
                    )
                    val dragging = draggingId == e.id
                    EntryRow(
                        e = e, dragging = dragging,
                        modifier = Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                        onClick = { vm.playAt(vm.indexOf(e.id)) },
                        onMore = { onMore(e) },
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
            }
            if (contextMode && queue.isNotEmpty()) item(key = "then") {
                SheetLabel("Then Spotify carries on${spotifyCtx?.let { " with $it" } ?: ""}")
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))
}

/** A song you've played, or the one playing now. Songs without a cover get the vinyl one. */
@Composable
private fun SongRow(
    title: String, artist: String, imageUrl: String?, bitmap: Bitmap?, modifier: Modifier,
    now: String? = null, onClick: () -> Unit,
) {
    Row(modifier.fillMaxWidth().height(QROW).pressable(onClick = onClick).padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(title, artist, imageUrl, bitmap)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (now != null) MarqueeText(title, Fidelis.ink, MaterialTheme.typography.titleSmall, Modifier.fillMaxWidth())
            else Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (now != null) Text(now, color = Fidelis.violet, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Cover(title: String, artist: String, imageUrl: String?, bitmap: Bitmap?) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass)) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else rememberVinyl(title, artist).let { vinyl ->
            AsyncImage(imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = vinyl, fallback = vinyl)
        }
    }
}

@Composable
private fun EntryRow(
    e: QueueEntry, dragging: Boolean, modifier: Modifier,
    onClick: () -> Unit, onMore: () -> Unit, handle: Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(QROW)
            .background(if (dragging) Fidelis.glassStrong else Color.Transparent)
            .pressable(onClick = onClick).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(e.track.title, e.track.artist, e.track.imageUrl, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.track.title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (e.added) "✦ Suggested · ${e.track.artist}" else e.track.artist,
                color = if (e.added) Fidelis.violet else Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (e.track.durationMs > 0) Text(formatDuration(e.track.durationMs), color = Fidelis.inkFaint, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.size(40.dp).pressable(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, "More", tint = Fidelis.inkMute, modifier = Modifier.size(20.dp))
        }
        Box(handle.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DragHandle, "Reorder", tint = if (dragging) Fidelis.violet else Fidelis.inkMute, modifier = Modifier.size(22.dp))
        }
    }
}
