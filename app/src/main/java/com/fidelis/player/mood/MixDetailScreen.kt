package com.fidelis.player.ui.mood

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fidelis.player.mood.MoodTrack
import com.fidelis.player.ui.theme.*

@Composable
fun MixDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: MixDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val mix by vm.mix.collectAsState()
    val snap by vm.nowPlaying.collectAsState()
    var menuFor by remember { mutableStateOf<Pair<Int, MoodTrack>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // Deleted (or never existed): leave the page once.
    LaunchedEffect(mix) { if (mix == null && !left) { left = true; onBack() } }
    val m = mix ?: return
    val tracks = m.tracks

    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                val meta = buildString {
                    append("${tracks.size} songs")
                    if (m.newCount > 0) append(" · ${m.newCount} new")
                    if (m.subtitle.isNotBlank()) append("\n${m.subtitle}")
                }
                DetailHeader(
                    eyebrow = "Mix", title = m.title, meta = meta,
                    backdropUrl = m.covers.firstOrNull(), onBack = onBack,
                    cover = { MixCollage(m.covers, Modifier.fillMaxSize()) },
                    trailing = { IconCircle(Icons.Rounded.DeleteOutline, "Delete mix", onClick = { confirmDelete = true }) },
                ) {
                    GradientButton("Play", { vm.playFrom(0); onOpenPlayer() }, Modifier.weight(1f),
                        icon = Icons.Rounded.PlayArrow, enabled = tracks.isNotEmpty())
                    SoftButton("Shuffle", { vm.shuffle(); onOpenPlayer() }, Modifier.weight(1f),
                        icon = Icons.Rounded.Shuffle, enabled = tracks.isNotEmpty())
                    if (tracks.isNotEmpty()) {
                        IconCircle(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to queue", size = 52.dp,
                            onClick = { vm.addAllToQueue(); toast("Added ${tracks.size} songs to queue") })
                    }
                }
            }
            if (tracks.isEmpty()) {
                item {
                    EmptyState("This mix is empty", "Every song was removed. Build a new one from the Mood tab.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
            itemsIndexed(tracks, key = { i, t -> "$i-${t.uri}" }) { i, t ->
                SongRow(
                    title = t.title, subtitle = t.artist, imageUrl = t.imageUrl,
                    highlighted = t.uri == snap?.uri,
                    badge = if (t.isNew) "NEW" else null,
                    onClick = { vm.playFrom(i); onOpenPlayer() },
                    onMore = { menuFor = i to t },
                )
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
                SheetAction("Remove from mix", Icons.Rounded.RemoveCircleOutline) { vm.removeFromMix(i) },
            ),
            onDismiss = { menuFor = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${m.title}”?") },
            text = { Text("The mix is removed from Fidelis. Your Spotify library isn't touched.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = Fidelis.bg1, titleContentColor = Fidelis.ink, textContentColor = Fidelis.inkSoft,
        )
    }
}
