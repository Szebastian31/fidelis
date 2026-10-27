package com.fidelis.player.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SearchResults
import com.fidelis.player.integration.spotify.SpotifyCatalog
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val catalog: SpotifyCatalog,
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("fidelis_ui", Context.MODE_PRIVATE)

    val nowPlaying = remote.state
    val artwork = remote.artwork

    var query by mutableStateOf(""); private set
    var results by mutableStateOf<SearchResults?>(null); private set
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var canLoadMore by mutableStateOf(false); private set
    var recent by mutableStateOf(readRecent()); private set

    private var job: Job? = null

    /** Searches 350ms after you stop typing. */
    fun onQuery(q: String) {
        query = q
        job?.cancel()
        if (q.isBlank()) { results = null; loading = false; return }
        job = viewModelScope.launch {
            delay(350)
            loading = true
            val r = catalog.search(q)
            results = r
            canLoadMore = r.tracks.size >= SpotifyRepository.SEARCH_PAGE
            loading = false
        }
    }

    fun useRecent(q: String) { onQuery(q); remember() }

    fun loadMore() {
        val r = results ?: return
        if (loadingMore || !canLoadMore) return
        viewModelScope.launch {
            loadingMore = true
            val more = repo.searchTracks(query, offset = r.tracks.size)
            val merged = (r.tracks + more).distinctBy { it.uri }
            results = r.copy(tracks = merged)
            canLoadMore = more.size >= SpotifyRepository.SEARCH_PAGE && merged.size < MAX_SONGS
            loadingMore = false
        }
    }

    /** Saves the current query to Recent searches. */
    fun remember() {
        val q = query.trim()
        if (q.isBlank()) return
        recent = (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(8)
        prefs.edit().putString(KEY, recent.joinToString("\n")).apply()
    }

    fun clearRecent() { recent = emptyList(); prefs.edit().remove(KEY).apply() }

    fun playSong(index: Int) {
        val songs = results?.tracks ?: return
        if (index !in songs.indices) return
        remember()
        remote.playQueue(songs.map { it.toQueue() }, index)
    }

    fun playNext(t: PlaylistTrack) { remember(); remote.playNext(listOf(t.toQueue())) }
    fun addToQueue(t: PlaylistTrack) { remember(); remote.addToQueue(listOf(t.toQueue())) }
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }

    private fun readRecent() = prefs.getString(KEY, null).orEmpty().lines().filter { it.isNotBlank() }

    private companion object {
        const val KEY = "recent_searches_v2"
        const val MAX_SONGS = 100
    }
}
