package com.fidelis.player.ui.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.ArtistItem
import com.fidelis.player.integration.spotify.HomeFeed
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyHome
import com.fidelis.player.integration.spotify.SpotifyHomeFeed
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import com.spotify.protocol.types.ListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Route id for the Liked Songs page (shares the playlist detail screen). */
const val LIKED_PLAYLIST_ID = "liked"

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repo: SpotifyRepository,
    private val remote: SpotifyRemote,
    private val feed: HomeFeed,
    val spotifyHome: SpotifyHome,
) : ViewModel() {
    val connected = repo.connected
    val nowPlaying = remote.state

    var loading by mutableStateOf(false); private set
    var playlists by mutableStateOf(repo.cachedPlaylists()); private set
    var likedCount by mutableIntStateOf(0); private set

    /** Home rows. Each stays empty (and hidden) if Spotify returns nothing. */
    var quickPicks by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set
    var listenAgain by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set
    var heavyRotation by mutableStateOf<List<ArtistItem>>(emptyList()); private set
    /** Spotify's own home: its mixes, your recents and its shelves. */
    var home by mutableStateOf<SpotifyHomeFeed?>(null); private set
    private var homeJob: Job? = null

    init {
        // These come through the Spotify app, so load them once it's connected.
        viewModelScope.launch { remote.connected.distinctUntilChanged().filter { it }.collect { loadHome() } }
    }

    private fun loadHome() {
        if (homeJob?.isActive == true) return
        homeJob = viewModelScope.launch { runCatching { spotifyHome.load() }.getOrNull()?.let { home = it } }
    }

    fun refresh() {
        loadHome()
        if (loading) return
        viewModelScope.launch {
            loading = true
            val liked = async { runCatching { repo.fetchLikedCount() }.getOrDefault(likedCount) }
            val picks = async { runCatching { repo.fetchTopTracks("short_term") }.getOrDefault(emptyList()) }
            val recent = async { runCatching { repo.fetchRecentlyPlayed() }.getOrDefault(emptyList()) }
            val artists = async { runCatching { feed.topArtists("medium_term") }.getOrDefault(emptyList()) }
            playlists = repo.fetchPlaylists()
            likedCount = liked.await()
            quickPicks = picks.await().distinctBy { it.uri }.take(20)
            listenAgain = recent.await().distinctBy { it.uri }.take(16)
            heavyRotation = artists.await()
            loading = false
        }
    }

    fun playQuickPick(index: Int) = play(quickPicks, index)
    fun playListenAgain(index: Int) = play(listenAgain, index)
    private fun play(list: List<PlaylistTrack>, index: Int) {
        if (index in list.indices) remote.playQueue(list.map { it.toQueue() }, index)
    }

    fun playItem(item: ListItem) = remote.playItem(item)
    fun playDj() = remote.playContext(SpotifyHome.DJ_URI)
    fun ownsPlaylist(id: String) = playlists.any { it.id == id }

    fun authUrl(): String = repo.authUrl()
}
