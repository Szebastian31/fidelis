package com.fidelis.player.ui.mood

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.mood.Mood
import com.fidelis.player.mood.MoodRepository
import com.fidelis.player.mood.MoodTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val moodRepo: MoodRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {

    val connected: StateFlow<Boolean> = repo.connected
    val nowPlaying: StateFlow<SpotifyRemote.Snapshot?> = remote.state

    var playlists by mutableStateOf<List<SpotifyPlaylist>>(emptyList()); private set
    var selected by mutableStateOf<SpotifyPlaylist?>(null); private set
    var building by mutableStateOf(false); private set
    var activeMood by mutableStateOf<Mood?>(null); private set
    var result by mutableStateOf<List<MoodTrack>>(emptyList()); private set
    var note by mutableStateOf<String?>(null); private set

    fun load() {
        viewModelScope.launch {
            playlists = repo.fetchPlaylists()
            if (selected == null) selected = playlists.firstOrNull()
        }
    }

    fun select(p: SpotifyPlaylist) { selected = p; result = emptyList(); activeMood = null }

    fun generate(mood: Mood) {
        val p = selected ?: return
        if (building) return
        viewModelScope.launch {
            building = true; activeMood = mood; note = null
            val built = moodRepo.build(p.id, mood)
            result = built
            note = if (built.isEmpty())
                "No ${mood.label.lowercase()} matches in this playlist — try another mood or playlist." else null
            building = false
        }
    }

    fun play() { if (result.isNotEmpty()) moodRepo.play(result) }
    fun authUrl(): String = repo.authUrl()
}
