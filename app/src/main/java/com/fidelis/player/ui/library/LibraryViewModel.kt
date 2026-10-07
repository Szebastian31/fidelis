package com.fidelis.player.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.data.MusicRepository
import com.fidelis.player.data.model.Track
import com.fidelis.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    val tracks: StateFlow<List<Track>> =
        repo.tracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var scanning by mutableStateOf(false)
        private set

    fun scan() {
        viewModelScope.launch {
            scanning = true
            repo.refresh()
            scanning = false
        }
    }

    fun play(list: List<Track>, index: Int) = playback.play(list, index)
}
