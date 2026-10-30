package com.fidelis.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.lyrics.LrcLib
import com.fidelis.player.integration.lyrics.Lyrics
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val remote: SpotifyRemote,
    private val lrclib: LrcLib,
) : ViewModel() {
    val state = remote.state
    val queue = remote.queue
    val history = remote.history
    val artwork = remote.artwork
    val liked = remote.liked
    val source = remote.source
    val spotifyContext = remote.spotifyContext
    val contextMode = remote.contextMode

    private val _lyrics = MutableStateFlow<Lyrics>(Lyrics.Loading)
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    init {
        // Fetch lyrics whenever the song changes.
        viewModelScope.launch {
            remote.state.filterNotNull().filter { it.title.isNotBlank() }
                .distinctUntilChangedBy { it.uri ?: "${it.artist}|${it.title}" }
                .collectLatest { s ->
                    _lyrics.value = Lyrics.Loading
                    _lyrics.value = lrclib.find(s.artist, s.title, s.durationMs)
                }
        }
    }

    fun togglePlay() { if (state.value?.isPaused == true) remote.resume() else remote.pause() }
    fun next() = remote.skipNext()
    fun prev() = remote.skipPrevious()
    fun seekTo(ms: Long) = remote.seekTo(ms)
    fun toggleLike() = remote.toggleLike()
    fun playAt(index: Int) = remote.playAt(index)
    fun move(from: Int, to: Int) = remote.move(from, to)
    fun remove(index: Int) = remote.remove(index)
    fun reshuffle() = remote.reshuffle()
    fun clear() = remote.clearQueue()
    fun playNow(t: QueueTrack) = remote.playNow(t)

    /** Live position of a queue slot (it can move while you drag). */
    fun indexOf(id: Long): Int = remote.queue.value.indexOfFirst { it.id == id }
}
