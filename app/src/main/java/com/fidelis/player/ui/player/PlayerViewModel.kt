package com.fidelis.player.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.data.MusicRepository
import com.fidelis.player.data.model.Track
import com.fidelis.player.playback.PlaybackConnection
import com.fidelis.player.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUi(
    val state: PlayerUiState = PlayerUiState(),
    val current: Track? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackConnection,
    repo: MusicRepository,
) : ViewModel() {

    private val byId = repo.tracks().map { list -> list.associateBy { it.id } }

    val ui: StateFlow<PlayerUi> =
        combine(playback.state, byId) { s, map ->
            PlayerUi(s, s.mediaId?.toLongOrNull()?.let { map[it] })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUi())

    var position by mutableStateOf(0L)
        private set

    init {
        viewModelScope.launch {
            while (true) {
                position = playback.positionMs()
                delay(500)
            }
        }
    }

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(ms: Long) = playback.seekTo(ms)
}
