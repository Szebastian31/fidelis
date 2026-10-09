package com.fidelis.player.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.PlaylistTrack
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
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("fidelis_ui", Context.MODE_PRIVATE)

    val connected = repo.connected
    val nowPlaying = remote.state
    val artwork = remote.artwork

    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var hasMore by mutableStateOf(false); private set
    var recent by mutableStateOf(loadRecent()); private set

    private var job: Job? = null

    /** Searches as you type, after a short pause. */
    fun onQuery(q: String) {
        query = q
        job?.cancel()
        if (q.isBlank()) { results = emptyList(); hasMore = false; loading = false; return }
        job = viewModelScope.launch {
            delay(350)
            loading = true
            val page = repo.searchTracks(q)
            results = page
            hasMore = page.size >= SpotifyRepository.SEARCH_PAGE
            loading = false
        }
    }

    fun submit() { if (query.isNotBlank()) remember(query.trim()) }

    fun loadMore() {
        if (loading || !hasMore || query.isBlank()) return
        val q = query
        job = viewModelScope.launch {
            loading = true
            val page = repo.searchTracks(q, offset = results.size)
            results = (results + page).distinctBy { it.uri }
            hasMore = page.size >= SpotifyRepository.SEARCH_PAGE
            loading = false
        }
    }

    fun useRecent(q: String) { onQuery(q); remember(q) }
    fun clearRecent() { recent = emptyList(); prefs.edit().remove(KEY).apply() }

    fun playNow(t: PlaylistTrack) { remember(query.trim()); remote.playNow(t.toQueue()) }
    fun playNext(t: PlaylistTrack) { remember(query.trim()); remote.playNext(listOf(t.toQueue())) }
    fun addToQueue(t: PlaylistTrack) { remember(query.trim()); remote.addToQueue(listOf(t.toQueue())) }
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }

    private fun remember(q: String) {
        if (q.isBlank()) return
        recent = (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(8)
        prefs.edit().putString(KEY, recent.joinToString("\u0001")).apply()
    }

    private fun loadRecent(): List<String> =
        prefs.getString(KEY, null)?.split("\u0001")?.filter { it.isNotBlank() } ?: emptyList()

    private companion object { const val KEY = "recent_searches" }
}
