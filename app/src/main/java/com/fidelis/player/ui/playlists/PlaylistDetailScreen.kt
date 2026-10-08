package com.fidelis.player.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.GlassCard

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: PlaylistDetailViewModel = hiltViewModel(),
) {
    FidelisBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Spacer(Modifier.weight(1f))
                Text("${vm.tracks.size} songs", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.playFrom(0); onOpenPlayer() },
                enabled = vm.tracks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Play all") }

            Spacer(Modifier.height(12.dp))
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Fidelis.violet)

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(vm.tracks) { index, t ->
                    GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { vm.playFrom(index); onOpenPlayer() }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.title, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = Fidelis.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private val Alignment = androidx.compose.ui.Alignment
