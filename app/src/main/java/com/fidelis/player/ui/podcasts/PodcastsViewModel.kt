package com.fidelis.player.ui.podcasts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PodcastEpisode
import com.fidelis.player.integration.spotify.PodcastRepository
import com.fidelis.player.integration.spotify.PodcastShow
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val pods: PodcastRepository,
    private val remote: SpotifyRemote,
    repo: SpotifyRepository,
) : ViewModel() {
    val connected = repo.connected
    val nowPlaying = remote.state
    var shows by mutableStateOf(pods.cachedShows()); private set
    var latest by mutableStateOf<List<PodcastEpisode>>(emptyList()); private set
    var loading by mutableStateOf(true); private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            shows = pods.shows()
            latest = pods.newEpisodes(shows)
            loading = false
        }
    }

    fun play(e: PodcastEpisode) = remote.playQueue(listOf(e.toQueue()), 0, from = e.show)
}

@HiltViewModel
class ShowViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val pods: PodcastRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    val nowPlaying = remote.state
    var show by mutableStateOf<PodcastShow?>(null); private set
    var episodes by mutableStateOf<List<PodcastEpisode>>(emptyList()); private set
    var loading by mutableStateOf(true); private set

    init {
        viewModelScope.launch {
            show = saved.get<String>("showId")?.let { pods.show(it) }
            episodes = show?.let { pods.episodes(it) }.orEmpty()
            loading = false
        }
    }

    fun play(i: Int) { episodes.getOrNull(i)?.let { remote.playQueue(listOf(it.toQueue()), 0, from = it.show) } }
}
