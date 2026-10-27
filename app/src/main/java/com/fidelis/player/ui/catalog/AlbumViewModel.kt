package com.fidelis.player.ui.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.AlbumPage
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyCatalog
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: SpotifyCatalog,
    private val remote: SpotifyRemote,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["albumId"])

    val nowPlaying = remote.state
    val artwork = remote.artwork

    var page by mutableStateOf<AlbumPage?>(null); private set
    var loading by mutableStateOf(true); private set
    var failed by mutableStateOf(false); private set

    private val tracks get() = page?.tracks.orEmpty()

    init { load() }

    fun load() {
        viewModelScope.launch {
            loading = true; failed = false
            page = catalog.album(id)
            failed = page == null
            loading = false
        }
    }

    /** Plays the album in its own order, from [index]. */
    fun play(index: Int = 0) { if (tracks.isNotEmpty()) remote.playQueue(tracks.map { it.toQueue() }, index) }
    fun shuffle() { if (tracks.isNotEmpty()) remote.playQueue(tracks.shuffled().map { it.toQueue() }, 0) }
    fun addAll() = remote.addToQueue(tracks.map { it.toQueue() })
    fun playNext(t: PlaylistTrack) = remote.playNext(listOf(t.toQueue()))
    fun addToQueue(t: PlaylistTrack) = remote.addToQueue(listOf(t.toQueue()))
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }
}
