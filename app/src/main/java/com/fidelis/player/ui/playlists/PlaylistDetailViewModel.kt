package com.fidelis.player.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import com.fidelis.player.integration.spotify.freshShuffle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
    private val smart: com.fidelis.player.integration.spotify.SmartShuffle,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val playlistId: String = savedStateHandle["playlistId"] ?: ""
    val isLiked = playlistId == LIKED_PLAYLIST_ID

    val playlist: SpotifyPlaylist? =
        if (isLiked) SpotifyPlaylist(LIKED_PLAYLIST_ID, "Liked Songs", null, 0, "")
        else repo.cachedPlaylist(playlistId)
    val nowPlaying = remote.state

    var loading by mutableStateOf(true); private set
    var tracks by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            loading = true
            tracks = if (isLiked) repo.fetchLikedTracks(1000) else repo.fetchPlaylistTracks(playlistId)
            loading = false
        }
    }

    fun playFrom(index: Int) = remote.playQueue(tracks.map { it.toQueue() }, index, from = playlist?.name)
    fun shuffle() = smart.play(tracks.map { it.toQueue() }, playlist?.name)
    val smartOn = smart.on
    fun toggleSmart() = smart.set(!smart.on.value)

    fun playNext(t: PlaylistTrack) = remote.playNext(listOf(t.toQueue()))
    fun addToQueue(t: PlaylistTrack) = remote.addToQueue(listOf(t.toQueue()))
    fun addAllToQueue() = remote.addToQueue(tracks.map { it.toQueue() })

    /** For playlists Spotify won't list (not owned by you): play the whole thing in Spotify. */
    fun playInSpotify() = remote.playContext(playlist?.uri?.ifBlank { null } ?: "spotify:playlist:$playlistId")
}
