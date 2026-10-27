package com.fidelis.player.ui.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.lastfm.LastfmTop
import com.fidelis.player.integration.spotify.AlbumItem
import com.fidelis.player.integration.spotify.ArtistItem
import com.fidelis.player.integration.spotify.ArtistRadio
import com.fidelis.player.integration.spotify.PlaylistTrack
import com.fidelis.player.integration.spotify.SpotifyCatalog
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.integration.spotify.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: SpotifyCatalog,
    private val radio: ArtistRadio,
    private val remote: SpotifyRemote,
    private val repo: SpotifyRepository,
    private val lastfmTop: LastfmTop,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["artistId"])

    val nowPlaying = remote.state
    val artwork = remote.artwork

    var artist by mutableStateOf<ArtistItem?>(null); private set
    var albums by mutableStateOf<List<AlbumItem>>(emptyList()); private set
    var singles by mutableStateOf<List<AlbumItem>>(emptyList()); private set
    var songs by mutableStateOf<List<PlaylistTrack>>(emptyList()); private set
    /** Last.fm play counts by Spotify uri, for songs that were ranked. */
    var plays by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    var byPopularity by mutableStateOf(false); private set
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
            val (all, found, ranked) = coroutineScope {
                val releases = async { catalog.artistAlbums(id) }
                val searched = async { catalog.artistSongs(a.name) }
                val top = async { lastfmTop.topTracks(a.name) }
                Triple(releases.await(), searched.await(), top.await())
            }
            albums = all.filterNot { it.isSingle }
            singles = all.filter { it.isSingle }
            var pool = found
            // Search finds few songs for some artists; top up from their newest releases.
            if (pool.size < 15) {
                for (rel in all.take(2)) {
                    pool = (pool + catalog.album(rel.id)?.tracks.orEmpty()).distinctBy { norm(it.title) }
                }
            }
            songs = if (ranked.isEmpty()) pool.also { plays = emptyMap(); byPopularity = false }
                    else rank(a.name, pool, ranked)
            loading = false
        }
    }

    /** Orders songs by Last.fm popularity; looks up ranked songs Spotify search missed. Unranked go last. */
    private suspend fun rank(name: String, pool: List<PlaylistTrack>, ranked: List<Pair<String, Long>>): List<PlaylistTrack> =
        coroutineScope {
            val byTitle = HashMap<String, PlaylistTrack>()
            pool.forEach { byTitle.putIfAbsent(norm(it.title), it) }
            val top = ranked.distinctBy { norm(it.first) }.take(MAX_RANKED)
            val sem = Semaphore(3)
            val matched = top.mapIndexed { i, (title, _) ->
                async {
                    byTitle[norm(title)]
                        ?: if (i < LOOKUPS) sem.withPermit {
                            runCatching { repo.findTrack(name, title) }.getOrNull()
                                ?.takeIf { it.artist.equals(name, ignoreCase = true) }
                        } else null
                }
            }.awaitAll()
            val counts = HashMap<String, Long>()
            val ordered = ArrayList<PlaylistTrack>()
            matched.forEachIndexed { i, t ->
                if (t != null && ordered.none { it.uri == t.uri }) { ordered += t; counts[t.uri] = top[i].second }
            }
            val seen = ordered.map { norm(it.title) }.toHashSet()
            ordered += pool.filter { norm(it.title) !in seen }
            plays = counts
            byPopularity = counts.isNotEmpty()
            ordered
        }

    /** "Disclosure · 12.4M plays" for ranked songs, just the artist otherwise. */
    fun subtitle(t: PlaylistTrack): String {
        val n = plays[t.uri] ?: return t.artist
        val short = when {
            n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
            n >= 1_000 -> "${n / 1_000}K"
            else -> "$n"
        }
        return "${t.artist} · $short plays"
    }

    fun play(index: Int = 0) { if (songs.isNotEmpty()) remote.playQueue(songs.map { it.toQueue() }, index) }
    fun shuffle() { if (songs.isNotEmpty()) remote.playQueue(songs.shuffled().map { it.toQueue() }, 0) }
    fun playNext(t: PlaylistTrack) = remote.playNext(listOf(t.toQueue()))
    fun addToQueue(t: PlaylistTrack) = remote.addToQueue(listOf(t.toQueue()))
    fun togglePlay() { if (nowPlaying.value?.isPaused == true) remote.resume() else remote.pause() }

    /** Builds the artist radio from their most popular songs and starts it. [onStarted] gets false if it couldn't. */
    fun startRadio(onStarted: (Boolean) -> Unit) {
        val a = artist ?: return
        if (buildingRadio) return
        viewModelScope.launch {
            buildingRadio = true
            val mix = radio.build(a.name, songs.take(30))
            buildingRadio = false
            if (mix.isEmpty()) { onStarted(false); return@launch }
            remote.playQueue(mix.map { it.toQueue() }, 0)
            onStarted(true)
        }
    }

    private companion object {
        const val MAX_RANKED = 40
        const val LOOKUPS = 25 // ranked songs we'll look up on Spotify if search missed them

        // "Song (feat. X)" / "Song - Remastered 2011" → "song", so versions match.
        fun norm(t: String) = t.lowercase()
            .replace(Regex("""\s*(\(|\[| - | – ).*$"""), "")
            .replace(Regex("""[^\p{L}\p{N} ]"""), "")
            .trim()
    }
}
