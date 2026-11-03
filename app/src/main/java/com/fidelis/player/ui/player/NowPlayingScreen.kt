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
    val queueFrom by vm.source.collectAsState()
    val spotifyCtx by vm.spotifyContext.collectAsState()
    val contextMode by vm.contextMode.collectAsState()
    var soundOpen by remember { mutableStateOf(false) }
    if (soundOpen) SoundSheet(onDismiss = { soundOpen = false })
    var addOpen by remember { mutableStateOf(false) }
    snap?.uri?.takeIf { addOpen }?.let { u ->
        AddToPlaylistSheet(snap?.title.orEmpty(), snap?.artist.orEmpty(), art, u, onDismiss = { addOpen = false })
    }
    var shareOpen by remember { mutableStateOf(false) }
    snap?.uri?.takeIf { shareOpen }?.let { u ->
        ShareSongSheet(snap?.title.orEmpty(), snap?.artist.orEmpty(), art, u, onDismiss = { shareOpen = false })
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val actions = LocalTrackActions.current
    var devicesOpen by remember { mutableStateOf(false) }
    if (devicesOpen) PlayingOnSheet(onDismiss = { devicesOpen = false })

    // One colour from the cover washes the whole player; the look's accent if the cover is grey.
    val wash by animateColorAsState(rememberArtTint(art) ?: Fidelis.purple, tween(700), label = "wash")
    val base = lerp(Color.Black, wash, 0.26f)
    // The queue gets the same living light as the rest of the app, in the cover's colour.
    val skyTime = rememberSkyTime()
    val alive by animateFloatAsState(if (snap?.isPaused == false) 1f else 0f, tween(1800), label = "alive")

    val duration = snap?.durationMs ?: 0L
    var position by remember(snap) { mutableLongStateOf(snap?.livePosition() ?: 0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var seekAt by remember { mutableLongStateOf(0L) } // just seeked: hold the bar until Spotify catches up
    LaunchedEffect(snap) {
        if (snap?.isPaused == false) while (true) {
            delay(250); if (!scrubbing && android.os.SystemClock.elapsedRealtime() - seekAt > 1500) position = snap?.livePosition() ?: 0L
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
                .background(base)
                .drawBehind {
                    val reveal = if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / size.height).coerceIn(0f, 1f)
                    drawLivingGlow(skyTime.value, wash, alive, reveal)
                },
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                item {
                    Hero(
                        modifier = Modifier.fillParentMaxSize(),
                        snap = snap, art = art, wash = wash, base = base, liked = liked,
                        next = queue.firstOrNull()?.track, prev = history.lastOrNull(),
                        position = position, duration = duration,
                        onClose = close, onToggleLike = vm::toggleLike,
                        onPrev = vm::prev, onToggle = vm::togglePlay, onNext = vm::next,
                        onScrub = { f -> scrubbing = true; position = (f * duration).toLong() },
                        onScrubEnd = { vm.seekTo(position); seekAt = android.os.SystemClock.elapsedRealtime(); scrubbing = false },
                        onLyrics = { showLyrics = true },
                        onShuffle = vm::reshuffle,
                        onArtist = { snap?.artist?.takeIf { it.isNotBlank() }?.let { a -> actions?.openArtist(a) } },
                        onRadio = { snap?.let { s -> actions?.songRadio(s.title, s.artist) } },
                        onDevices = { devicesOpen = true },
                    onSound = { soundOpen = true },
                    onAddToPlaylist = { addOpen = true },
                    onShare = { shareOpen = true },
                        onQueue = { scope.launch { list.animateScrollToItem(1) } },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (queue.isEmpty()) "UP NEXT" else "UP NEXT · ${queue.size}" + queue.sumOf { it.track.durationMs }.let { if (it > 0) " · " + formatTotal(it).uppercase() else "" },
                            color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        if (queue.size > 1) FidelisChip("Shuffle", false, vm::reshuffle)
                        if (queue.isNotEmpty()) { Spacer(Modifier.width(8.dp)); FidelisChip("Clear", false, vm::clear) }
                    }
                }
                if (queue.isEmpty()) {
                    item {
                    Text(if (contextMode) "Spotify picks what plays next${spotifyCtx?.let { " from $it" } ?: ""}. Anything you add plays first, then it carries on."
                        else "Nothing queued. Use ⋮ › Play next or Add to queue on any song.",
                            color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    }
                }
            itemsIndexed(queue, key = { _, it -> it.id }) { qi, e ->
                // Spotify-style sections: your own picks first, then where the rest comes from.
                if (qi == 0 || queue[qi - 1].manual != e.manual) QueueHeader(
                    when {
                        e.manual -> "Next in queue"
                        queueFrom != null -> "Next from: $queueFrom"
                        contextMode && spotifyCtx != null -> "Next from: $spotifyCtx"
                        else -> "Next up"
                    },
                )
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
            if (contextMode && queue.isNotEmpty()) item { QueueHeader("Then Spotify carries on${spotifyCtx?.let { " with $it" } ?: ""}") }
                if (history.isNotEmpty()) {
                    item { SectionLabel("Recently played", Modifier.padding(horizontal = 24.dp)) }
                    items(history.asReversed(), key = { "h-${it.uri}" }) { t ->
                        TrackRow(title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                            onClick = { vm.playNow(t) })
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
            }
        }

        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 8 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 8 },
        ) {
            LyricsView(lyrics, position, art, wash,
                onSeek = { ms -> position = ms; seekAt = android.os.SystemClock.elapsedRealtime(); vm.seekTo(ms) },
                onClose = { showLyrics = false })
        }
    }

    sheetEntry?.let { e ->
        TrackSheet(
            uri = e.track.uri, title = e.track.title, subtitle = e.track.artist, imageUrl = e.track.imageUrl,
            actions = listOf(
                SheetAction("Play now", Icons.Rounded.PlayArrow) { vm.playAt(vm.indexOf(e.id)) },
                SheetAction("Move to top", Icons.Rounded.VerticalAlignTop) { vm.move(vm.indexOf(e.id), 0) },
                SheetAction("Remove from queue", Icons.Rounded.RemoveCircleOutline) { vm.remove(vm.indexOf(e.id)) },
            ),
            onDismiss = { sheetEntry = null },
        )
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
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp))
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
        if (e.track.durationMs > 0) {
            Text(formatDuration(e.track.durationMs), color = Fidelis.inkFaint, style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun QueueHeader(text: String) {
    Text(text, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp))
}
