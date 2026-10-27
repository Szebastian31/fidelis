package com.fidelis.player.integration.spotify

import android.content.Context
import android.content.Intent
import android.graphics. Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.fidelis.player.BuildConfig
import com.fidelis.player.playback.PlaybackService
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class QueueTrack(val uri: String, val title: String, val artist: String, val imageUrl: String? = null)

/** One slot in Up next. [id] stays stable while the slot is moved around. */
data class QueueEntry(val id: Long, val track: QueueTrack)

/**
 * Bridge to the Spotify app plus Fidelis's own queue.
 *
 * Fidelis owns Up next and the last 10 played songs. Spotify only ever gets the very next
 * song, handed over ~20s before the current one ends, so crossfade still works and the
 * queue can be reordered freely until then.
 */
@Singleton
class SpotifyRemote @Inject constructor() {

    data class Snapshot(
        val title: String, val artist: String, val isPaused: Boolean,
        val positionMs: Long, val durationMs: Long, val imageUri: String?, val uri: String?,
    ) {
        fun matches(t: QueueTrack) = uri == t.uri ||
            (title.equals(t.title, ignoreCase = true) && artist.equals(t.artist, ignoreCase = true))
    }

    private var appRemote: SpotifyAppRemote? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connected = MutableStateFlow(false); val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _connecting = MutableStateFlow(false); val connecting: StateFlow<Boolean> = _connecting.asStateFlow()
    private val _state = MutableStateFlow<Snapshot?>(null); val state: StateFlow<Snapshot?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error.asStateFlow()
    private val _artwork = MutableStateFlow<Bitmap?>(null); val artwork: StateFlow<Bitmap?> = _artwork.asStateFlow()
    /** Whether the playing song is in your Spotify Liked Songs. */
    private val _liked = MutableStateFlow(false); val liked: StateFlow<Boolean> = _liked.asStateFlow()

    /** Up next, in play order. Does not include the current song. */
    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList()); val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()
    /** Last played songs, oldest first, max 10. */
    private val _history = MutableStateFlow<List<QueueTrack>>(emptyList()); val history: StateFlow<List<QueueTrack>> = _history.asStateFlow()
    private val _current = MutableStateFlow<QueueTrack?>(null); val current: StateFlow<QueueTrack?> = _current.asStateFlow()

    private var nextId = 1L
    private var expecting: QueueTrack? = null      // song we just asked Spotify to start
    private var expectingSince = 0L
    private var queuedNext: QueueTrack? = null     // song already handed to Spotify's queue
    private val stale = LinkedHashSet<String>()    // handed over, then made obsolete
    private var lastStateAt = 0L
    private var lastArtUri: String? = null
    private var likedUri: String? = null
    private var ticker: Job? = null

    fun markConnecting() { _connecting.value = true; _error.value = null }
    fun reportAuthError(message: String?) { _connecting.value = false; _error.value = message ?: "Authorization failed" }

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (appRemote?.isConnected == true) { _connected.value = true; _connecting.value = false; return }
        _connecting.value = true
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri("fidelis://auth").showAuthView(false).build()
        try {
            SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    appRemote = remote; _connected.value = true; _connecting.value = false; _error.value = null
                    remote.playerApi.subscribeToPlayerState().setEventCallback { ps -> onPlayerState(ps, remote) }
                    startTicker()
                }
                override fun onFailure(error: Throwable) {
                    Log.e("FidelisBridge", "failure", error)
                    _connected.value = false; _connecting.value = false
                    _error.value = error.message ?: error.javaClass.simpleName
                }
            })
        } catch (t: Throwable) {
            _connecting.value = false; _error.value = "connect(): ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun onPlayerState(ps: PlayerState, remote: SpotifyAppRemote) {
        val t = ps.track
        val snap = Snapshot(
            title = t?.name ?: "", artist = t?.artist?.name ?: "",
            isPaused = ps.isPaused, positionMs = ps.playbackPosition,
            durationMs = t?.duration ?: 0L, imageUri = t?.imageUri?.raw, uri = t?.uri,
        )
        _state.value = snap
        lastStateAt = SystemClock.elapsedRealtime()
        if (t?.imageUri != null && t.imageUri.raw != lastArtUri) {
            lastArtUri = t.imageUri.raw
            remote.imagesApi.getImage(t.imageUri).setResultCallback { bmp -> _artwork.value = bmp }
        }
        if (t?.uri != null && t.uri != likedUri) refreshLiked(t.uri, remote)
        if (snap.uri != null) reconcile(snap)
    }

    private fun refreshLiked(uri: String, remote: SpotifyAppRemote) {
        likedUri = uri
        _liked.value = false
        remote.userApi.getLibraryState(uri).setResultCallback { ls -> if (ls.uri == likedUri) _liked.value = ls.isAdded }
    }

    /** Saves the playing song to Liked Songs, or removes it. Rolls back if Spotify refuses. */
    fun toggleLike() {
        val uri = _state.value?.uri ?: return
        val api = appRemote?.userApi ?: return
        val now = !_liked.value
        _liked.value = now
        (if (now) api.addToLibrary(uri) else api.removeFromLibrary(uri))
            .setErrorCallback { if (likedUri == uri) _liked.value = !now }
    }

    /** Keeps our model in step when the playing song changes. */
    private fun reconcile(s: Snapshot) {
        expecting?.let { exp ->
            if (s.matches(exp)) { expecting = null; return }
            if (SystemClock.elapsedRealtime() - expectingSince < 5_000) return // still switching
            expecting = null
        }
        val cur = _current.value
        if (cur != null && s.matches(cur)) return

        val handed = queuedNext
        when {
            // Natural hand-over to the song we queued.
            handed != null && s.matches(handed) -> {
                queuedNext = null
                pushHistory(cur)
                _current.value = handed
                val up = _queue.value
                if (up.firstOrNull()?.track?.uri == handed.uri) _queue.value = up.drop(1)
            }
            // A leftover from an older queue: jump to what should really be next.
            s.uri != null && stale.remove(s.uri) && _queue.value.isNotEmpty() -> {
                val up = _queue.value
                _queue.value = up.drop(1)
                goTo(up.first().track)
            }
            // Changed from outside Fidelis (Spotify app, headphones…). Follow along.
            else -> {
                pushHistory(cur)
                _current.value = QueueTrack(s.uri ?: "", s.title, s.artist)
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) { delay(1_000); maybeHandOverNext() }
        }
    }

    /** Gives Spotify the next song shortly before the current one ends. */
    private fun maybeHandOverNext() {
        val s = _state.value ?: return
        if (s.isPaused || queuedNext != null || expecting != null || s.durationMs <= 0) return
        val next = _queue.value.firstOrNull()?.track ?: return
        val cur = _current.value ?: return
        if (!s.matches(cur)) return
        val pos = s.positionMs + (SystemClock.elapsedRealtime() - lastStateAt)
        if (s.durationMs - pos > HANDOVER_MS) return
        queuedNext = next
        appRemote?.playerApi?.queue(next.uri)
    }

    // ---------- Queue operations ----------

    private fun entry(t: QueueTrack) = QueueEntry(nextId++, t)

    private fun pushHistory(t: QueueTrack?) {
        if (t == null || t.uri.isBlank()) return
        val h = _history.value
        if (h.lastOrNull()?.uri == t.uri) return
        _history.value = (h + t).takeLast(HISTORY)
    }

    private fun invalidateHandOver() {
        queuedNext?.let { stale.add(it.uri); while (stale.size > 20) stale.remove(stale.first()) }
        queuedNext = null
    }

    /** Call after Up next changes: if Spotify holds a song that's no longer first, retract it. */
    private fun upNextChanged() {
        val h = queuedNext ?: return
        if (_queue.value.firstOrNull()?.track?.uri != h.uri) invalidateHandOver()
    }

    private fun goTo(t: QueueTrack, rememberCurrent: Boolean = true) {
        if (rememberCurrent) pushHistory(_current.value)
        invalidateHandOver()
        _current.value = t
        expecting = t; expectingSince = SystemClock.elapsedRealtime()
        appRemote?.playerApi?.play(t.uri)
        ensureService()
    }

    private fun startIfIdle() {
        if (_current.value != null) return
        val up = _queue.value
        if (up.isEmpty()) return
        _queue.value = up.drop(1)
        goTo(up.first().track)
    }

    /** Replace Up next with [items] and start at [startIndex]. */
    fun playQueue(items: List<QueueTrack>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        val i = startIndex.coerceIn(0, items.lastIndex)
        _queue.value = items.drop(i + 1).map(::entry)
        goTo(items[i])
    }

    /** Play one song now; Up next stays as it is. */
    fun playNow(t: QueueTrack) = goTo(t)

    fun playNext(tracks: List<QueueTrack>) {
        if (tracks.isEmpty()) return
        _queue.value = tracks.map(::entry) + _queue.value
        upNextChanged(); startIfIdle()
    }

    fun addToQueue(tracks: List<QueueTrack>) {
        if (tracks.isEmpty()) return
        _queue.value = _queue.value + tracks.map(::entry)
        upNextChanged(); startIfIdle()
    }

    /** Jump to an Up next slot; songs before it are skipped. */
    fun playAt(index: Int) {
        val up = _queue.value
        if (index !in up.indices) return
        _queue.value = up.drop(index + 1)
        goTo(up[index].track)
    }

    fun move(from: Int, to: Int) {
        val up = _queue.value.toMutableList()
        if (from !in up.indices || to !in up.indices || from == to) return
        up.add(to, up.removeAt(from))
        _queue.value = up
        upNextChanged()
    }

    fun remove(index: Int) {
        val up = _queue.value
        if (index !in up.indices) return
        _queue.value = up.filterIndexed { i, _ -> i != index }
        upNextChanged()
    }

    fun clearQueue() { _queue.value = emptyList(); upNextChanged() }

    fun reshuffle() { _queue.value = _queue.value.shuffled(); upNextChanged() }

    /** Plays a whole Spotify context (e.g. a playlist Fidelis can't list). Clears Up next. */
    fun playContext(uri: String) {
        pushHistory(_current.value)
        _queue.value = emptyList()
        invalidateHandOver()
        expecting = null; _current.value = null
        appRemote?.playerApi?.play(uri)
        ensureService()
    }

    fun skipNext() {
        val up = _queue.value
        if (up.isEmpty()) { appRemote?.playerApi?.skipNext(); return } // let Spotify continue
        val next = up.first().track
        _queue.value = up.drop(1)
        if (queuedNext?.uri == next.uri) {
            // Already in Spotify's queue: a plain skip consumes it cleanly.
            queuedNext = null
            pushHistory(_current.value)
            _current.value = next
            expecting = next; expectingSince = SystemClock.elapsedRealtime()
            appRemote?.playerApi?.skipNext()
        } else {
            goTo(next)
        }
    }

    /** Goes back to the song you played before (up to 10). Restarts only if there's no history. */
    fun skipPrevious() {
        val h = _history.value
        if (h.isEmpty()) { seekTo(0); return }
        val prev = h.last()
        _history.value = h.dropLast(1)
        _current.value?.let { _queue.value = listOf(entry(it)) + _queue.value }
        goTo(prev, rememberCurrent = false)
    }

    fun resume() { appRemote?.playerApi?.resume() }
    fun pause() { appRemote?.playerApi?.pause() }
    fun seekTo(ms: Long) { appRemote?.playerApi?.seekTo(ms) }

    private fun ensureService() {
        val ctx = appContext ?: return
        ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
    }

    fun disconnect() {
        appRemote?.let { SpotifyAppRemote.disconnect(it) }; appRemote = null; _connected.value = false
        appContext?.let { it.stopService(Intent(it, PlaybackService::class.java)) }
    }

    private companion object {
        const val HANDOVER_MS = 20_000L
        const val HISTORY = 10
    }
}
