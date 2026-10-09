package com.fidelis.player.ui.player

import androidx.lifecycle.ViewModel
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val remote: SpotifyRemote,
) : ViewModel() {
    val state = remote.state
    val queue = remote.queue
    val history = remote.history
    val artwork = remote.artwork

    fun togglePlay() { if (state.value?.isPaused == true) remote.resume() else remote.pause() }
    fun next() = remote.skipNext()
    fun prev() = remote.skipPrevious()
    fun seekTo(ms: Long) = remote.seekTo(ms)

    fun playAt(index: Int) = remote.playAt(index)
    fun move(from: Int, to: Int) = remote.move(from, to)
    fun remove(index: Int) = remote.remove(index)
    fun reshuffle() = remote.reshuffle()
    fun clear() = remote.clearQueue()
    fun playNow(t: QueueTrack) = remote.playNow(t)

    /** Live position of a queue slot (it can move while you drag). */
    fun indexOf(id: Long): Int = remote.queue.value.indexOfFirst { it.id == id }
}
