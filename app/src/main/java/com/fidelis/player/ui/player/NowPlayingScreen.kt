package com.fidelis.player.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded. Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.lyrics.LyricLine
import com.fidelis.player.integration.lyrics.Lyrics
import com.fidelis.player.integration.spotify. QueueEntry
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val ROW = 64.dp

@Composable
fun NowPlayingScreen(onBack: () -> Unit, vm: NowPlayingViewModel = hiltViewModel()) {
    val snap by vm.state.collectAsState()
    val queue by vm.queue.collectAsState()
    val history by vm.history.collectAsState()
    val art by vm.artwork.collectAsState()
    val liked by vm.liked.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    // One colour from the cover washes the whole player; the look's accent if the cover is grey.
    val wash by animateColorAsState(rememberArtTint(art) ?: Fidelis.purple, tween(700), label = "wash")
    val base = lerp(Color.Black, wash, 0.26f)

    val duration = snap?.durationMs ?: 0L
    var position by remember(snap) { mutableLongStateOf(snap?.positionMs ?: 0L) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(snap) {
        if (snap?.isPaused == false) while (true) {
            delay(500); if (!scrubbing) position = (position + 500).coerceAtMost(duration)
        }
    }

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showLyrics) { showLyrics = false }

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

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = dragY
                    alpha = 1f - (dragY / size.height).coerceIn(0f, 1f) * 0.4f
                    clip = dragY > 0f
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                }
                .nestedScroll(sheet)
                .background(base),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Hero(
                        modifier = Modifier.fillParentMaxSize(),
                        snap = snap, art = art, wash = wash, base = base, liked = liked,
                        position = position, duration = duration,
                        onClose = close, onToggleLike = vm::toggleLike,
                        onPrev = vm::prev, onToggle = vm::togglePlay, onNext = vm::next,
                        onScrub = { f -> scrubbing = true; position = (f * duration).toLong() },
                        onScrubEnd = { vm.seekTo(position); scrubbing = false },
                        onLyrics = { showLyrics = true },
                        onShuffle = vm::reshuffle,
                        onQueue = { scope.launch { list.animateScrollToItem(1) } },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
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

        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 8 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 8 },
        ) {
            LyricsView(lyrics, position, art, wash,
                onSeek = { ms -> position = ms; vm.seekTo(ms) },
                onClose = { showLyrics = false })
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

/** Full-screen first page: cover with a colour wash, title, ❤, progress and controls. */
@Composable
private fun Hero(
    modifier: Modifier,
    snap: SpotifyRemote.Snapshot?, art: Bitmap?, wash: Color, base: Color, liked: Boolean,
    position: Long, duration: Long,
    onClose: () -> Unit, onToggleLike: () -> Unit,
    onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onScrub: (Float) -> Unit, onScrubEnd: () -> Unit,
    onLyrics: () -> Unit, onShuffle: () -> Unit, onQueue: () -> Unit,
) {
    Box(modifier.background(base)) {
        art?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().fillMaxHeight(0.74f),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(wash.copy(alpha = 0.35f), BlendMode.Color))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color. Black.copy(alpha = 0.45f), 0.16f to Color.Transparent,
            0.42f to base.copy(alpha = 0.15f), 0.68f to base.copy(alpha = 0.92f), 1f to base,
        )))

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
                        maxLines = 1, overflow = TextOverflow. Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                val pop by animateFloatAsState(if (liked) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "heart")
                Box(Modifier.pressable(onClick = onToggleLike).size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(if (liked) Icons. Rounded.Favorite else Icons.Rounded.FavoriteBorder,
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
                        if (playing) "Pause" else "Play", tint = Color. Black, modifier = Modifier.size(38.dp))
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
    Box(Modifier.pressable(onClick = onClick).size(size + 24.dp), contentAlignment = Alignment.Center) {
        Icon(icon, cd, tint = tint, modifier = Modifier.size(size))
    }
}

/** Near-black, grainy lyrics page. The current line glows; tap any line to jump to it. */
@Composable
private fun LyricsView(
    lyrics: Lyrics, positionMs: Long, art: Bitmap?, wash: Color,
    onSeek: (Long) -> Unit, onClose: () -> Unit,
) {
    val dots = remember { List(1400) { Offset(Random.nextFloat(), Random.nextFloat()) } }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } } // keep taps off the player underneath
            .background(Color(0xFF050507))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(wash.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.1f), radius = size.width * 1.1f))
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPoints(dots.map { Offset(it.x * size.width, it.y * size.height) }, PointMode.Points,
                Color.White.copy(alpha = 0.07f), strokeWidth = 2.5f)
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Close, "Close lyrics", onClick = onClose)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass)) {
                    art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
            when (lyrics) {
                Lyrics.Loading -> LyricsNote("Finding lyrics…")
                Lyrics.None -> LyricsNote("No lyrics for this song yet.")
                Lyrics.Instrumental -> LyricsNote("♪  Instrumental")
                is Lyrics.Plain -> PlainLyrics(lyrics.lines)
                is Lyrics.Synced -> SyncedLyrics(lyrics.lines, positionMs, onSeek)
            }
            Text("Lyrics from LRCLIB", color = Fidelis.inkFaint, style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        }
    }
}

@Composable
private fun ColumnScope.LyricsNote(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = Fidelis.inkMute, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ColumnScope.PlainLyrics(lines: List<String>) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp)) {
        item {
            Text("NOT SYNCED TO THE SONG", color = Fidelis.inkFaint, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 16.dp))
        }
        items(lines) { l ->
            Text(l.ifBlank { " " }, color = Fidelis.inkSoft,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 30.sp),
                modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun ColumnScope.SyncedLyrics(lines: List<LyricLine>, positionMs: Long, onSeek: (Long) -> Unit) {
    val current = remember(lines, positionMs) { lines.indexOfLast { it.timeMs <= positionMs + 250 } }
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        // The current line sits about a third of the way down.
        LaunchedEffect(current) {
            if (current >= 0 && !state.isScrollInProgress) state.animateScrollToItem(current)
        }
        LazyColumn(state = state, contentPadding = PaddingValues(top = maxHeight * 0.32f, bottom = maxHeight * 0.6f)) {
            itemsIndexed(lines) { i, line ->
                val active = i == current
                val alpha by animateFloatAsState(
                    when { active -> 1f; i < current -> 0.26f; else -> 0.42f }, tween(350), label = "line",
                )
                Text(line.text.ifBlank { "♪" }, color = Color.White.copy(alpha = alpha), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp,
                        shadow = if (active) Shadow(Color.White.copy(alpha = 0.55f), blurRadius = 28f) else null,
                    ),
                    modifier = Modifier.fillMaxWidth().pressable { onSeek(line.timeMs) }
                        .padding(horizontal = 28.dp, vertical = 10.dp))
            }
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
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
