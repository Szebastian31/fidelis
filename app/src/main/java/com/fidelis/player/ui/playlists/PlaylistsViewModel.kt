package com.fidelis.player.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {

    val connected: StateFlow<Boolean> = repo.connected
    val nowPlaying: StateFlow<SpotifyRemote.Snapshot?> = remote.state

    var loading by mutableStateOf(false); private set
    var playlists by mutableStateOf<List<SpotifyPlaylist>>(emptyList()); private set

    fun refresh() {
        viewModelScope.launch {
            loading = true
            playlists = repo.fetchPlaylists()
            loading = false
        }
    }

    fun play(p: SpotifyPlaylist) = remote.playContext(p.uri)
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }
    fun authUrl(): String = repo.authUrl()
}
