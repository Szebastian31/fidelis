package com.fidelis.player.ui.playlists

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyHome
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.ui.theme.*
import com.spotify.protocol.types.ListItem

private val EDGE = 20.dp

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    vm: PlaylistsViewModel = hiltViewModel(),
) {
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val context = LocalContext.current
    val home = vm.home
    val full: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
    val play: (ListItem) -> Unit = { vm.playItem(it); onOpenPlayer() }
    // Albums, artists, Liked Songs and your own playlists open in Fidelis; everything else plays.
    val open: (ListItem) -> Unit = { item ->
        val u = item.uri.orEmpty()
        val id = u.substringAfterLast(':')
        when {
            u.startsWith("spotify:album:") -> onOpenAlbum(id)
            u.startsWith("spotify:artist:") -> onOpenArtist(id)
            u.startsWith("spotify:playlist:") && vm.ownsPlaylist(id) -> onOpenPlaylist(id)
            u.endsWith(":collection") -> onOpenPlaylist(LIKED_PLAYLIST_ID)
            else -> play(item)
        }
    }
    LaunchedEffect(connected) { if (connected) vm.refresh() }

    FidelisBackground {
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
                return@LazyVerticalGrid
            }

            item(span = full, key = "made") {
                Column {
                    RowTitle("Made for you", "Spotify's own mixes, plus the DJ")
                    LazyRow(Modifier.bleed(EDGE), contentPadding = PaddingValues(horizontal = EDGE),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        item(key = "dj") { DjCard { vm.playDj(); onOpenPlayer() } }
                        itemsIndexed(home?.made.orEmpty(), key = { i, _ -> "made-$i" }) { _, item ->
                            SpotifyTile(item, vm.spotifyHome, 164.dp, onOpen = { play(item) }, onPlay = { play(item) })
                        }
                    }
                }
            }

            home?.recents?.takeIf { it.isNotEmpty() }?.let { recents ->
                item(span = full, key = "recents") {
                    Column {
                        RowTitle("Recents", null)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recents.take(8).chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { item -> RecentTile(item, vm.spotifyHome, Modifier.weight(1f)) { open(item) } }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            if (vm.quickPicks.isNotEmpty()) {
                item(span = full, key = "qp") {
                    Column {
                        RowTitle("Quick picks", "Your most played, last 4 weeks")
                        QuickPicks(vm.quickPicks, snap?.uri, vm::playQuickPick)
                    }
                }
            }

            home?.sections?.forEachIndexed { si, s ->
                item(span = full, key = "shelf-$si") {
                    Column {
                        RowTitle(s.title, null)
                        LazyRow(Modifier.bleed(EDGE), contentPadding = PaddingValues(horizontal = EDGE),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(s.items, key = { i, _ -> "shelf-$si-$i" }) { _, item ->
                                SpotifyTile(item, vm.spotifyHome, 148.dp, onOpen = { open(item) }, onPlay = { play(item) })
                            }
                        }
                    }
                }
            }

            if (vm.listenAgain.isNotEmpty()) {
                item(span = full, key = "la") {
                    Column {
                        RowTitle("Listen again", "Recently played songs")
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

/** Cover art shared by the Spotify app; a note icon until it arrives. */
@Composable
private fun SpotifyArt(item: ListItem, home: SpotifyHome, modifier: Modifier) {
    val bmp by produceState(home.cached(item), item.imageUri?.raw) { if (value == null) value = home.art(item) }
    Box(modifier.background(Fidelis.glass), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(remember(b) { b.asImageBitmap() }, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PlayDot(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(36.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun SpotifyTile(item: ListItem, home: SpotifyHome, size: Dp, onOpen: () -> Unit, onPlay: () -> Unit) {
    val round = item.uri.orEmpty().startsWith("spotify:artist:")
    val shape = RoundedCornerShape(if (round) size / 2 else 18.dp)
    Column(Modifier.width(size).pressable(onClick = onOpen)) {
        Box(Modifier.size(size).shadow(12.dp, shape).clip(shape)) {
            SpotifyArt(item, home, Modifier.fillMaxSize())
            PlayDot(Modifier.align(Alignment.BottomEnd).padding(8.dp), onPlay)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title.orEmpty(), color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DjCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.width(164.dp).pressable(onClick = onClick)) {
        Box(
            Modifier.size(164.dp).shadow(14.dp, shape, ambientColor = Color(0xFF1DB9C3), spotColor = Color(0xFF1DB9C3)).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF0F2A6B), Color(0xFF1DB9C3), Color(0xFF7BE0B5)))),
        ) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))))
            Text("DJ", color = Color.White, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.align(Alignment.Center))
            PlayDot(Modifier.align(Alignment.BottomEnd).padding(8.dp), onClick)
        }
        Spacer(Modifier.height(8.dp))
        Text("DJ", color = Fidelis.ink, style = MaterialTheme.typography.labelLarge)
        Text("Spotify's AI DJ picks for you", color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Spotify-style recent: small cover and name, two per row. */
@Composable
private fun RecentTile(item: ListItem, home: SpotifyHome, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(58.dp).clip(shape).background(Fidelis.glass).pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        SpotifyArt(item, home, Modifier.size(58.dp))
        Spacer(Modifier.width(10.dp))
        Text(item.title.orEmpty(), color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
    }
}

/** Two rows of songs that scroll sideways together. */
@Composable
private fun QuickPicks(tracks: List<PlaylistTrack>, playingUri: String?, onPlay: (Int) -> Unit) {
    val rows = minOf(2, tracks.size)
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
