package com.fidelis.player.ui.podcasts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle. SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PodcastDiscovery
import com.fidelis.player.integration.spotify.PodcastEpisode
import com.fidelis.player.integration.spotify.PodcastRepository
import com.fidelis.player.integration.spotify.PodcastRow
import com.fidelis.player.integration.spotify.PodcastShow
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val pods: PodcastRepository,
    private val discover: PodcastDiscovery,
    private val remote: SpotifyRemote,
    repo: SpotifyRepository,
) : ViewModel() {
    val connected = repo.connected
    val nowPlaying = remote.state
    var shows by mutableStateOf(pods.cachedShows()); private set
    var latest by mutableStateOf<List<PodcastEpisode>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var vibe by mutableStateOf<List<String>>(emptyList()); private set
    var rows by mutableStateOf<List<PodcastRow>>(emptyList()); private set
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<PodcastShow>?>(null); private set
    private var job: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            shows = pods.shows()
            latest = pods.newEpisodes(shows)
            loading = false
            runCatching { discover.forYou(shows, favourite()) }.getOrNull()?.let { vibe = it.vibe; rows = it.rows }
        }
    }

    /** The show you've spent the most time with lately. */
    private fun favourite(): PodcastShow? =
        latest.filter { it.playedMs > 0 || it.finished }.groupingBy { it.show }.eachCount()
            .maxByOrNull { it.value }?.key?.let { n -> shows.firstOrNull { it.name == n } } ?: shows.firstOrNull()

    /** Searches as you type, after a short pause. */
    fun search(q: String) {
        query = q
        job?.cancel()
        results = null
        if (q.isBlank()) return
        job = viewModelScope.launch {
            delay(350)
            results = try { discover.search(q) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
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
