package com.fidelis.player.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"] ?: ""
    var loading by mutableStateOf(false); private set
    var tracks by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set

    init { load() }
    fun load() { viewModelScope.launch { loading = true; tracks = repo.fetchPlaylistTracks(playlistId); loading = false } }

    fun playFrom(index: Int) =
        remote.playQueue(tracks.map { QueueTrack(it.uri, it.title, it.artist) }, index)
}
