package com.fidelis.player.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.coverUrl
import com.fidelis.player.ui.theme.*

/** Spotify's own mixes and playlists: see the songs first, then Play or Shuffle. */
@Composable
fun SpotifyPageScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit, vm: SpotifyPageViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snap by vm.nowPlaying.collectAsState()
    val src = vm.source
    val tracks = vm.tracks
    val cover = remember(src) { src?.coverUrl() } ?: tracks.firstOrNull()?.imageUrl
    var menuFor by remember { mutableStateOf<Pair<Int, QueueTrack>?>(null) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromeBottom())) {
            item {
                val total = tracks.sumOf { it.durationMs }
                DetailHeader(
                    eyebrow = "From Spotify",
                    title = src?.title ?: "Playlist",
                    meta = listOfNotNull(
                        tracks.size.takeIf { it > 0 }?.let { "$it songs" },
                        total.takeIf { it > 0 }?.let(::formatTotal),
                    ).joinToString(" · ").ifBlank { src?.subtitle?.takeIf { it.isNotBlank() } },
                    backdropUrl = cover,
                    onBack = onBack,
                    cover = {
                        val bmp = vm.cover; if (bmp != null) Image(bmp.asImageBitmap(), src?.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else if (cover != null) AsyncImage(cover, src?.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(48.dp))
                    },
                ) {
                    GradientButton(
                        text = "Play", icon = Icons.Rounded.PlayArrow, enabled = !vm.loading,
                        onClick = { if (tracks.isNotEmpty()) vm.playFrom(0) else vm.playInSpotify(); onOpenPlayer() },
                        modifier = Modifier.weight(1f),
                    )
                    if (tracks.size > 1) IconCircle(Icons.Rounded.Shuffle, "Shuffle", size = 52.dp,
                        onClick = { vm.shuffle(); onOpenPlayer() })
                    if (tracks.isNotEmpty()) IconCircle(Icons.AutoMirrored. Rounded.PlaylistAdd, "Add all to queue", size = 52.dp,
                        onClick = { vm.addAll(); toast("Added ${tracks.size} songs to queue") })
                }
            }
            when {
                vm.loading -> items(8) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.size(46.dp), radius = 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            SkeletonBox(Modifier.fillMaxWidth(0.6f).height(12.dp), radius = 6.dp)
                            Spacer(Modifier.height(6.dp))
                            SkeletonBox(Modifier.fillMaxWidth(0.35f).height(10.dp), radius = 5.dp)
                        }
                    }
                }
                tracks.isEmpty() -> item {
                    EmptyState(
                        title = "Songs not shared by Spotify",
                        body = "Spotify didn't list the songs for this one. You can still play it in full.",
                        actionLabel = "Play", onAction = { vm.playInSpotify(); onOpenPlayer() },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                else -> itemsIndexed(tracks, key = { i, t -> "$i-${t.uri}" }) { i, t ->
                    SongRow(
                        title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                        highlighted = t.uri == snap?.uri, duration = t.durationMs,
                        onClick = { vm.playFrom(i); onOpenPlayer() },
                        onMore = { menuFor = i to t },
                    )
                }
            }
        }
    }
    menuFor?.let { (i, t) ->
        TrackSheet(
            uri = t.uri, title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
            actions = listOf(
                SheetAction("Play from here", Icons.Rounded.PlayArrow) { vm.playFrom(i); onOpenPlayer() },
                SheetAction("Play next", Icons.Rounded.SkipNext) { vm.playNext(t); toast("Plays next") },
                SheetAction("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { vm.addToQueue(t); toast("Added to queue") },
            ),
            onDismiss = { menuFor = null },
        )
    }
}
