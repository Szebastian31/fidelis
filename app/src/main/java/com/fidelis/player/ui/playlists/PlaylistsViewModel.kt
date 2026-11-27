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
import com.fidelis.player.integration.spotify.PodcastEpisode
import com.fidelis.player.integration.spotify.PodcastRepository
import com.spotify.protocol.types.ListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
        private val pins: PlaylistPins,
    private val podcasts: PodcastRepository,
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

    /** The Podcasts pill: the shows you follow and their newest episodes. Loaded the first time it's opened. */
    var shows by mutableStateOf(podcasts.cachedShows()); private set
    var newEpisodes by mutableStateOf(podcasts.cachedNewEpisodes()); private set
    var loadingShows by mutableStateOf(false); private set
    private var showsJob: Job? = null

    fun loadShows(force: Boolean = false) {
        if (showsJob?.isActive == true || (!force && showsJob != null)) return
        showsJob = viewModelScope.launch {
            loadingShows = true
            val s = runCatching { podcasts.shows() }.getOrDefault(shows)
            shows = s
            newEpisodes = runCatching { podcasts.newEpisodes(s) }.getOrDefault(newEpisodes)
            loadingShows = false
        }
    }

    fun playEpisode(index: Int) {
        val list = newEpisodes
        if (index in list.indices) remote.playQueue(list.map { it.toQueue() }, index)
    }

    init {
        // These come through the Spotify app, so load them once it's connected.
        viewModelScope.launch { remote.connected.filter { it }.collect { loadHome() } }
    }

    private fun loadHome() {
        if (homeJob?.isActive == true) return
        homeJob = viewModelScope.launch { runCatching { spotifyHome.load() }.getOrNull()?.let { home = it } }
    }

    fun refresh() {
        loadHome()
        if (showsJob != null) loadShows(force = true)
        if (loading) return
        viewModelScope.launch {
            loading = true
            // Each row appears as soon as its own answer arrives, instead of all waiting for the slowest.
            launch { likedCount = runCatching { repo.fetchLikedCount() }.getOrDefault(likedCount) }
            launch { runCatching { repo.fetchTopTracks("short_term") }.getOrNull()?.let { quickPicks = it.distinctBy { t -> t.uri }.take(20) } }
            launch { runCatching { repo.fetchRecentlyPlayed() }.getOrNull()?.let { listenAgain = it.distinctBy { t -> t.uri }.take(16) } }
            launch { runCatching { feed.topArtists("medium_term") }.getOrNull()?.let { heavyRotation = it } }
            playlists = repo.fetchPlaylists()
            loading = false
        }
    }

    fun playQuickPick(index: Int) = play(quickPicks, index)
    fun playListenAgain(index: Int) = play(listenAgain, index)
    private fun play(list: List<PlaylistTrack>, index: Int) {
        if (index in list.indices) remote.playQueue(list.map { it.toQueue() }, index)
    }

    fun playItem(item: ListItem) = remote.playWithQueue(item)
    fun playDj() = remote.playContext(SpotifyHome.DJ_URI)
    fun ownsPlaylist(id: String) = playlists.any { it.id == id }

    val pinned = pins.ids
    fun togglePin(id: String) = pins.toggle(id)
    val pinItems = pins.items
    fun togglePin(item: ListItem) = pins.toggle(item)
    /** Your playlists with everything pinned first, in pin order: yours and Spotify's alike. */
    fun shelf(all: List<Pin>): List<Shelf> {
        val byId = playlists.associateBy { it.id }
        val front = all.mapNotNull { pin ->
            byId[pin.playlistId]?.let { Shelf.Mine(it) } ?: pin.takeIf { it.title.isNotBlank() }?.let { Shelf.Other(it) }
        }
        val taken = front.map { it.key }.toSet()
        return front + playlists.filter { it.id !in taken }.map { Shelf.Mine(it) }
    }
    /** Your playlists with pinned ones first, in pin order. */
    fun ordered(pinnedIds: List<String>) =
        playlists.sortedBy { p -> pinnedIds.indexOf(p.id).let { if (it < 0) Int.MAX_VALUE else it } }

    fun authUrl(): String = repo.authUrl()
}
