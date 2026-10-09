package com.fidelis.player.ui.mood

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val m = mix
    if (m == null) {
        // Deleted, or an old link: just go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    FidelisBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    MixCollage(m.covers, Modifier.matchParentSize().blur(60.dp).alpha(0.45f))
                    Box(Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Fidelis.bg0.copy(alpha = 0.95f)))
                    ))

                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                            Spacer(Modifier.weight(1f))
                            IconCircle(Icons.Rounded.DeleteOutline, "Delete mix", onClick = {
                                vm.delete(); toast("Mix deleted")
                            })
                        }
                        Spacer(Modifier.height(12.dp))

                        val shape = RoundedCornerShape(24.dp)
                        MixCollage(m.covers,
                            Modifier.align(Alignment.CenterHorizontally).size(210.dp).shadow(28.dp, shape).clip(shape))

                        Spacer(Modifier.height(20.dp))
                        Text(m.title, color = Fidelis.ink, style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth())
                        val meta = buildString {
                            append("${m.tracks.size} songs")
                            if (m.newCount > 0) append(" · ${m.newCount} new for you")
                        }
                        Text(meta, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        if (m.subtitle.isNotBlank()) {
                            Text(m.subtitle, color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GradientButton("Play", { vm.playFrom(0); onOpenPlayer() },
                                Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = m.tracks.isNotEmpty())
                            SoftButton("Shuffle", { vm.shuffle(); onOpenPlayer() },
                                Modifier.weight(1f), icon = Icons.Rounded.Shuffle, enabled = m.tracks.isNotEmpty())
                            IconCircle(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to queue", size = 52.dp,
                                onClick = { vm.addAllToQueue(); toast("Added ${m.tracks.size} songs to queue") })
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            itemsIndexed(m.tracks, key = { i, t -> "$i-${t.uri}" }) { i, t ->
                SongRow(
                    title = t.title,
                    subtitle = if (t.matched.isNotEmpty() && !t.isNew) "${t.artist} · ${t.matched.take(2).joinToString(", ")}" else t.artist,
                    imageUrl = t.imageUrl,
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
}
