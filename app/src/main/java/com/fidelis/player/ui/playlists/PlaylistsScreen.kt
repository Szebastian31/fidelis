package com.fidelis.player.ui.playlists

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.ui.theme.EmptyState
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.IconCircle
import com.fidelis.player.ui.theme.MiniPlayer
import com.fidelis.player.ui.theme.ScreenHeader
import com.fidelis.player.ui.theme.SkeletonBox
import com.fidelis.player.ui.theme.pressable

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: PlaylistsViewModel = hiltViewModel(),
) {
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val context = LocalContext.current
    val playing = snap?.takeIf { it.title.isNotBlank() }

    // Reload whenever the screen appears or the Spotify connection changes.
    LaunchedEffect(connected) { if (connected) vm.refresh() }

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    bottom = if (playing != null) 100.dp else 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ScreenHeader(title = "Playlists", eyebrow = "Your library") {
                        if (connected) IconCircle(Icons.Rounded.Refresh, "Refresh", onClick = { vm.refresh() })
                    }
                }

                when {
                    !connected -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = "Connect Spotify",
                            body = "Sign in once to bring your playlists into Fidelis.",
                            actionLabel = "Connect Spotify",
                            onAction = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl())))
                            },
                        )
                    }

                    vm.loading && vm.playlists.isEmpty() -> items(9) {
                        Column {
                            SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), radius = 16.dp)
                            Spacer(Modifier.height(8.dp))
                            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(12.dp), radius = 6.dp)
                        }
                    }

                    vm.playlists.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = "No playlists found",
                            body = "Couldn't reach Spotify, or your library is empty.",
                            actionLabel = "Try again",
                            onAction = { vm.refresh() },
                        )
                    }

                    else -> items(vm.playlists, key = { it.id }) { p ->
                        PlaylistTile(p) { onOpenPlaylist(p.id) }
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
                Icon(Icons.Rounded. MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            p.name, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (p.trackCount > 0) {
            Text("${p.trackCount} songs", color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
        }
    }
}
