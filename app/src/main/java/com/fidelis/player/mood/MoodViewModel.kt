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
import com.fidelis.player.mood.Source
import com.fidelis.player.mood.Suggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a build-your-own mix draws from: a library source or one of your playlists. */
sealed interface Pick {
    val label: String
    val key: String
    data class FromSource(val source: Source) : Pick {
        override val label get() = source.label
        override val key get() = "s:${source.name}"
    }
    data class FromPlaylist(val playlist: SpotifyPlaylist) : Pick {
        override val label get() = playlist.name
        override val key get() = "p:${playlist.id}"
    }
}

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val moodRepo: MoodRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val connected = repo.connected
    val nowPlaying = remote.state
    val artwork = remote.artwork
    val progress = moodRepo.progress
    val suggestions: List<Suggestion> = Suggestion.presets

    var picks by mutableStateOf(sourcePicks(emptyList())); private set
    var selected by mutableStateOf<Pick>(Pick.FromSource(Source.EVERYTHING)); private set
    var selectedMood by mutableStateOf<Mood?>(null); private set
    var building by mutableStateOf(false); private set
    var mixTitle by mutableStateOf<String?>(null); private set
    var result by mutableStateOf<List<MoodTrack>>(emptyList()); private set
    var note by mutableStateOf<String?>(null); private set

    private var lastRun: (suspend () -> List<MoodTrack>)? = null
    private var lastEmptyNote = ""

    private fun sourcePicks(playlists: List<SpotifyPlaylist>): List<Pick> =
        Source.entries.map { Pick.FromSource(it) } + playlists.map { Pick.FromPlaylist(it) }

    fun load() {
        viewModelScope.launch { picks = sourcePicks(repo.fetchPlaylists()) }
    }

    fun select(p: Pick) { selected = p }

    fun runSuggestion(s: Suggestion) {
        selectedMood = null
        run(s.title, "Not enough listening history for this one yet.") {
            when (s) {
                Suggestion.OnRepeat -> moodRepo.topMix("short_term")
                Suggestion.HalfYear -> moodRepo.topMix("medium_term")
                Suggestion.Rediscover -> moodRepo.rediscover()
                is Suggestion.MoodMix -> moodRepo.build(Source.EVERYTHING, s.mood)
            }
        }
    }

    fun generate(mood: Mood) {
        val pick = selected
        selectedMood = mood
        val empty = if (pick is Pick.FromPlaylist)
            "No ${mood.label.lowercase()} songs found. Spotify only shares songs from playlists you created, so try one of yours or Everything."
        else "No ${mood.label.lowercase()} songs found in ${pick.label}. Try Everything."
        run("${mood.label} · ${pick.label}", empty) {
            when (pick) {
                is Pick.FromSource -> moodRepo.build(pick.source, mood)
                is Pick.FromPlaylist -> moodRepo.buildFromPlaylist(pick.playlist.id, mood)
            }
        }
    }

    /** Same mix, freshly ordered. Tags are cached, so this is instant. */
    fun regenerate() {
        val again = lastRun ?: return
        run(mixTitle ?: "Your mix", lastEmptyNote, again)
    }

    private fun run(title: String, emptyNote: String, block: suspend () -> List<MoodTrack>) {
        if (building) return
        lastRun = block; lastEmptyNote = emptyNote
        viewModelScope.launch {
            building = true; mixTitle = title; note = null
            result = runCatching { block() }.getOrDefault(emptyList())
            note = if (result.isEmpty()) emptyNote else null
            building = false
        }
    }

    fun play() { if (result.isNotEmpty()) moodRepo.play(result) }
    fun playFrom(index: Int) {
        if (result.isNotEmpty()) moodRepo.play(result.drop(index) + result.take(index))
    }
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }
    fun authUrl(): String = repo.authUrl()
}
