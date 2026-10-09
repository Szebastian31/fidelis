package com.fidelis.player.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape. RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(onBack: () -> Unit, vm: NowPlayingViewModel = hiltViewModel()) {
    val snap by vm.state.collectAsState()
    val queue by vm.queue.collectAsState()
    val art by vm.artwork.collectAsState()

    val duration = snap?.durationMs ?: 0L
    var position by remember(snap) { mutableLongStateOf(snap?.positionMs ?: 0L) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(snap) {
        if (snap?.isPaused == false) while (true) {
            delay(500); if (!scrubbing) position = (position + 500).coerceAtMost(duration)
        }
    }
    val currentIndex = queue.indexOfFirst { it.uri == snap?.uri }
    val upNext = if (currentIndex >= 0) queue.drop(currentIndex + 1) else queue

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            art?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize().blur(80.dp).alpha(0.5f),
                    contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Fidelis.bg0.copy(alpha = 0.3f), Fidelis.bg0.copy(alpha = 0.92f)))
                ))
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.Rounded.KeyboardArrowDown, "Collapse", onClick = onBack)
                            Text("NOW PLAYING", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f).padding(end = 40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }

                        Spacer(Modifier.height(28.dp))
                        val shape = RoundedCornerShape(28.dp)
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(36.dp, shape).clip(shape).background(Fidelis.glass)) {
                            art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }

                        Spacer(Modifier.height(28.dp))
                        Text(snap?.title?.ifBlank { null } ?: "Nothing playing", color = Fidelis.ink,
                            style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(snap?.artist ?: "", color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)

                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { scrubbing = true; position = (it * duration).toLong() },
                            onValueChangeFinished = { vm.seekTo(position); scrubbing = false },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White, activeTrackColor = Fidelis.violet,
                                inactiveTrackColor = Fidelis.glassStrong,
                            ),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmt(position), color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text(fmt(duration), color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement. SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.Rounded.SkipPrevious, "Previous", onClick = vm::prev, size = 56.dp)
                            IconCircle(
                                if (snap?.isPaused == false) Icons.Rounded.Pause else Icons. Rounded.PlayArrow,
                                if (snap?.isPaused == false) "Pause" else "Play",
                                onClick = vm::togglePlay, size = 80.dp, filled = true,
                            )
                            IconCircle(Icons.Rounded.SkipNext, "Next", onClick = vm::next, size = 56.dp)
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("UP NEXT", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f))
                            if (queue.isNotEmpty()) {
                                FidelisChip("Reshuffle", selected = false, onClick = vm::reshuffle)
                            }
                        }
                    }
                }

                if (upNext.isEmpty()) {
                    item {
                        Text(
                            if (queue.isEmpty()) "Start a playlist or mood mix to see what's coming up."
                            else "That's the end of this queue.",
                            color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    itemsIndexed(upNext, key = { i, q -> "$i-${q.uri}" }) { i, q ->
                        TrackRow(
                            title = q.title, subtitle = q.artist, index = i + 1,
                            onClick = { vm.playAt(currentIndex + 1 + i) },
                        )
                    }
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
