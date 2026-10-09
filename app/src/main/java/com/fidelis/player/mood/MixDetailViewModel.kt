package com.fidelis.player.ui.mood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.mood.Mix
import com.fidelis.player.mood.MixStore
import com.fidelis.player.mood.MoodTrack
import com.fidelis.player.mood.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MixDetailViewModel @Inject constructor(
    private val store: MixStore,
    private val remote: SpotifyRemote,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mixId: String = savedStateHandle["mixId"] ?: ""

    val mix: StateFlow<Mix?> = store.mixes
        .map { list -> list.firstOrNull { it.id == mixId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.get(mixId))
    val nowPlaying = remote.state

    private fun tracks() = mix.value?.tracks.orEmpty()

    fun playFrom(index: Int) { val t = tracks(); if (t.isNotEmpty()) remote.playQueue(t.map { it.toQueue() }, index) }
    fun shuffle() { val t = tracks(); if (t.isNotEmpty()) remote.playQueue(t.shuffled().map { it.toQueue() }, 0) }
    fun playNext(t: MoodTrack) = remote.playNext(listOf(t.toQueue()))
    fun addToQueue(t: MoodTrack) = remote.addToQueue(listOf(t.toQueue()))
    fun addAllToQueue() = remote.addToQueue(tracks().map { it.toQueue() })

    fun removeFromMix(index: Int) {
        val t = tracks()
        if (index in t.indices) store.update(mixId, t.filterIndexed { i, _ -> i != index })
    }

    fun delete() = store.delete(mixId)
}
