package com.fidelis.player.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
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
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.ui.theme.*

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: PlaylistDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val actions = LocalTrackActions.current
    val snap by vm.nowPlaying.collectAsState()
    val p = vm.playlist
    val tracks = vm.tracks
    var menuFor by remember { mutableStateOf<Pair<Int, PlaylistTrack>?>(null) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    val label = if (vm.isLiked) "your Liked Songs" else p?.name ?: "this playlist"

    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                val count = if (tracks.isNotEmpty()) tracks.size else p?.trackCount ?: 0
                val total = tracks.sumOf { it.durationMs }
                DetailHeader(
                    eyebrow = if (vm.isLiked) "Your library" else "Playlist",
                    title = if (vm.isLiked) "Liked Songs" else p?.name ?: "Playlist",
                    meta = if (count > 0) listOfNotNull("$count songs", total.takeIf { it > 0 }?.let(::formatTotal)).joinToString(" · ") else null,
                    backdropUrl = if (vm.isLiked) null else p?.imageUrl,
                    onBack = onBack,
                    cover = {
                        when {
                            vm.isLiked -> Box(Modifier.fillMaxSize().background(Fidelis.accent), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Favorite, null, tint = Fidelis.ink, modifier = Modifier.size(80.dp))
                            }
                            p?.imageUrl != null -> AsyncImage(p.imageUrl, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else -> Icon(Icons.Rounded.MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(48.dp))
                        }
                    },
                ) {
                    GradientButton(
                        text = "Play", icon = Icons.Rounded.PlayArrow, enabled = !vm.loading,
                        onClick = {
                            if (tracks.isNotEmpty()) vm.playFrom(0) else vm.playInSpotify()
                            onOpenPlayer()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (tracks.isNotEmpty()) {
                        IconCircle(Icons.Rounded.Shuffle, "Shuffle", size = 52.dp,
                            onClick = { vm.shuffle(); onOpenPlayer() })
                        actions?.let { a ->
                            IconCircle(Icons.Rounded.Radio, "Play similar songs", size = 52.dp,
                                onClick = { a.similarTo(tracks, label) })
                        }
                        IconCircle(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to queue", size = 52.dp,
                            onClick = { vm.addAllToQueue(); toast("Added ${tracks.size} songs to queue") })
                    }
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
                    if (vm.isLiked) {
                        EmptyState(
                            title = "No liked songs yet",
                            body = "Songs you ♥ in Spotify or in the Fidelis player show up here.",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    } else {
                        EmptyState(
                            title = "Songs not shared by Spotify",
                            body = "Spotify only lists the songs of playlists you created or collaborate on. " +
                                "You can still play this one in full.",
                            actionLabel = "Play in Spotify",
                            onAction = { vm.playInSpotify(); onOpenPlayer() },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
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
            title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
            actions = listOf(
                SheetAction("Play from here", Icons.Rounded.PlayArrow) { vm.playFrom(i); onOpenPlayer() },
                SheetAction("Play next", Icons.Rounded.SkipNext) { vm.playNext(t); toast("Plays next") },
                SheetAction("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { vm.addToQueue(t); toast("Added to queue") },
            ),
            onDismiss = { menuFor = null },
        )
    }
}
