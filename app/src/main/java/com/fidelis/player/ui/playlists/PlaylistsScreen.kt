package com.fidelis.player.ui.playlists

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui. Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.GlassCard

@Composable
fun PlaylistsScreen(vm: PlaylistsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by vm.connected.collectAsState()
    val now by vm.nowPlaying.collectAsState()
    LaunchedEffect(connected) { if (connected) vm.refresh() }

    FidelisBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Playlists", color = Fidelis.ink, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            when {
                !connected -> GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Connect Spotify to see your playlists.", color = Fidelis.inkSoft)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl())))
                        }) { Text("Connect Spotify") }
                    }
                }
                vm.loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Fidelis.violet)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) { items(vm.playlists) { p -> PlaylistCard(p) { vm.play(p) } } }
            }

            now?.let { NowPlayingBar(it) { vm.togglePlay() } }
        }
    }
}

@Composable
private fun PlaylistCard(p: SpotifyPlaylist, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = p.imageUrl, contentDescription = p.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
            Text(p.name, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
            Text("${p.trackCount} tracks", color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NowPlayingBar(s: SpotifyRemote.Snapshot, onToggle: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onToggle)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title.ifBlank { "—" }, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.artist, color = Fidelis.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(if (s.isPaused) "Paused" else "Playing", color = Fidelis.violet,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
