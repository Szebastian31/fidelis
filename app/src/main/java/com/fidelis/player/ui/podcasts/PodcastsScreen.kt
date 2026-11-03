package com.fidelis.player.ui.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

/** Your podcasts, like Spotify's: newest episodes first, then the shows you follow. */
@Composable
fun PodcastsScreen(onOpenShow: (String) -> Unit, onOpenPlayer: () -> Unit, toggle: @Composable () -> Unit = {}, vm: PodcastsViewModel = hiltViewModel()) {
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    FidelisBackground {
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = chromeBottom()),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeader("Listen", eyebrow = "Podcasts") { toggle() } }
            when {
                !connected -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("Connect Spotify", "Sign in on the Listen tab and your podcasts appear here.")
                }
                vm.loading && vm.shows.isEmpty() -> items(6) { SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f)) }
                vm.shows.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("No shows yet", "Follow a podcast in Spotify and it shows up here.",
                        actionLabel = "Refresh", onAction = vm::refresh)
                }
                else -> {
                    if (vm.latest.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("New episodes") }
                        items(vm.latest.take(8), key = { "e-" + it.uri }, span = { GridItemSpan(maxLineSpan) }) { e ->
                            EpisodeRow(e, e.uri == snap?.uri) { vm.play(e); onOpenPlayer() }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Your shows") }
                    items(vm.shows, key = { it.id }) { s -> ShowTile(s) { onOpenShow(s.id) } }
                }
            }
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
private fun ShowTile(s: PodcastShow, onClick: () -> Unit) {
    Column(Modifier.pressable(onClick = onClick)) {
        AsyncImage(s.imageUrl, s.name, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
            .background(Fidelis.glass), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(6.dp))
        Text(s.name, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow. Ellipsis)
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
