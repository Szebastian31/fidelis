package com.fidelis.player.ui.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import com.fidelis.player.ui.radio.stationColor
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded. PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.PodcastEpisode
import com.fidelis.player.integration.spotify.PodcastShow
import com.fidelis.player.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Your podcasts, laid out like the radio: big cards for what's new, where you left off, then your shows. */
@Composable
fun PodcastsScreen(onOpenShow: (String) -> Unit, onOpenPlayer: () -> Unit, toggle: @Composable () -> Unit = {}, vm: PodcastsViewModel = hiltViewModel()) {
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val play: (PodcastEpisode) -> Unit = { vm.play(it); onOpenPlayer() }
    PodcastNotice(vm.notice, vm::clearNotice)
    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            item { Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader("Listen", eyebrow = "Podcasts") { toggle() } } }
            if (connected) item { PodcastSearchField(vm.query, vm::search) }
            when {
                connected && vm.query.isNotBlank() -> podcastResults(vm.results, vm.followed) { vm.open(it, onOpenShow) }
                !connected -> item {
                    EmptyState("Connect Spotify", "Connect Spotify in Profile and your podcasts appear here.",
                        Modifier.padding(horizontal = 20.dp))
                }
                vm.loading && vm.shows.isEmpty() -> items(4) { i ->
                    SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(if (i == 0) 196.dp else 72.dp))
                }
                vm.shows.isEmpty() -> item {
                    EmptyState("No shows yet", "Follow a podcast in Spotify and it shows up here.",
                        Modifier.padding(horizontal = 20.dp), actionLabel = "Refresh", onAction = vm::refresh)
                }
                else -> {
                    val fresh = vm.latest.filter { !it.finished }.take(8)
                    val resume = vm.latest.filter { !it.finished && it.playedMs > 0 }.take(4)
                    if (fresh.isNotEmpty()) item {
                        LazyRow(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(fresh, key = { "f" + it.uri }) { e -> EpisodeCard(e, e.uri == snap?.uri) { play(e) } }
                        }
                    }
                    if (resume.isNotEmpty()) {
                        item { Label("Continue listening") }
                        items(resume, key = { "r" + it.uri }) { e ->
                            Box(Modifier.padding(horizontal = 20.dp)) { EpisodeRow(e, e.uri == snap?.uri) { play(e) } }
                        }
                    }
                    item { Label("Your shows") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(vm.shows, key = { it.id }) { s -> ShowTile(s) { onOpenShow(s.id) } }
                        }
                    }
                    podcastPicks(vm.vibe, vm.rows, vm::search) { vm.open(it, onOpenShow) }
                    if (vm.latest.isNotEmpty()) {
                        item { Label("New episodes") }
                        items(vm.latest.take(12), key = { "e" + it.uri }) { e ->
                            Box(Modifier.padding(horizontal = 20.dp)) { EpisodeRow(e, e.uri == snap?.uri) { play(e) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = SectionLabel(text, Modifier.padding(horizontal = 20.dp))

/** A big card for a fresh episode, in its cover's own colour. */
@Composable
private fun EpisodeCard(e: PodcastEpisode, playing: Boolean, onClick: () -> Unit) {
    var tint by remember(e.uri) { mutableStateOf<Color?>(null) }
    val c = tint ?: stationColor(e.show)
    val shape = RoundedCornerShape(26.dp)
    val fresh = remember(e.released) {
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(e.released), LocalDate.now()) < 7 }.getOrDefault(false)
    }
    val badge = if (playing) "PLAYING" else if (fresh) "NEW" else null
    Box(
        Modifier.width(290.dp).height(196.dp).pressable(onClick = onClick)
            .shadow(18.dp, shape, ambientColor = c, spotColor = c).clip(shape)
            .background(Brush.linearGradient(listOf(lerp(c, Color.White, 0.12f), lerp(c, Color.Black, 0.55f))))
            .background(Fidelis.sheen).border(1.dp, Fidelis.border, shape).padding(18.dp),
    ) {
        AsyncImage(e.imageUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Fidelis.glass),
            contentScale = ContentScale.Crop,
            onSuccess = { s -> tint = (s.result.drawable as? BitmapDrawable)?.bitmap?.let(::artTint) })
        badge?.let {
            Box(Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(it, color = Fidelis.ink, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(e.show, color = Fidelis.inkSoft, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.name, color = Fidelis.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
            Text(episodeMeta(e), color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

/** One show's page: its episodes, newest first. */
@Composable
fun ShowScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit, vm: ShowViewModel = hiltViewModel()) {
    val snap by vm.nowPlaying.collectAsState()
    val show = vm.show
    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            item {
                DetailHeader(
                    eyebrow = "Podcast", title = show?.name ?: "Podcast", meta = show?.publisher,
                    backdropUrl = show?.imageUrl, onBack = onBack,
                    cover = { if (show?.imageUrl != null) AsyncImage(show.imageUrl, show.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                ) {
                    GradientButton(
                        text = "Play latest", icon = Icons.Rounded.PlayArrow, enabled = vm.episodes.isNotEmpty(),
                        onClick = { vm.play(0); onOpenPlayer() }, modifier = Modifier.weight(1f),
                    )
                }
            }
            when {
                vm.loading -> items(5) {
                    SkeletonBox(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(72.dp))
                }
                vm.episodes.isEmpty() -> item {
                    EmptyState("No episodes", "Spotify didn't list any episodes for this show.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                else -> itemsIndexed(vm.episodes, key = { _, e -> e.uri }) { i, e ->
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        EpisodeRow(e, e.uri == snap?.uri, showName = false) { vm.play(i); onOpenPlayer() }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(e: PodcastEpisode, playing: Boolean, showName: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(e.imageUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Fidelis.glass),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (showName) Text(e.show, color = Fidelis.violet, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.name, color = if (playing) Fidelis.violet else Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(e.description, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(episodeMeta(e), color = Fidelis.inkFaint, style = MaterialTheme.typography.labelSmall)
                if (!e.finished && e.playedMs > 0 && e.durationMs > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(48.dp).height(3.dp).clip(CircleShape).background(Fidelis.glassStrong)) {
                        Box(Modifier.fillMaxWidth((e.playedMs.toFloat() / e.durationMs).coerceIn(0f, 1f)).fillMaxHeight()
                            .background(Fidelis.accent))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShowTile(s: PodcastShow, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.width(124.dp).pressable(onClick = onClick)) {
        AsyncImage(s.imageUrl, s.name, Modifier.size(124.dp).shadow(10.dp, shape).clip(shape).background(Fidelis.glass),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.height(8.dp))
        Text(s.name, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(s.publisher, color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Yesterday · 42 min", "3 days ago · 12 min left", "12 Oct 2026 · Played". */
private fun episodeMeta(e: PodcastEpisode): String {
    val date = runCatching { LocalDate.parse(e.released) }.getOrNull()?.let { d ->
        val days = ChronoUnit.DAYS.between(d, LocalDate.now())
        when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    } ?: e.released
    val mins = (e.durationMs / 60_000).toInt()
    val length = when {
        e.finished -> "Played"
        e.playedMs > 0 -> "${((e.durationMs - e.playedMs) / 60_000).coerceAtLeast(1)} min left"
        mins >= 60 -> "${mins / 60} h ${mins % 60} min"
        else -> "$mins min"
    }
    return listOf(date, length).filter { it.isNotBlank() }.joinToString(" · ")
}
