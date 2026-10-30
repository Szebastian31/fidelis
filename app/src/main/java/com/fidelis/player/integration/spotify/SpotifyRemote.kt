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

data class QueueTrack(val uri: String, val title: String, val artist: String, val imageUrl: String? = null, val durationMs: Long = 0L)

/** One slot in Up next. [id] stays stable while the slot is moved around. */
data class QueueEntry(val id: Long, val track: QueueTrack, val manual: Boolean = false)

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
        val at: Long = SystemClock.elapsedRealtime(),
    ) {
        fun matches(t: QueueTrack) = uri == t.uri ||
            (title.equals(t.title, ignoreCase = true) && artist.equals(t.artist, ignoreCase = true))

        /** Where the song is right now. Spotify only reports the position when something changes. */
        fun livePosition(): Long =
            if (isPaused || durationMs <= 0) positionMs
            else (positionMs + SystemClock.elapsedRealtime() - at).coerceIn(0L, durationMs)
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
    /** Where the songs after your own picks come from ("Next from: …"). */
    private val _source = MutableStateFlow<String?>(null); val source: StateFlow<String?> = _source.asStateFlow()
    /** What Spotify itself says it's playing from (a playlist, the DJ…). */
    private val _spotifyContext = MutableStateFlow<String?>(null); val spotifyContext: StateFlow<String?> = _spotifyContext.asStateFlow()
    /** True while Spotify runs a whole playlist or the DJ and picks the next songs itself. */
    private val _contextMode = MutableStateFlow(false); val contextMode: StateFlow<Boolean> = _contextMode.asStateFlow()
    private var lastSeekAt = 0L

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
                    remote.playerApi.subscribeToPlayerContext().setEventCallback { c -> _spotifyContext.value = c?.title?.takeIf { it.isNotBlank() } }
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
        // Headphone "next" goes to Spotify, which only knows this one song and restarts it. Treat that as next.
        val was = _state.value
        if (was != null && !was.isPaused && was.uri != null && was.uri == snap.uri && _queue.value.isNotEmpty() &&
            expecting == null && snap.positionMs < 1_500 && was.livePosition() > 4_000 &&
            was.livePosition() < was.durationMs - 4_000 && SystemClock.elapsedRealtime() - lastSeekAt > 3_000
        ) { _state.value = snap; skipNext(); return }
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

    private fun entry(t: QueueTrack, manual: Boolean = false) = QueueEntry(nextId++, t, manual)

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
        _contextMode.value = false
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
    fun playQueue(items: List<QueueTrack>, startIndex: Int = 0, from: String? = null) {
        if (items.isEmpty()) return
        val i = startIndex.coerceIn(0, items.lastIndex)
        _queue.value = _queue.value.filter { it.manual } + items.drop(i + 1).map(::entry)
        _source.value = from
        goTo(items[i])
    }

    /** Play one song now; Up next stays as it is. */
    fun playNow(t: QueueTrack) = goTo(t)

    fun playNext(tracks: List<QueueTrack>) {
        if (tracks.isEmpty()) return
        _queue.value = tracks.map { entry(it, manual = true) } + _queue.value
        upNextChanged(); startIfIdle()
    }

    fun addToQueue(tracks: List<QueueTrack>) {
        if (tracks.isEmpty()) return
        // Your picks line up after the ones you already added, before the playlist carries on.
        val q = _queue.value
        val mine = q.takeWhile { it.manual }.size
        _queue.value = q.take(mine) + tracks.map { entry(it, manual = true) } + q.drop(mine)
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
        // Dropped among your picks it becomes one of them; dropped among the rest it joins them.
        val e = up.removeAt(from)
        val manual = (up.getOrNull(to - 1)?.manual ?: false) || (to == 0 && up.getOrNull(0)?.manual == true)
        up.add(to, e.copy(manual = manual))
        _queue.value = up
        upNextChanged()
    }

    fun remove(index: Int) {
        val up = _queue.value
        if (index !in up.indices) return
        _queue.value = up.filterIndexed { i, _ -> i != index }
        upNextChanged()
    }

    /** Replaces Up next and keeps the current song playing (song radio). */
    fun setUpNext(tracks: List<QueueTrack>) {
        _queue.value = _queue.value.filter { it.manual } + tracks.map(::entry)
        _source.value = "Song radio"
        upNextChanged()
    }

    fun clearQueue() { _queue.value = emptyList(); upNextChanged() }

    /** A fresh random order every time. Your own picks stay first; with nothing to shuffle, Spotify shuffles. */
    fun reshuffle() {
        val (mine, rest) = _queue.value.partition { it.manual }
        if (rest.size < 2) { appRemote?.playerApi?.setShuffle(true); return }
        _queue.value = mine + rest.freshShuffle { it.track.artist }
        upNextChanged()
    }

    /** Plays a whole Spotify context (e.g. a playlist Fidelis can't list). Clears Up next. */
    fun playContext(uri: String) {
        pushHistory(_current.value)
        _queue.value = emptyList()
        invalidateHandOver()
        expecting = null; _current.value = null
        _contextMode.value = true; _source.value = null
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

    /** Spotify's own home sections (Recents, Made for you…), as the Spotify app shares them with car screens. */
    suspend fun browse(parent: com.spotify.protocol.types.ListItem?, limit: Int = 30): List<com.spotify.protocol.types.ListItem> {
        val api = appRemote?.contentApi ?: return emptyList()
        val done = kotlinx.coroutines.CompletableDeferred<List<com.spotify.protocol.types.ListItem>>()
        runCatching {
            val call = if (parent == null) api.getRecommendedContentItems("default") else api.getChildrenOfItem(parent, limit, 0)
            call.setResultCallback { r -> done.complete(r?.items?.filterNotNull().orEmpty()) }
            call.setErrorCallback { done.complete(emptyList()) }
        }.onFailure { done.complete(emptyList()) }
        return kotlinx.coroutines.withTimeoutOrNull(8_000) { done.await() } ?: emptyList()
    }

    /** Cover art for anything Spotify shares, straight from the Spotify app. */
    suspend fun image(uri: com.spotify.protocol.types.ImageUri): Bitmap? {
        val api = appRemote?.imagesApi ?: return null
        val done = kotlinx.coroutines.CompletableDeferred<Bitmap?>()
        runCatching {
            val call = api.getImage(uri, com.spotify.protocol.types.Image.Dimension.MEDIUM)
            call.setResultCallback { done.complete(it) }
            call.setErrorCallback { done.complete(null) }
        }.onFailure { done.complete(null) }
        return kotlinx.coroutines.withTimeoutOrNull(8_000) { done.await() }
    }

    /** Plays something from Spotify's home sections. Clears Up next, like any other playlist or album. */
    fun playItem(item: com.spotify.protocol.types.ListItem) {
        val uri = item.uri
        if (uri != null && uri.startsWith("spotify:")) { playContext(uri); return }
        pushHistory(_current.value)
        _queue.value = emptyList()
        invalidateHandOver()
        expecting = null; _current.value = null
        appRemote?.contentApi?.playContentItem(item)
        ensureService()
    }

    /**
     * Plays a Spotify mix or album with its songs in Up next, so it can be reordered like any playlist.
     * If the Spotify app won't share the songs, it plays the whole thing as before.
     */
    fun playWithQueue(item: com.spotify.protocol.types.ListItem) {
        scope.launch {
            val songs = (kotlinx.coroutines.withTimeoutOrNull(3_000) { browse(item, 100) } ?: emptyList())
                .filter { it.uri.orEmpty().startsWith("spotify:track:") }
                .map { QueueTrack(it.uri, it.title.orEmpty(), it.subtitle.orEmpty()) }
            if (songs.size >= 2) playQueue(songs, from = item.title) else playItem(item)
        }
    }

    fun playUriWithQueue(uri: String) =
        playWithQueue(com.spotify.protocol.types.ListItem(uri, uri, null, "", "", true, true))

    fun resume() { appRemote?.playerApi?.resume() }
    fun pause() { appRemote?.playerApi?.pause() }
    fun seekTo(ms: Long) { lastSeekAt = SystemClock.elapsedRealtime(); appRemote?.playerApi?.seekTo(ms) }

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

/** A new random order every time, keeping the same artist from playing twice in a row where it can. */
fun <T> List<T>.freshShuffle(artist: (T) -> String): List<T> {
    val pool = shuffled(kotlin.random.Random(System.nanoTime())).toMutableList()
    val out = ArrayList<T>(size)
    while (pool.isNotEmpty()) {
        val last = out.lastOrNull()?.let(artist)
        val i = pool.indexOfFirst { artist(it) != last }.let { if (it < 0) 0 else it }
        out += pool.removeAt(i)
    }
    return out
}
