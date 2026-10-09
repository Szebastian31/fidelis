package com.fidelis.player.ui.mood

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape. RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.mood.Mood
import com.fidelis.player.mood.Suggestion
import com.fidelis.player.ui.theme.*

@Composable
fun MoodScreen(onOpenPlayer: () -> Unit, vm: MoodViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val playing = snap?.takeIf { it.title.isNotBlank() }

    LaunchedEffect(connected) { if (connected && vm.playlists.isEmpty()) vm.load() }

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (playing != null) 100.dp else 24.dp),
            ) {
                item {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        ScreenHeader(title = "Mood", eyebrow = "Mixes for right now")
                    }
                }

                if (!connected) {
                    item {
                        EmptyState(
                            title = "Connect Spotify",
                            body = "Mood mixes are built from your own playlists and listening history.",
                            actionLabel = "Connect Spotify",
                            onAction = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl()))) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    return@LazyColumn
                }

                item { SectionLabel("Suggested", Modifier.padding(horizontal = 20.dp)) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(vm.suggestions) { i, s ->
                            SuggestionCard(s, i, selected = vm.mixTitle == s.title) { vm.runSuggestion(s) }
                        }
                    }
                }

                item { SectionLabel("Build your own", Modifier.padding(horizontal = 20.dp)) }
                item {
                    Text("From playlist", color = Fidelis.inkFaint, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(vm.playlists, key = { it.id }) { p ->
                            FidelisChip(p.name, vm.selected?.id == p.id, { vm.select(p) },
                                Modifier.widthIn(max = 180.dp))
                        }
                    }
                    Text("Mood", color = Fidelis.inkFaint, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Mood.entries) { m -> FidelisChip(m.label, vm.selectedMood == m, { vm.generate(m) }) }
                    }
                }

                if (vm.building) {
                    item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                                color = Fidelis.violet, trackColor = Fidelis.glass,
                            )
                            Text("Reading the mood of each song… the first build takes a few seconds.",
                                color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                vm.note?.let {
                    item {
                        EmptyState(title = "No mix yet", body = it,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    }
                }

                if (vm.result.isNotEmpty() && !vm.building) {
                    item {
                        Row(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(vm.mixTitle ?: "Your mix", color = Fidelis.ink,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${vm.result.size} songs · ordered to flow", color = Fidelis.inkMute,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            GradientButton("Play", { vm.play(); onOpenPlayer() }, icon = Icons.Rounded.PlayArrow)
                        }
                    }
                    itemsIndexed(vm.result, key = { i, t -> "$i-${t.uri}" }) { i, t ->
                        TrackRow(
                            title = t.title,
                            subtitle = if (t.matched.isNotEmpty()) "${t.artist} · ${t.matched.take(2).joinToString(", ")}" else t.artist,
                            index = i + 1,
                            highlighted = t.uri == snap?.uri,
                            onClick = { vm.play(); onOpenPlayer() },
                        )
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

private val cardGradients = listOf(
    listOf(Color(0xFF8B5CC7), Color(0xFFE4657F)),
    listOf(Color(0xFF3A6FD8), Color(0xFF7CC4F5)),
    listOf(Color(0xFFFF9E5A), Color(0xFFE4657F)),
    listOf(Color(0xFF2E9C8F), Color(0xFF7CC4F5)),
    listOf(Color(0xFF5B3FC4), Color(0xFFB49CFF)),
)

@Composable
private fun SuggestionCard(s: Suggestion, index: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.width(160.dp).height(170.dp).clip(shape)
            .background(Brush.linearGradient(cardGradients[index % cardGradients.size]))
            .then(if (selected) Modifier.background(Color.White.copy(alpha = 0.12f)) else Modifier)
            .pressable(onClick = onClick).padding(16.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.weight(1f))
        Text(s.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(s.subtitle, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
