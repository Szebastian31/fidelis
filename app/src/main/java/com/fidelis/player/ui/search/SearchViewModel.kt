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
import com.fidelis.player.integration.spotify.PlaylistItem
import com.fidelis.player.integration.spotify.SpotifyLink
import com.fidelis.player.ui.playlists.PlaylistPins
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.ListItem
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
    private val pins: PlaylistPins,
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
    var loadingPlaylists by mutableStateOf(false); private set
    var canMorePlaylists by mutableStateOf(false); private set
    /** A pasted profile or playlist link, shown instead of search results. */
    var link by mutableStateOf<SpotifyLink?>(null); private set
    /** The playlist behind a pasted playlist link, once Spotify answers. */
    var linked by mutableStateOf<PlaylistItem?>(null); private set
    val pinned = pins.items

    private var job: Job? = null

    /** Searches 350ms after you stop typing. */
    fun onQuery(q: String) {
        query = q
        job?.cancel()
        link = SpotifyLink.parse(q)
        linked = null
        if (q.isBlank()) { results = null; loading = false; return }
        link?.let { l ->
            results = null; loading = false
            if (!l.isProfile) job = viewModelScope.launch { linked = catalog.playlist(l.id) }
            return
        }
        job = viewModelScope.launch {
            delay(350)
            loading = true
            val r = catalog.search(q)
            results = r
            canLoadMore = r.tracks.size >= SpotifyRepository.SEARCH_PAGE
            canMorePlaylists = r.playlists.size >= SpotifyRepository.SEARCH_PAGE
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

    fun morePlaylists() {
        val r = results ?: return
        if (loadingPlaylists || !canMorePlaylists) return
        viewModelScope.launch {
            loadingPlaylists = true
            val more = catalog.morePlaylists(query, offset = r.playlists.size)
            val merged = (r.playlists + more).distinctBy { it.id }
            results = r.copy(playlists = merged)
            canMorePlaylists = more.size >= SpotifyRepository.SEARCH_PAGE && merged.size < MAX_SONGS
            loadingPlaylists = false
        }
    }

    /** Other people's playlists can't be listed song by song, so Spotify plays them whole. */
    fun playPlaylist(p: PlaylistItem) { remember(); remote.playContext(p.uri) }

    fun togglePin(p: PlaylistItem) {
        remember()
        pins.toggle(ListItem(p.uri, p.uri, p.imageUrl?.let { ImageUri(it) }, p.name, p.label, true, true))
    }

    private fun linkedPlaylist(): PlaylistItem? {
        val l = link?.takeIf { !it.isProfile } ?: return null
        return linked ?: PlaylistItem(l.id, "Spotify playlist", "", "", null, 0, l.uri)
    }
    fun playLink() { linkedPlaylist()?.let { playPlaylist(it) } }
    fun pinLink() { linkedPlaylist()?.let { togglePin(it) } }

    private fun readRecent() = prefs.getString(KEY, null).orEmpty().lines().filter { it.isNotBlank() }

    private companion object {
        const val KEY = "recent_searches_v2"
        const val MAX_SONGS = 100
    }
}
