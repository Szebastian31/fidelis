package com.fidelis.player.ui.player

import androidx.lifecycle.ViewModel
import com.fidelis.player.integration.spotify.SpotifyRemote
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(private val remote: SpotifyRemote) : ViewModel() {
    val state = remote.state
    val queue = remote.queue
    val artwork = remote.artwork
    fun togglePlay() { if (state.value?.isPaused == true) remote.resume() else remote.pause() }
    fun next() = remote.skipNext()
    fun prev() = remote.skipPrevious()
    fun seekTo(ms: Long) = remote.seekTo(ms)
    fun reshuffle() = remote.reshuffle()
    fun playAt(index: Int) { val q = queue.value; if (index in q.indices) remote.playQueue(q, index) }
}
