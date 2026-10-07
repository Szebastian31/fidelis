package com.fidelis.player.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.data.MusicRepository
import com.fidelis.player.data.model.Track
import com.fidelis.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeRows(
    val quickPicks: List<Track> = emptyList(),
    val listenAgain: List<Track> = emptyList(),
    val losslessGems: List<Track> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: MusicRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    private val all: StateFlow<List<Track>> =
        repo.tracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var query by mutableStateOf("")

    val rows: StateFlow<HomeRows> = all.map { list ->
        HomeRows(
            quickPicks = list.shuffled().take(12),
            listenAgain = list.asReversed().take(12),
            losslessGems = list.filter { it.fidelity.isLossless }.take(12),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeRows())

    val results: StateFlow<List<Track>> =
        snapshotFlow { query }.combine(all) { q, list ->
            if (q.isBlank()) emptyList()
            else list.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(list: List<Track>, index: Int) = playback.play(list, index)
}
