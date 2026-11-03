package com.fidelis.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.lyrics.Lyrics
import com.fidelis.player.integration.lyrics.LyricsFinder
import com.fidelis.player.integration.spotify.QueueTrack
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val remote: SpotifyRemote,
    private val finder: LyricsFinder,
    private val repo: SpotifyRepository,
) : ViewModel() {
    val state = remote.state
    val queue = remote.queue
    val history = remote.history
    val artwork = remote.artwork
    val liked = remote.liked
    val source = remote.source
    val spotifyContext = remote.spotifyContext
    val contextMode = remote.contextMode

    private val _lyrics = MutableStateFlow<Lyrics>(Lyrics.Loading)
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    private val _lyricOffset = MutableStateFlow(0L)
    /** How much earlier (positive) or later the lyrics show for this song, in ms. */
    val lyricOffset: StateFlow<Long> = _lyricOffset.asStateFlow()

    private var options: List<Lyrics> = emptyList()
    private var pick = 0
    private var key = ""

    init {
        // Fetch lyrics whenever the song changes.
        viewModelScope.launch {
            remote.state.filterNotNull().filter { it.title.isNotBlank() }
                .distinctUntilChangedBy { it.uri ?: "${it.artist}|${it.title}" }
                .collectLatest { s ->
                    key = finder.key(s.artist, s.title)
                    _lyrics.value = Lyrics.Loading
                    _lyricOffset.value = finder.offset(key)
                    options = finder.find(s.artist, s.title, s.durationMs)
                    pick = finder.preferred(key, options)
                    _lyrics.value = options.getOrElse(pick) { Lyrics.None }
                }
        }
    }

    init {
        // Covers for queued songs that came without one (Spotify mixes, albums…).
        viewModelScope.launch {
            val tried = HashSet<String>()
            remote.queue.collect { q ->
                val missing = (q.map { it.track } + remote.history.value)
                    .filter { it.imageUrl == null && it.uri.startsWith("spotify:track:") }
                    .map { it.uri }.distinct().filter { it !in tried }.take(20)
                tried += missing
                if (missing.isNotEmpty()) remote.fillArt(repo.trackArt(missing))
            }
        }
    }

    /** Shows the next source's lyrics, and remembers the choice for this song. */
    fun nextLyrics() {
        if (options.size < 2) return
        pick = (pick + 1) % options.size
        finder.prefer(key, options[pick])
        _lyrics.value = options[pick]
    }

    /** Moves the lyrics earlier (positive) or later for this song, and remembers it. */
    fun nudgeLyrics(by: Long) {
        val v = (_lyricOffset.value + by).coerceIn(-10_000L, 10_000L)
        _lyricOffset.value = v
        finder.setOffset(key, v)
    }

    fun togglePlay() { if (state.value?.isPaused == true) remote.resume() else remote.pause() }
    fun next() = remote.skipNext()
    fun prev() = remote.skipPrevious()
    fun seekTo(ms: Long) = remote.seekTo(ms)
    fun toggleLike() = remote.toggleLike()
    fun playAt(index: Int) = remote.playAt(index)
    fun move(from: Int, to: Int) = remote.move(from, to)
    fun remove(index: Int) = remote.remove(index)
    fun reshuffle() = remote.reshuffle()
    fun clear() = remote.clearQueue()
    fun playNow(t: QueueTrack) = remote.playNow(t)

    /** Live position of a queue slot (it can move while you drag). */
    fun indexOf(id: Long): Int = remote.queue.value.indexOfFirst { it.id == id }
}
