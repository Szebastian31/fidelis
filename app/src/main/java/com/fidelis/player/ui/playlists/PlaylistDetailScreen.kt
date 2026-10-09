package com.fidelis.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fidelis.player.ui.theme.EmptyState
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.GradientButton
import com.fidelis.player.ui.theme.IconCircle
import com.fidelis.player.ui.theme.SkeletonBox
import com.fidelis.player.ui.theme.SoftButton
import com.fidelis.player.ui.theme.TrackRow

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: PlaylistDetailViewModel = hiltViewModel(),
) {
    val snap by vm.nowPlaying.collectAsState()
    val p = vm.playlist
    val tracks = vm.tracks

    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    // Blurred cover as a soft backdrop behind the header.
                    p?.imageUrl?.let {
                        AsyncImage(
                            it, null,
                            Modifier.matchParentSize().blur(60.dp).alpha(0.45f),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, Fidelis.bg0.copy(alpha = 0.95f)))
                            )
                        )
                    }

                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(16.dp))
                        IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.height(12.dp))

                        val shape = RoundedCornerShape(24.dp)
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).size(210.dp)
                                .shadow(28.dp, shape).clip(shape).background(Fidelis.glass),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (p?.imageUrl != null) {
                                AsyncImage(p.imageUrl, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Rounded.MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(48.dp))
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            p?.name ?: "Playlist", color = Fidelis.ink,
                            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val count = if (tracks.isNotEmpty()) tracks.size else p?.trackCount ?: 0
                        if (count > 0) {
                            Text(
                                "$count songs", color = Fidelis.inkMute,
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientButton(
                                text = "Play", icon = Icons.Rounded.PlayArrow,
                                enabled = !vm.loading,
                                onClick = {
                                    if (tracks.isNotEmpty()) vm.playFrom(0) else vm.playInSpotify()
                                    onOpenPlayer()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            SoftButton(
                                text = "Shuffle", icon = Icons.Rounded.Shuffle,
                                enabled = tracks.isNotEmpty(),
                                onClick = { vm.shuffle(); onOpenPlayer() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            when {
                vm.loading -> items(8) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.size(44.dp), radius = 10.dp)
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
                        body = "Spotify only lists the songs of playlists you created or collaborate on. " +
                            "You can still play this one in full.",
                        actionLabel = "Play in Spotify",
                        onAction = { vm.playInSpotify(); onOpenPlayer() },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                else -> itemsIndexed(tracks, key = { i, t -> "$i-${t.uri}" }) { i, t ->
                    TrackRow(
                        title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                        highlighted = t.uri == snap?.uri,
                        onClick = { vm.playFrom(i); onOpenPlayer() },
                    )
                }
            }
        }
    }
}
