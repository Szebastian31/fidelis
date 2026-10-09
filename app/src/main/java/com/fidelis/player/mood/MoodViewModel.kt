package com.fidelis.player.ui.mood

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyPlaylist
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.mood.DiscoverPreset
import com.fidelis.player.mood.Mix
import com.fidelis.player.mood.MixStore
import com.fidelis.player.mood.Mood
import com.fidelis.player.mood.MoodRepository
import com.fidelis.player.mood.MoodTrack
import com.fidelis.player.mood.Source
import com.fidelis.player.mood.Suggestion
import com.fidelis.player.mood.toQueue
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
    private val store: MixStore,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val connected = repo.connected
    val nowPlaying = remote.state
    val artwork = remote.artwork
    val progress = moodRepo.progress
    val mixes = store.mixes
    val suggestions: List<Suggestion> = Suggestion.presets
    val discover: List<DiscoverPreset> = DiscoverPreset.presets

    var picks by mutableStateOf(sourcePicks(emptyList())); private set
    var selected by mutableStateOf<Pick>(Pick.FromSource(Source.EVERYTHING)); private set
    var selectedMood by mutableStateOf<Mood?>(null); private set
    var includeNew by mutableStateOf(true); private set

    /** Title of the mix being built right now, or null. */
    var building by mutableStateOf<String?>(null); private set
    var note by mutableStateOf<String?>(null); private set
    /** Set when a mix is ready; the screen opens it, then calls [consumeOpen]. */
    var openMix by mutableStateOf<String?>(null); private set

    private fun sourcePicks(playlists: List<SpotifyPlaylist>): List<Pick> =
        Source.entries.map { Pick.FromSource(it) } + playlists.map { Pick.FromPlaylist(it) }

    fun load() { viewModelScope.launch { picks = sourcePicks(repo.fetchPlaylists()) } }

    fun select(p: Pick) { selected = p }
    fun pickMood(m: Mood) { selectedMood = if (selectedMood == m) null else m }
    fun toggleNew() { includeNew = !includeNew }
    val canCreate get() = selectedMood != null || includeNew

    fun existing(title: String): Mix? = mixes.value.firstOrNull { it.title == title }

    fun runSuggestion(s: Suggestion) =
        create(s.title, s.subtitle, "Not enough listening history for this one yet.") {
            when (s) {
                Suggestion.OnRepeat -> moodRepo.topMix("short_term")
                Suggestion.HalfYear -> moodRepo.topMix("medium_term")
                Suggestion.Rediscover -> moodRepo.rediscover()
                is Suggestion.MoodMix -> moodRepo.build(Source.EVERYTHING, s.mood)
            }
        }

    fun runDiscover(d: DiscoverPreset) =
        create(d.title, d.subtitle, "Couldn't find new songs right now. Check your connection and Last.fm key.") {
            moodRepo.discover(d.mood)
        }

    fun createCustom() {
        val pick = selected
        val mood = selectedMood
        if (!canCreate) return
        val title = listOfNotNull(mood?.label, pick.label).joinToString(" · ") + if (includeNew) " + new" else ""
        val subtitle = buildString {
            append(if (mood != null) "${mood.label} picks from ${pick.label}" else "From ${pick.label}")
            if (includeNew) append(", with new songs")
        }
        val empty = if (pick is Pick.FromPlaylist)
            "Nothing fit. Spotify only shares songs from playlists you created, so try one of yours or Everything."
        else "Nothing fit in ${pick.label}. Try Everything or a different mood."
        create(title, subtitle, empty) {
            val base: List<PlaylistTrack>? = when (pick) {
                is Pick.FromPlaylist -> repo.fetchPlaylistTracks(pick.playlist.id).take(200)
                is Pick.FromSource -> sourceTracks(pick.source)
            }
            when {
                includeNew -> moodRepo.discover(mood, base)
                mood != null && pick is Pick.FromSource -> moodRepo.build(pick.source, mood)
                mood != null && pick is Pick.FromPlaylist -> moodRepo.buildFromPlaylist(pick.playlist.id, mood)
                else -> emptyList()
            }
        }
    }

    private suspend fun sourceTracks(s: Source): List<PlaylistTrack>? = when (s) {
        Source.EVERYTHING -> null // whole library
        Source.LIKED -> repo.fetchLikedTracks(300)
        Source.RECENT -> repo.fetchRecentlyPlayed()
        Source.TOP_MONTH -> repo.fetchTopTracks("short_term")
        Source.TOP_HALF -> repo.fetchTopTracks("medium_term")
        Source.TOP_ALL -> repo.fetchTopTracks("long_term")
    }

    private fun create(title: String, subtitle: String, emptyNote: String, block: suspend () -> List<MoodTrack>) {
        if (building != null) return
        viewModelScope.launch {
            building = title; note = null
            val result = runCatching { block() }.getOrDefault(emptyList())
            if (result.isEmpty()) note = emptyNote
            else openMix = store.add(title, subtitle, result).id
            building = null
        }
    }

    fun consumeOpen() { openMix = null }

    fun playMix(m: Mix) { if (m.tracks.isNotEmpty()) remote.playQueue(m.tracks.map { it.toQueue() }, 0) }
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }
    fun authUrl(): String = repo.authUrl()
}
