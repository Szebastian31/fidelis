package com.fidelis.player.ui.playlists

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.OpenedItems
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyHome
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.coverUrl
import com.fidelis.player.integration.spotify.freshShuffle
import com.spotify.protocol.types.ListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A Spotify mix, editorial playlist or shelf item, shown with its songs before playing. */
@HiltViewModel
class SpotifyPageViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val remote: SpotifyRemote,
    private val smart: com.fidelis.player.integration.spotify.SmartShuffle,
    private val repo: SpotifyRepository,
    private val home: SpotifyHome,
    private val pins: PlaylistPins,
) : ViewModel() {
    val source: ListItem? = saved.get<String>("key")?.let(OpenedItems::get)
    val nowPlaying = remote.state
    var loading by mutableStateOf(true); private set
    var tracks by mutableStateOf<List<QueueTrack>>(emptyList()); private set
    /** The cover exactly as the Spotify app shows it, including mixes whose image has no web address. */
    var cover by mutableStateOf<Bitmap?>(source?.let(home::cached)); private set

    init {
        source?.let { s -> viewModelScope.launch { if (cover == null) cover = runCatching { home.art(s) }.getOrNull() } }
        viewModelScope.launch {
            tracks = source?.let { remote.browse(it, 100) }.orEmpty()
                .filter { it.uri.orEmpty().startsWith("spotify:track:") || it.uri.orEmpty().startsWith("spotify:episode:") }
                .map { QueueTrack(it.uri, it.title.orEmpty(), it.subtitle.orEmpty(), it.coverUrl()) }
            loading = false
            fillCovers()
        }
    }

    /** Songs in Spotify's mixes often arrive without covers; look them up a few at a time. */
    private suspend fun fillCovers() {
        val missing = tracks.filter { it.imageUrl == null && it.uri.startsWith("spotify:track:") }.map { it.uri }
        for (batch in missing.chunked(12)) {
            val art = batch.chunked(3)
                .map { part -> viewModelScope.async { runCatching { repo.trackArt(part) }.getOrDefault(emptyMap()) } }
                .awaitAll().fold(emptyMap<String, String>()) { a, b -> a + b }
            if (art.isEmpty()) continue
            tracks = tracks.map { t -> if (t.imageUrl == null) art[t.uri]?.let { t.copy(imageUrl = it) } ?: t else t }
            remote.fillArt(art)
        }
    }

    fun playFrom(i: Int) = remote.playQueue(tracks, i, from = source?.title)
    fun shuffle() = smart.play(tracks, source?.title)
    fun playInSpotify() { source?.let(remote::playItem) }
    fun playNext(t: QueueTrack) = remote.playNext(listOf(t))
    fun addToQueue(t: QueueTrack) = remote.addToQueue(listOf(t))
    fun addAll() = remote.addToQueue(tracks)
    val pinned = pins.items
    fun togglePin() { source?.let { pins.toggle(it) } }
}
