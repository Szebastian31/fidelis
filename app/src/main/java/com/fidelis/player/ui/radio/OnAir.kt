package com.fidelis.player.ui.radio

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import com.fidelis.player.radio.Radio
import com.fidelis.player.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Station tags like "[Live]" and stray dashes, which only confuse the search. */
internal fun cleanOnAir(s: String): String =
    s.replace(Regex("\\[[^\\]]*\\]"), " ").replace(Regex("\\s+"), " ").trim().trim('-', '–', ' ')

/** Finds the song a station is playing on Spotify, so you can keep it. */
@HiltViewModel
class OnAirViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    var matches by mutableStateOf<List<PlaylistTrack>?>(null); private set
    var liked by mutableStateOf<Set<String>>(emptySet()); private set
    private var asked: String? = null

    fun find(song: String) {
        if (song == asked) return
        asked = song; matches = null
        viewModelScope.launch { matches = runCatching { lookup(song) }.getOrDefault(emptyList()) }
    }

    // Stations write "Artist - Title", sometimes "Title - Artist"; try both, then a loose search.
    private suspend fun lookup(song: String): List<PlaylistTrack> {
        val clean = cleanOnAir(song)
        val parts = clean.split(" - ", " – ", " — ", limit = 2).map { it.trim() }
        val best = if (parts.size == 2 && parts.all { it.isNotBlank() })
            repo.findTrack(parts[0], parts[1]) ?: repo.findTrack(parts[1], parts[0]) else null
        val more = repo.searchTracks(parts.joinToString(" "), limit = 6)
        return (listOfNotNull(best) + more).distinctBy { it.uri }
    }

    fun like(t: PlaylistTrack, onFail: () -> Unit) {
        liked = liked + t.uri
        remote.like(t.uri) { ok -> if (!ok) { liked = liked - t.uri; onFail() } }
    }

    fun play(t: PlaylistTrack) = remote.playNow(t.toQueue())
}

/** The song on air, matched on Spotify: like it, add it to a playlist, or play it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirSheet(song: String, station: String, onDismiss: () -> Unit, vm: OnAirViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(song) { vm.find(song) }
    var pick by remember(song) { mutableStateOf<PlaylistTrack?>(null) }
    var adding by remember { mutableStateOf<PlaylistTrack?>(null) }
    val list = vm.matches
    val top = pick ?: list?.firstOrNull()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fidelis.bg1, contentColor = Fidelis.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(true)
                Spacer(Modifier.width(6.dp))
                Text("ON AIR · ${station.uppercase()}", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(cleanOnAir(song), color = Fidelis.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            when {
                list == null -> SkeletonBox(Modifier.fillMaxWidth().height(104.dp), radius = 22.dp)
                top == null -> Text(
                    "Spotify doesn't seem to have this one. Some stations show a show name or an ad here instead of a song.",
                    color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium)
                else -> {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(top.imageUrl, null, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(Fidelis.glass),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("SPOTIFY MATCH", color = Fidelis.violet, style = MaterialTheme.typography.labelSmall)
                                Text(top.title, color = Fidelis.ink, maxLines = 2, overflow = TextOverflow. Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                                Text(top.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val isLiked = top.uri in vm.liked
                        GradientButton(if (isLiked) "Liked" else "Like", {
                            if (!isLiked) vm.like(top) {
                                Toast.makeText(context, "Couldn't reach Spotify. Open the player once, then try again.", Toast.LENGTH_SHORT).show()
                            }
                        }, Modifier.weight(1f), icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder)
                        SoftButton("Add", { adding = top }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
                        IconCircle(Icons.Rounded.PlayArrow, "Play on Spotify", {
                            if (Radio.playing || Radio.buffering) Radio.toggle(context)
                            vm.play(top); onDismiss()
                        }, size = 52.dp)
                    }
                    val others = list.filter { it.uri != top.uri }.take(5)
                    if (others.isNotEmpty()) {
                        SectionLabel("Other matches")
                        others.forEach { t ->
                            Row(Modifier.fillMaxWidth().pressable { pick = t }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(t.imageUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass),
                                    contentScale = ContentScale. Crop)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(t.artist, color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    adding?.let { t -> AddToPlaylistSheet(t.title, t.artist, t.imageUrl, t.uri, onDismiss = { adding = null }) }
}
