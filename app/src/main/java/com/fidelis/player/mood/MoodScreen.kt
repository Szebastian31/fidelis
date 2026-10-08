package com.fidelis.player.ui.mood

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.mood.Mood
import com.fidelis.player.mood.MoodTrack
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.FidelisBackground
import com.fidelis.player.ui.theme.GlassCard

@Composable
fun MoodScreen(vm: MoodViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by vm.connected.collectAsState()
    val now by vm.nowPlaying.collectAsState()
    LaunchedEffect(connected) { if (connected && vm.playlists.isEmpty()) vm.load() }

    FidelisBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Mood", color = Fidelis.ink, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            if (!connected) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Connect Spotify to build mood mixes.", color = Fidelis.inkSoft)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl())))
                        }) { Text("Connect Spotify") }
                    }
                }
                return@Column
            }

            Text("SOURCE PLAYLIST", color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                vm.playlists.forEach { p ->
                    FilterChip(
                        selected = vm.selected?.id == p.id,
                        onClick = { vm.select(p) },
                        label = { Text(p.name, maxLines = 1) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("MOOD", color = Fidelis.inkMute, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                Mood.entries.forEach { m ->
                    FilterChip(
                        selected = vm.activeMood == m,
                        onClick = { vm.generate(m) },
                        label = { Text(m.label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (vm.building) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Fidelis.violet)
                Text("Reading tags… first build of a playlist takes a few seconds.",
                    color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
            }
            vm.note?.let { Text(it, color = Fidelis.inkSoft, style = MaterialTheme.typography.bodyMedium) }

            if (vm.result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.play() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Play ${vm.result.size}-track ${vm.activeMood?.label ?: ""} mix")
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(vm.result) { t -> MoodRow(t) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            now?.let {
                GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(it.title.ifBlank { "—" }, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(it.artist, color = Fidelis.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (it.isPaused) "Paused" else "Playing", color = Fidelis.violet,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodRow(t: MoodTrack) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Fidelis.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = Fidelis.inkMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
                if (t.matched.isNotEmpty()) {
                    Text(t.matched.joinToString(" · "), color = Fidelis.violet,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("${t.score}", color = Fidelis.pink, style = MaterialTheme.typography.titleMedium)
        }
    }
}
