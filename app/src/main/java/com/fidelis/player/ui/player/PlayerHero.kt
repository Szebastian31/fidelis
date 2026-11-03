package com.fidelis.player.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector. ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.spotifyImageUrl
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.IconCircle
import com.fidelis.player.ui.theme.pressable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One cover page. [image] is a cover address or Spotify's own Bitmap. */
private data class Page(val uri: String?, val title: String, val artist: String, val image: Any?)

/**
 * Full-screen first page of the player. The previous and next covers wait on either side, already
 * loaded: dragging dissolves one into the other under your finger, and a skip never waits for Spotify.
 * Tap the cover to see it whole (it grows back after 5s or another tap).
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
    onArtist: () -> Unit = {}, onRadio: () -> Unit = {}, onDevices: () -> Unit = {}, onSound: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShare: () -> Unit = {},
    next: QueueTrack? = null, prev: QueueTrack? = null,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val density = LocalDensity.current
    val width = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val swipeAt = with(density) { 72.dp.toPx() }

    // Once a song's cover is on screen it keeps the same source, so nothing reloads or flashes.
    val covers = remember { LinkedHashMap<String, Any>() }
    fun cover(uri: String?, candidate: Any?): Any? {
        if (uri.isNullOrBlank()) return candidate
        covers[uri]?.let { return it }
        if (candidate != null) {
            covers[uri] = candidate
            if (covers.size > 16) covers.remove(covers.keys.first())
        }
        return candidate
    }
    val live = Page(snap?.uri, snap?.title.orEmpty(), snap?.artist.orEmpty(),
        cover(snap?.uri, spotifyImageUrl(snap?.imageUri) ?: art))
    val nextPage = next?.let { Page(it.uri, it.title, it.artist, cover(it.uri, it.imageUrl)) }
    val prevPage = prev?.let { Page(it.uri, it.title, it.artist, cover(it.uri, it.imageUrl)) }

    // After a skip the new song shows at once; this holds it until Spotify reports it too.
    var optimistic by remember { mutableStateOf<Page?>(null) }
    var skipFrom by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snap?.uri, optimistic) {
        val o = optimistic ?: return@LaunchedEffect
        val arrived = if (o.uri == null) snap?.uri != skipFrom else snap?.uri == o.uri
        if (arrived) optimistic = null else { delay(5_000); optimistic = null }
    }
    val current = optimistic ?: live

    val offset = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf<Page?>(null) }
    var flying by remember { mutableStateOf<Page?>(null) }
    var dir by remember { mutableIntStateOf(-1) }
    var job by remember { mutableStateOf<Job?>(null) }
    val nowLive by rememberUpdatedState(live)
    val nowNext by rememberUpdatedState(nextPage)
    val nowPrev by rememberUpdatedState(prevPage)
    val nextCb by rememberUpdatedState(onNext)
    val prevCb by rememberUpdatedState(onPrev)

    val land: suspend () -> Unit = {
        flying?.let { optimistic = it }
        flying = null; leaving = null
        offset.snapTo(0f)
    }
    // d = -1 next (the cover leaves to the left), 1 previous.
    val go: (Int, Float) -> Unit = { d, velocity ->
        if (d > 0 && nowPrev == null) {
            // Nothing earlier: Spotify restarts this song. A small bounce says so.
            tick(); prevCb()
            scope.launch {
                offset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f),
                    initialVelocity = if (velocity == 0f) 2200f else velocity)
            }
        } else {
            val wasFlying = job?.isActive == true
            job?.cancel()
            tick()
            job = scope.launch {
                if (wasFlying || flying != null) land()
                val target = (if (d < 0) nowNext else nowPrev) ?: Page(null, "", "", null)
                skipFrom = nowLive.uri
                leaving = optimistic ?: nowLive
                flying = target
                dir = d
                // Spotify starts loading right away, while the covers dissolve.
                if (d < 0) nextCb() else prevCb()
                offset.animateTo(d * width, spring(dampingRatio = 1f, stiffness = 210f), initialVelocity = velocity)
                land()
            }
        }
    }
    val goNow by rememberUpdatedState(go)

    var compact by remember { mutableStateOf(false) }
    val shown = flying ?: current
    LaunchedEffect(compact) { if (compact) { delay(5_000); compact = false } }
    LaunchedEffect(shown.uri) { compact = false }
    val t by animateFloatAsState(if (compact) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "compact")
    val tint = ColorFilter.tint(wash.copy(alpha = 0.22f), BlendMode.Color)

    // Pages on screen, each keyed by song so its picture survives when it moves to the middle.
    val pages: List<Pair<Page, Float>> = run {
        val l = leaving; val f = flying
        if (l != null && f != null) listOf(l to 0f, f to -dir.toFloat())
        else listOfNotNull(prevPage?.let { it to -1f }, current to 0f, nextPage?.let { it to 1f })
    }.distinctBy { it.first.uri ?: "blank" }

    Box(modifier.background(base)) {
        // Ambient light: the settled cover, small and heavily blurred, dissolving slowly between songs.
        Crossfade(shown.image, Modifier.fillMaxSize(), animationSpec = tween(900), label = "ambient") { img ->
            if (img != null) Ambient(img, tint = ColorFilter.tint(wash.copy(alpha = 0.3f), BlendMode.Color))
        }
        // The covers from the very top, fading into the blur briefly at the top and longer at the bottom.
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.88f)
                .graphicsLayer { alpha = 1f - t; compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.11f to Color.Black, 0.52f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            for ((page, slot) in pages) key(page.uri ?: "blank") {
                CoverPage(page, slot, { offset.value }, width, tint)
            }
        }
        // Readable status bar and controls; solid at the very bottom so nothing shows through.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.35f), 0.12f to Color.Transparent, 0.48f to Color.Transparent,
            0.7f to base.copy(alpha = 0.55f), 0.86f to base.copy(alpha = 0.92f), 1f to base,
        )))

        // Tapped: the whole cover as a card.
        if (t > 0f && shown.image != null) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f).statusBarsPadding().padding(top = 64.dp),
                contentAlignment = Alignment.Center) {
                AsyncImage(shown.image, "Album artwork",
                    Modifier.fillMaxWidth(0.8f).aspectRatio(1f)
                        .graphicsLayer { alpha = t; scaleX = 0.9f + 0.1f * t; scaleY = 0.9f + 0.1f * t }
                        .shadow(28.dp, RoundedCornerShape(20.dp), ambientColor = wash, spotColor = wash)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop)
            }
        }

        // Gestures on the cover: tap for the full view, drag for next / previous.
        val tracker = remember { VelocityTracker() }
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.62f)
                .pointerInput(Unit) { detectTapGestures { compact = !compact } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            if (job?.isActive == true) return@detectHorizontalDragGestures
                            val v = tracker.calculateVelocity().x
                            val x = offset.value
                            when {
                                x < 0 && (x < -swipeAt || v < -900f) -> goNow(-1, v)
                                x > 0 && (x > swipeAt || v > 900f) && nowPrev != null -> goNow(1, v)
                                else -> scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f), initialVelocity = v) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f)) } },
                    ) { change, dx ->
                        if (job?.isActive == true) return@detectHorizontalDragGestures
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val x = offset.value
                        // Nothing to go back to: resist like a rubber band.
                        val target = if (x + dx > 0 && nowPrev == null) x + dx * 0.3f else x + dx
                        scope.launch { offset.snapTo(target) }
                    }
                },
        )

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.KeyboardArrowDown, "Collapse", onClick = onClose)
            Text("NOW PLAYING", color = Fidelis.inkSoft, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            if (snap?.uri != null) { IconCircle(Icons.Rounded.Share, "Share", onClick = onShare); Spacer(Modifier.width(8.dp)) }
            IconCircle(Icons.Rounded.Speaker, "Playing on", onClick = onDevices)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Title and artist slide across in the direction of the skip.
                AnimatedContent(
                    targetState = shown.title to shown.artist,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val d = dir
                        (fadeIn(tween(360, delayMillis = 80)) +
                            slideInHorizontally(tween(460, easing = FastOutSlowInEasing)) { -d * it / 10 })
                            .togetherWith(fadeOut(tween(160)) +
                                slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { d * it / 14 })
                            .using(SizeTransform(clip = false))
                    },
                    label = "title",
                ) { (title, artist) ->
                    Column {
                        Text(title.ifBlank { " " }, color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        if (artist.isNotBlank()) {
                            Row(Modifier.pressable(onClick = onArtist), verticalAlignment = Alignment.CenterVertically) {
                                Text(artist, color = Fidelis.inkSoft, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Icon(Icons.Rounded.ChevronRight, "Go to $artist", tint = Fidelis.inkMute,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (snap?.uri != null) {
                    Box(Modifier.pressable { tick(); onAddToPlaylist() }.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AddCircleOutline, "Add to playlist", tint = Color.White,
                            modifier = Modifier.size(26.dp))
                    }
                }
                val pop by animateFloatAsState(if (liked) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "heart")
                Box(Modifier.pressable { tick(); onToggleLike() }.size(48.dp), contentAlignment = Alignment.Center) {
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
                PlainIcon(Icons.Rounded.SkipPrevious, "Previous", 40.dp, { goNow(1, 0f) })
                val playing = snap?.isPaused == false
                Box(
                    Modifier.pressable { tick(); onToggle() }.size(76.dp)
                        .shadow(24.dp, CircleShape, ambientColor = wash, spotColor = wash)
                        .clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(38.dp))
                }
                PlainIcon(Icons.Rounded.SkipNext, "Next", 40.dp, { goNow(-1, 0f) })
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PlainIcon(Icons.Rounded.ChatBubbleOutline, "Lyrics", 24.dp, onLyrics, Fidelis.inkSoft)
                PlainIcon(Icons.Rounded.Shuffle, "Shuffle Up next", 24.dp, onShuffle, Fidelis.inkSoft)
                PlainIcon(Icons.Rounded.Radio, "Start song radio", 24.dp, onRadio, Fidelis.inkSoft)
                PlainIcon(Icons.Rounded.Tune, "Sound", 24.dp, onSound, Fidelis.inkSoft)
                PlainIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", 24.dp, onQueue, Fidelis.inkSoft)
            }
        }
    }
}

/**
 * A cover at [slot] (-1 previous, 0 current, 1 next) shifted by the drag. It drifts a third as far as
 * your finger and fades as it goes, so neighbouring covers dissolve into each other.
 */
@Composable
private fun CoverPage(page: Page, slot: Float, x: () -> Float, width: Float, tint: ColorFilter) {
    val context = LocalContext.current
    val request = remember(page.image) { ImageRequest.Builder(context).data(page.image).crossfade(320).build() }
    AsyncImage(
        request, null,
        Modifier.fillMaxSize().graphicsLayer {
            val px = x() + slot * width
            val a = (abs(px) / width).coerceIn(0f, 1f)
            translationX = px * 0.34f
            alpha = if (a >= 0.999f) 0f else (1f - a) * (1f - a * 0.25f)
            val s = 1f + 0.05f * a
            scaleX = s; scaleY = s
        },
        contentScale = ContentScale.Crop,
        colorFilter = tint,
    )
}

/** The cover as soft light: drawn tiny and blurred, so it costs little to dissolve. */
@Composable
private fun Ambient(image: Any, tint: ColorFilter) {
    val context = LocalContext.current
    val request = remember(image) { ImageRequest.Builder(context).data(image).size(96).build() }
    AsyncImage(request, null, Modifier.fillMaxSize().blur(48.dp, BlurredEdgeTreatment.Rectangle),
        contentScale = ContentScale.Crop, colorFilter = tint)
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

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
