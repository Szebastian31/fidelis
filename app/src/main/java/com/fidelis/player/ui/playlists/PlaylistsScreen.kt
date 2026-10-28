package com.fidelis.player.ui.playlists

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3. MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui. Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.ui.theme.*

private val EDGE = 20.dp

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    vm: PlaylistsViewModel = hiltViewModel(),
) {
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val context = LocalContext.current
    val playing = snap?.takeIf { it.title.isNotBlank() }
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> androidx.compose.foundation.lazy.grid.GridItemSpan =
        { GridItemSpan(it.maxLineSpan) }

    LaunchedEffect(connected) { if (connected) vm.refresh() }

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = EDGE, end = EDGE, bottom = chromeBottom()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = full) {
                    ScreenHeader(title = "Listen", eyebrow = "Your library") {
                        if (connected) IconCircle(Icons.Rounded.Refresh, "Refresh", onClick = { vm.refresh() })
                    }
                }
                if (!connected) {
                    item(span = full) {
                        EmptyState(
                            title = "Connect Spotify",
                            body = "Sign in once to bring your playlists into Fidelis.",
                            actionLabel = "Connect Spotify",
                            onAction = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl()))) },
                        )
                    }
                } else {
                    if (vm.quickPicks.isNotEmpty()) {
                        item(span = full, key = "qp") {
                            Column {
                                RowTitle("Quick picks", "Your most played, last 4 weeks")
                                QuickPicks(vm.quickPicks, snap?.uri, vm::playQuickPick)
                            }
                        }
                    }
                    if (vm.listenAgain.isNotEmpty()) {
                        item(span = full, key = "la") {
                            Column {
                                RowTitle("Listen again", "Recently played")
                                LazyRow(Modifier.bleed(EDGE), contentPadding = PaddingValues(horizontal = EDGE),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    itemsIndexed(vm.listenAgain, key = { _, t -> "la-${t.uri}" }) { i, t ->
                                        AlbumTile(t.title, t.artist, t.imageUrl) { vm.playListenAgain(i) }
                                    }
                                }
                            }
                        }
                    }
                    if (vm.heavyRotation.isNotEmpty()) {
                        item(span = full, key = "hr") {
                            Column {
                                RowTitle("Heavy rotation", "Your top artists, last 6 months")
                                LazyRow(Modifier.bleed(EDGE), contentPadding = PaddingValues(horizontal = EDGE),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(vm.heavyRotation, key = { "hr-${it.id}" }) { a ->
                                        ArtistBubble(a.name, a.imageUrl) { onOpenArtist(a.id) }
                                    }
                                }
                            }
                        }
                    }
                    item(span = full, key = "yp") { RowTitle("Your playlists", null) }
                    item(key = LIKED_PLAYLIST_ID) { LikedTile(vm.likedCount) { onOpenPlaylist(LIKED_PLAYLIST_ID) } }
                    when {
                        vm.loading && vm.playlists.isEmpty() -> items(8) {
                            Column {
                                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), radius = 16.dp)
                                Spacer(Modifier.height(8.dp))
                                SkeletonBox(Modifier.fillMaxWidth(0.7f).height(12.dp), radius = 6.dp)
                            }
                        }
                        vm.playlists.isEmpty() && !vm.loading -> item(span = full) {
                            EmptyState(
                                title = "No playlists found",
                                body = "Couldn't reach Spotify, or you don't have any playlists yet.",
                                actionLabel = "Try again", onAction = { vm.refresh() },
                            )
                        }
                        else -> items(vm.playlists, key = { it.id }) { p -> PlaylistTile(p) { onOpenPlaylist(p.id) } }
                    }
                }
            }

            playing?.let {
                MiniPlayer(
                    snap = it, art = art, onOpen = onOpenPlayer, onToggle = vm::togglePlay,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Lets a full-width row scroll edge to edge, past the grid's side padding. */
private fun Modifier.bleed(edge: Dp) = layout { measurable, c ->
    val px = edge.roundToPx()
    val w = c.maxWidth + px * 2
    val p = measurable.measure(c.copy(minWidth = w, maxWidth = w))
    layout(c.maxWidth, p.height) { p.place(-px, 0) }
}

@Composable
private fun RowTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 6.dp, bottom = 12.dp)) {
        Text(title, color = Fidelis.ink, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Obsidian-style: four rows of songs that scroll sideways together. */
@Composable
private fun QuickPicks(tracks: List<PlaylistTrack>, playingUri: String?, onPlay: (Int) -> Unit) {
    val rows = minOf(4, tracks.size)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier.bleed(EDGE).height(60.dp * rows + 6.dp * (rows - 1)),
        contentPadding = PaddingValues(horizontal = EDGE),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(tracks, key = { _, t -> "qp-${t.uri}" }) { i, t ->
            val on = t.uri == playingUri
            Row(Modifier.width(290.dp).fillMaxHeight().pressable { onPlay(i) },
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Fidelis.glass)) {
                    t.imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = if (on) Fidelis.violet else Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LikedTile(count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.pressable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).shadow(10.dp, shape).clip(shape).background(Fidelis.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = Fidelis.ink, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Liked Songs", color = Fidelis.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (count > 0) Text("$count songs", color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlaylistTile(p: SpotifyPlaylist, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.pressable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).shadow(10.dp, shape).clip(shape).background(Fidelis.glass),
            contentAlignment = Alignment.Center,
        ) {
            if (p.imageUrl != null) {
                AsyncImage(p.imageUrl, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (p.trackCount > 0) Text("${p.trackCount} songs", color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
    }
}
