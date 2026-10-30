package com.fidelis.player.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.OpenedItems
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.coverUrl
import com.fidelis.player.integration.spotify.freshShuffle
import com.spotify.protocol.types.ListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A Spotify mix, editorial playlist or shelf item, shown with its songs before playing. */
@HiltViewModel
class SpotifyPageViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val source: ListItem? = saved.get<String>("key")?.let(OpenedItems::get)
    val nowPlaying = remote.state
    var loading by mutableStateOf(true); private set
    var tracks by mutableStateOf<List<QueueTrack>>(emptyList()); private set

    init {
        viewModelScope.launch {
            tracks = source?.let { remote.browse(it, 100) }.orEmpty()
                .filter { it.uri.orEmpty().startsWith("spotify:track:") || it.uri.orEmpty().startsWith("spotify:episode:") }
                .map { QueueTrack(it.uri, it.title.orEmpty(), it.subtitle.orEmpty(), it.coverUrl()) }
            loading = false
        }
    }

    fun playFrom(i: Int) = remote.playQueue(tracks, i, from = source?.title)
    fun shuffle() = remote.playQueue(tracks.freshShuffle { it.artist }, 0, from = source?.title)
    fun playInSpotify() { source?.let(remote::playItem) }
    fun playNext(t: QueueTrack) = remote.playNext(listOf(t))
    fun addToQueue(t: QueueTrack) = remote.addToQueue(listOf(t))
    fun addAll() = remote.addToQueue(tracks)
}
