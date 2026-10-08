package com.fidelis.player.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.GlassCard
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(onBack: () -> Unit, vm: NowPlayingViewModel = hiltViewModel()) {
    val snap by vm.state.collectAsState()
    val queue by vm.queue.collectAsState()
    val art by vm.artwork.collectAsState()

    val duration = snap?.durationMs ?: 0L
    var position by remember(snap) { mutableStateOf(snap?.positionMs ?: 0L) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(snap) {
        if (snap?.isPaused == false) {
            while (true) { delay(500); if (!scrubbing) position = (position + 500).coerceAtMost(duration) }
        }
    }

    FidelisBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("⌄ Collapse") }

            art?.let {
                Image(
                    bitmap = it.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
            } ?: Spacer(Modifier.fillMaxWidth().aspectRatio(1f))

            Spacer(Modifier.height(16.dp))
            Text(snap?.title ?: "—", color = Fidelis.ink, style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(snap?.artist ?: "", color = Fidelis.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { scrubbing = true; position = (it * duration).toLong() },
                onValueChangeFinished = { vm.seekTo(position); scrubbing = false },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.prev() }) { Text("⏮", style = MaterialTheme.typography.headlineSmall) }
                FilledIconButton(onClick = { vm.togglePlay() }) {
                    Text(if (snap?.isPaused == true) "▶" else "⏸")
                }
                TextButton(onClick = { vm.next() }) { Text("⏭", style = MaterialTheme.typography.headlineSmall) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Up next", color = Fidelis.inkMute, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.reshuffle() }, enabled = queue.isNotEmpty()) { Text("Reshuffle") }
            }

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(queue) { index, q ->
                    val isCurrent = q.uri == snap?.uri
                    GlassCard(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { vm.playAt(index) }) {
                        Column(Modifier.padding(10.dp)) {
                            Text(q.title, color = if (isCurrent) Fidelis.violet else Fidelis.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(q.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow. Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
