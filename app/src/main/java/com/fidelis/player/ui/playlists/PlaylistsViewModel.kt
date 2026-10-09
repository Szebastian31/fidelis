package com.fidelis.player.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Route id for the Liked Songs page (shares the playlist detail screen). */
const val LIKED_PLAYLIST_ID = "liked"

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val connected = repo.connected
    val nowPlaying = remote.state
    val artwork = remote.artwork

    var loading by mutableStateOf(false); private set
    var playlists by mutableStateOf(repo.cachedPlaylists()); private set
    var likedCount by mutableIntStateOf(0); private set

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            val liked = async { repo.fetchLikedCount() }
            playlists = repo.fetchPlaylists()
            likedCount = liked.await()
            loading = false
        }
    }

    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }
    fun authUrl(): String = repo.authUrl()
}
