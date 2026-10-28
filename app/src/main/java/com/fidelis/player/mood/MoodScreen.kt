package com.fidelis.player.ui.mood

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape. CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.mood.Mix
import com.fidelis.player.mood.Mood
import com.fidelis.player.ui.theme.*

@Composable
fun MoodScreen(onOpenPlayer: () -> Unit, onOpenMix: (String) -> Unit, vm: MoodViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by vm.connected.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    val art by vm.artwork.collectAsState()
    val progress by vm.progress.collectAsState()
    val mixes by vm.mixes.collectAsState()
    val playing = snap?.takeIf { it.title.isNotBlank() }

    LaunchedEffect(connected) { if (connected) vm.load() }
    LaunchedEffect(vm.openMix) { vm.openMix?.let { onOpenMix(it); vm.consumeOpen() } }

    FidelisBackground {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = chromeBottom()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                full { Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader("Mood", "Mixes for right now") } }

                if (!connected) {
                    full {
                        EmptyState(
                            title = "Connect Spotify",
                            body = "Mixes are built from your own library and listening history.",
                            actionLabel = "Connect Spotify",
                            onAction = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl()))) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    return@LazyVerticalGrid
                }

                vm.building?.let { title ->
                    full {
                        GlassCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Making “$title”", color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(10.dp))
                                val p = progress
                                val bar = Modifier.fillMaxWidth().clip(CircleShape)
                                if (p != null && p.total > 0) {
                                    LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = bar,
                                        color = Fidelis.violet, trackColor = Fidelis.glass)
                                    Text("${p.phase} · ${p.done}/${p.total}", color = Fidelis.inkMute,
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                } else {
                                    LinearProgressIndicator(bar, color = Fidelis.violet, trackColor = Fidelis.glass)
                                    Text("Gathering your songs…", color = Fidelis.inkMute,
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }

                vm.note?.let {
                    full { EmptyState("No mix yet", it, Modifier.padding(horizontal = 20.dp)) }
                }

                // ---- Your mixes ----
                if (mixes.isNotEmpty()) {
                    full { SectionLabel("Your mixes", Modifier.padding(horizontal = 20.dp)) }
                    items(mixes, key = { it.id }) { m ->
                        val col = mixes.indexOf(m) % 3
                        MixTile(
                            m,
                            modifier = Modifier.padding(start = if (col == 0) 20.dp else 0.dp, end = if (col == 2) 20.dp else 0.dp),
                            onOpen = { onOpenMix(m.id) },
                            onPlay = { vm.playMix(m); onOpenPlayer() },
                        )
                    }
                }

                // ---- Discover ----
                full { SectionLabel("Discover · your songs + new ones", Modifier.padding(horizontal = 20.dp)) }
                full {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(vm.discover) { i, d ->
                            val saved = mixes.firstOrNull { it.title == d.title }
                            PresetCard(d.title, d.subtitle, i + 2, Icons.Rounded.TravelExplore,
                                saved = saved != null, busy = vm.building == d.title,
                                onClick = { if (saved != null) onOpenMix(saved.id) else vm.runDiscover(d) },
                                onRefresh = { vm.runDiscover(d) })
                        }
                    }
                }

                // ---- Made for you ----
                full { SectionLabel("Made for you", Modifier.padding(horizontal = 20.dp)) }
                full {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(vm.suggestions) { i, s ->
                            val saved = mixes.firstOrNull { it.title == s.title }
                            PresetCard(s.title, s.subtitle, i, Icons.Rounded.AutoAwesome,
                                saved = saved != null, busy = vm.building == s.title,
                                onClick = { if (saved != null) onOpenMix(saved.id) else vm.runSuggestion(s) },
                                onRefresh = { vm.runSuggestion(s) })
                        }
                    }
                }

                // ---- Build your own ----
                full { SectionLabel("Build your own", Modifier.padding(horizontal = 20.dp)) }
                full {
                    Column {
                        Hint("Draw from")
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.picks, key = { it.key }) { p ->
                                FidelisChip(p.label, vm.selected == p, { vm.select(p) }, Modifier.widthIn(max = 180.dp))
                            }
                        }
                        Hint("Mood (optional)")
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Mood.entries) { m -> FidelisChip(m.label, vm.selectedMood == m, { vm.pickMood(m) }) }
                        }
                        Hint("Extras")
                        Row(Modifier.padding(horizontal = 20.dp)) {
                            FidelisChip("✦ Add new songs", vm.includeNew, vm::toggleNew)
                        }
                        Spacer(Modifier.height(18.dp))
                        GradientButton(
                            text = if (vm.building != null) "Working…" else "Create mix",
                            onClick = vm::createCustom,
                            enabled = vm.canCreate && vm.building == null,
                            icon = Icons.Rounded.AutoAwesome,
                            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                        )
                        if (!vm.canCreate) {
                            Text("Pick a mood, or turn on new songs.", color = Fidelis.inkFaint,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                        }
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

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Hint(text: String) {
    Text(text, color = Fidelis.inkFaint, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp))
}

@Composable
private fun MixTile(m: Mix, modifier: Modifier, onOpen: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.pressable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(10.dp, shape).clip(shape)) {
            MixCollage(m.covers, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                    .background(Fidelis.accent).pressable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Fidelis.ink, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(m.title, color = Fidelis.ink, style = MaterialTheme.typography.labelLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (m.newCount > 0) "${m.tracks.size} songs · ${m.newCount} new" else "${m.tracks.size} songs",
            color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall,
        )
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
private fun PresetCard(
    title: String, subtitle: String, index: Int, icon: ImageVector,
    saved: Boolean, busy: Boolean, onClick: () -> Unit, onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.width(160.dp).height(170.dp).clip(shape)
            .background(Brush.linearGradient(cardGradients[index % cardGradients.size]))
            .alpha(if (busy) 0.6f else 1f)
            .pressable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(if (busy) "Making it…" else subtitle, color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (saved && !busy) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)).pressable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Refresh, "Make again", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}
