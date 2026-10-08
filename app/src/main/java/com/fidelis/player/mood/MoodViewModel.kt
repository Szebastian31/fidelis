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
import com.fidelis.player.mood.Suggestion
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
    val nowPlaying: StateFlow<SpotifyRemote. Snapshot?> = remote.state
    val suggestions: List<Suggestion> = Suggestion.presets

    var playlists by mutableStateOf<List<SpotifyPlaylist>>(emptyList()); private set
    var selected by mutableStateOf<SpotifyPlaylist?>(null); private set
    var building by mutableStateOf(false); private set
    var mixTitle by mutableStateOf<String?>(null); private set
    var result by mutableStateOf<List<MoodTrack>>(emptyList()); private set
    var note by mutableStateOf<String?>(null); private set

    fun load() {
        viewModelScope.launch {
            playlists = repo.fetchPlaylists()
            if (selected == null) selected = playlists.firstOrNull()
        }
    }

    fun select(p: SpotifyPlaylist) { selected = p }

    fun runSuggestion(s: Suggestion) {
        if (building) return
        viewModelScope.launch {
            building = true; mixTitle = s.title; note = null
            result = when (s) {
                is Suggestion.OnRepeat -> moodRepo.topMix()
                is Suggestion.MoodMix -> moodRepo.moodFromTop(s.mood)
            }
            note = if (result.isEmpty())
                "Nothing came back — check your Last.fm keys and that Spotify has enough listening history." else null
            building = false
        }
    }

    fun generate(mood: Mood) {
        val p = selected ?: return
        if (building) return
        viewModelScope.launch {
            building = true; mixTitle = "${mood.label} · ${p.name}"; note = null
            result = moodRepo.build(p.id, mood)
            note = if (result.isEmpty())
                "No ${mood.label.lowercase()} matches in this playlist — try another mood or playlist." else null
            building = false
        }
    }

    fun play() { if (result.isNotEmpty()) moodRepo.play(result) }
    fun authUrl(): String = repo.authUrl()
}
