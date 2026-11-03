package com.fidelis.player.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui. Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.ui.theme.Fidelis
import com.fidelis.player.ui.theme.pressable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The playlists a song can go into: the ones you made or collaborate on. */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val spotify: SpotifyRepository,
) : ViewModel() {
    var playlists by mutableStateOf(spotify.cachedPlaylists().filter { it.editable }); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf<String?>(null); private set

    fun load() {
        loading = true
        viewModelScope.launch {
            playlists = spotify.fetchPlaylists().filter { it.editable }
            loading = false
        }
    }

    fun add(p: SpotifyPlaylist, uri: String, done: (added: Boolean, message: String) -> Unit) {
        if (busy != null) return
        busy = p.id
        viewModelScope.launch {
            val code = spotify.addToPlaylist(p.id, uri)
            busy = null
            when (code) {
                in 200..299 -> done(true, "Added to ${p.name}")
                401, 403 -> done(false, "Reconnect Spotify in Profile so Fidelis can add to your playlists")
                0 -> done(false, "No connection. Try again in a moment")
                else -> done(false, "Couldn't add to ${p.name}")
            }
        }
    }
}

/** The list inside the song sheet: tap a playlist to add the song to it. */
@Composable
fun PlaylistPicker(uri: String, onDone: () -> Unit, vm: AddToPlaylistViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    Text("ADD TO PLAYLIST", color = Fidelis.inkMute, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    val lists = vm.playlists
    if (lists.isEmpty()) {
        Text(
            if (vm.loading) "Loading your playlists…"
            else "No playlists to add to. Spotify only lets apps add songs to playlists you made or collaborate on.",
            color = Fidelis.inkFaint, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        items(lists, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth()
                    .pressable {
                        vm.add(p, uri) { added, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (added) onDone()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fidelis.glass),
                    contentAlignment = Alignment.Center) {
                    if (p.imageUrl != null) AsyncImage(p.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = Fidelis.inkFaint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Fidelis.ink, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (vm.busy == p.id) "Adding…" else "${p.trackCount} songs",
                        color = Fidelis.inkMute, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
