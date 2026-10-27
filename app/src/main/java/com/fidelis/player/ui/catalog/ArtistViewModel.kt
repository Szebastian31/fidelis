package com.fidelis.player.ui.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.AlbumItem
import com.fidelis.player.integration.spotify.ArtistItem
import com.fidelis.player.integration.spotify.ArtistRadio
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyCatalog
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: SpotifyCatalog,
    private val radio: ArtistRadio,
    private val remote: SpotifyRemote,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["artistId"])

    val nowPlaying = remote.state
    val artwork = remote.artwork

    var artist by mutableStateOf<ArtistItem?>(null); private set
    var albums by mutableStateOf<List<AlbumItem>>(emptyList()); private set
    var singles by mutableStateOf<List<AlbumItem>>(emptyList()); private set
    var songs by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var failed by mutableStateOf(false); private set
    var buildingRadio by mutableStateOf(false); private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            loading = true; failed = false
            val a = catalog.artist(id)
            if (a == null) { failed = true; loading = false; return@launch }
            artist = a
            val all = catalog.artistAlbums(id)
            albums = all.filterNot { it.isSingle }
            singles = all.filter { it.isSingle }
            var list = catalog.artistSongs(a.name)
            // Search finds few songs for some artists; top up from their newest releases.
            if (list.size < 15) {
                for (rel in all.take(2)) {
                    list = (list + catalog.album(rel.id)?.tracks.orEmpty()).distinctBy { it.title.lowercase() }
                }
            }
            songs = list
            loading = false
        }
    }

    fun play(index: Int = 0) { if (songs.isNotEmpty()) remote.playQueue(songs.map { it.toQueue() }, index) }
    fun shuffle() { if (songs.isNotEmpty()) remote.playQueue(songs.shuffled().map { it.toQueue() }, 0) }
    fun playNext(t: PlaylistTrack) = remote.playNext(listOf(t.toQueue()))
    fun addToQueue(t: PlaylistTrack) = remote.addToQueue(listOf(t.toQueue()))
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }

    /** Builds the artist radio and starts it. [onStarted] runs once it's playing; false if it couldn't. */
    fun startRadio(onStarted: (Boolean) -> Unit) {
        val a = artist ?: return
        if (buildingRadio) return
        viewModelScope.launch {
            buildingRadio = true
            val mix = radio.build(a.name, songs)
            buildingRadio = false
            if (mix.isEmpty()) { onStarted(false); return@launch }
            remote.playQueue(mix.map { it.toQueue() }, 0)
            onStarted(true)
        }
    }
}
